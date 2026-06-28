package com.xxx.jfxssh.ui.sftp;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ssh.SftpCancelledException;
import com.xxx.jfxssh.ssh.SftpEntry;
import com.xxx.jfxssh.ssh.SftpOperationException;
import com.xxx.jfxssh.ssh.SftpProgress;
import com.xxx.jfxssh.ssh.SftpSession;
import com.xxx.jfxssh.ssh.SshSession;
import com.xxx.jfxssh.ui.dialog.UiDialogs;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 双栏 SFTP 文件管理窗口（每个连接一个）。
 *
 * <p>左 {@link LocalPane}（本地）、右 {@link RemotePane}（远程）。<b>浏览</b>（list/cd/新建/
 * 重命名/删除）走一个单线程执行器与共享 SFTP 通道；<b>每个传输</b>则<b>独立开一条 SFTP 通道</b>
 * 在自己的线程上跑——因此传输不阻塞浏览，且多个传输可并发。底部为传输列表（每行：进度 /
 * 百分比 / MB / 速度 / 取消）。关闭窗口取消所有传输并释放 SFTP 与底层 SSH 会话。</p>
 */
public final class SftpBrowserWindow {

    private static final Logger log = LoggerFactory.getLogger(SftpBrowserWindow.class);

    private final Stage stage;
    private final SftpSession sftp;
    private final SshSession ssh;
    private final ExecutorService browseExecutor;
    private final Consumer<SftpBrowserWindow> onClose;
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private final LocalPane localPane;
    private final RemotePane remotePane;
    private final Label statusLabel = new Label();
    private final VBox transfersBox = new VBox(4);
    private final ScrollPane transfersScroll = new ScrollPane(transfersBox);
    private final Set<TransferRow> activeRows = new LinkedHashSet<>();

    /**
     * @param title   连接显示名（窗口标题）
     * @param sftp    已打开的 SFTP 会话（用于浏览）
     * @param ssh     依附的 SSH 会话（每个传输从中开新通道；窗口关闭时一并关闭）
     * @param owner   父窗口（可空）
     * @param onClose 关闭回调（从启动器注册表移除）
     */
    public SftpBrowserWindow(String title, SftpSession sftp, SshSession ssh,
                             Window owner, Consumer<SftpBrowserWindow> onClose) {
        this.sftp = sftp;
        this.ssh = ssh;
        this.onClose = onClose;
        this.browseExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sftp-browse-" + title);
            t.setDaemon(true);
            return t;
        });

        this.localPane = new LocalPane(statusLabel::setText, this::upload);
        this.remotePane = new RemotePane(sftp, browseExecutor, statusLabel::setText, this::download);

        this.stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(I18n.t("sftp.title", title));
        stage.setScene(new Scene(buildRoot(), 980, 600));
        stage.setOnCloseRequest(e -> close());
    }

    /** 显示窗口。 */
    public void show() {
        stage.show();
    }

    /** 关闭窗口：取消所有传输并释放浏览执行器、SFTP 与 SSH 会话（幂等）。 */
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        activeRows.forEach(TransferRow::requestCancel);
        browseExecutor.shutdownNow();
        Thread closer = new Thread(() -> {
            try {
                sftp.close();
            } catch (RuntimeException ex) {
                log.warn("Error closing SFTP: {}", ex.getMessage());
            }
            ssh.close();
        }, "sftp-close");
        closer.setDaemon(true);
        closer.start();
        if (onClose != null) {
            onClose.accept(this);
        }
        if (stage.isShowing()) {
            stage.close();
        }
    }

    private VBox buildRoot() {
        HBox panes = new HBox(8, localPane.getView(), remotePane.getView());
        HBox.setHgrow(localPane.getView(), Priority.ALWAYS);
        HBox.setHgrow(remotePane.getView(), Priority.ALWAYS);
        VBox.setVgrow(panes, Priority.ALWAYS);

        transfersBox.setPadding(new Insets(4, 8, 4, 8));
        transfersScroll.setFitToWidth(true);
        transfersScroll.setMaxHeight(140);
        transfersScroll.setVisible(false);
        transfersScroll.setManaged(false);

        HBox statusBar = new HBox(statusLabel);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(4, 8, 4, 8));
        statusBar.setStyle("-fx-border-color: -fx-accent; -fx-border-width: 1 0 0 0;");

        return new VBox(panes, transfersScroll, statusBar);
    }

    /** 本地选中项 → 远程当前目录（左栏右键「上传」）。文件夹则递归上传整目录。 */
    private void upload() {
        LocalPane.LocalEntry entry = localPane.selected();
        if (entry == null) {
            return;
        }
        if (entry.directory()) {
            uploadFolder(entry);
        } else {
            uploadFile(entry);
        }
    }

    /** 单文件上传：远程已存在则先弹覆盖确认，拒绝则取消该次传输。 */
    private void uploadFile(LocalPane.LocalEntry entry) {
        File local = entry.path().toFile();
        String remote = remotePane.resolve(entry.name());
        startTransfer(true, entry.name(), (channel, progress, cancelled) -> {
            if (channel.statEntry(remote).isPresent()
                    && askOverwrite(entry.name(), false) != UiDialogs.OverwriteChoice.OVERWRITE) {
                throw new SftpCancelledException("overwrite declined");
            }
            channel.upload(local, remote, progress, cancelled);
        }, remotePane::refresh);
    }

    /** 文件夹上传：递归建远程目录、逐个上传文件；进度为累计字节，支持「全部覆盖/跳过」。 */
    private void uploadFolder(LocalPane.LocalEntry entry) {
        Path localRoot = entry.path();
        String remoteRoot = remotePane.resolve(entry.name());
        startTransfer(true, entry.name(),
                (channel, progress, cancelled) ->
                        new FolderUpload(channel, progress, cancelled).run(localRoot, remoteRoot),
                remotePane::refresh);
    }

    /** 远程选中文件 → 本地当前目录（右栏右键「下载」）。 */
    private void download() {
        SftpEntry entry = remotePane.selected();
        if (entry == null) {
            return;
        }
        if (entry.directory()) {
            UiDialogs.error(I18n.t("sftp.error.dir_unsupported"));
            return;
        }
        String remote = remotePane.resolve(entry.name());
        Path local = localPane.currentDir().resolve(entry.name());
        startTransfer(false, entry.name(),
                (channel, progress, cancelled) -> channel.download(remote, local.toFile(), progress, cancelled),
                localPane::refresh);
    }

    /**
     * 启动一次传输：独立线程 + 独立 SFTP 通道，添加一行进度，结束后移除。
     *
     * @param uploading true 上传 / false 下载（仅用于文案）
     * @param name      文件名
     * @param action    实际传输动作（接收独立通道、进度回调、取消标志）
     * @param onDone    结束后在 FX 线程的刷新动作
     */
    private void startTransfer(boolean uploading, String name, TransferAction action, Runnable onDone) {
        TransferRow row = new TransferRow(name);
        addRow(row);
        statusLabel.setText(I18n.t(uploading ? "sftp.status.uploading" : "sftp.status.downloading", name));

        // 进度回调（传输线程）：按 ~150ms 节流，计算瞬时速度，回 FX 线程更新本行。
        long[] lastNanos = {System.nanoTime()};
        long[] lastBytes = {0};
        SftpProgress progress = (transferred, total) -> {
            long now = System.nanoTime();
            long since = now - lastNanos[0];
            boolean done = total > 0 && transferred >= total;
            if (since < 150_000_000L && !done) {
                return;
            }
            double seconds = since / 1_000_000_000.0;
            double speed = seconds > 0 ? (transferred - lastBytes[0]) / seconds : 0;
            lastNanos[0] = now;
            lastBytes[0] = transferred;
            double frac = total > 0 ? (double) transferred / total : ProgressBar.INDETERMINATE_PROGRESS;
            String text = formatTransfer(transferred, total, speed);
            Platform.runLater(() -> row.update(frac, text));
        };

        Thread worker = new Thread(() -> {
            SftpSession channel = null;
            try {
                channel = ssh.openSftp();
                action.run(channel, progress, row::cancelled);
                Platform.runLater(() -> {
                    if (closing.get()) {
                        return;
                    }
                    removeRow(row);
                    statusLabel.setText(I18n.t(
                            uploading ? "sftp.status.upload_done" : "sftp.status.download_done", name));
                    onDone.run();
                });
            } catch (SftpCancelledException ce) {
                Platform.runLater(() -> onCancelled(row, name));
            } catch (RuntimeException ex) {
                // 窗口关闭 / 用户取消会切断通道，表现为 IOException —— 视为取消，不报错
                if (closing.get() || row.cancelled()) {
                    Platform.runLater(() -> onCancelled(row, name));
                } else {
                    log.warn("Transfer failed for {}: {}", name, ex.getMessage());
                    String message = SftpErrors.message(
                            uploading ? "sftp.error.upload" : "sftp.error.download", name, ex);
                    Platform.runLater(() -> {
                        removeRow(row);
                        statusLabel.setText(I18n.t("sftp.status.failed", name));
                        UiDialogs.error(message);
                    });
                }
            } finally {
                if (channel != null) {
                    try {
                        channel.close();
                    } catch (RuntimeException ignore) {
                        // 通道关闭异常忽略
                    }
                }
            }
        }, "sftp-xfer-" + name);
        worker.setDaemon(true);
        worker.start();
    }

    private void addRow(TransferRow row) {
        activeRows.add(row);
        transfersBox.getChildren().add(row.node());
        updateTransfersVisibility();
    }

    /** 传输被取消 / 窗口关闭中断：窗口关闭则静默，否则移除行并提示已取消（FX 线程）。 */
    private void onCancelled(TransferRow row, String name) {
        if (closing.get()) {
            return;
        }
        removeRow(row);
        statusLabel.setText(I18n.t("sftp.status.cancelled", name));
    }

    private void removeRow(TransferRow row) {
        activeRows.remove(row);
        transfersBox.getChildren().remove(row.node());
        updateTransfersVisibility();
    }

    private void updateTransfersVisibility() {
        boolean any = !transfersBox.getChildren().isEmpty();
        transfersScroll.setVisible(any);
        transfersScroll.setManaged(any);
    }

    /** 形如 "42%  12.3/29.0 MB  3.5 MB/s"。 */
    private String formatTransfer(long transferred, long total, double bytesPerSec) {
        String speed = SftpFormat.humanSize(Math.round(bytesPerSec)) + "/s";
        if (total > 0) {
            long percent = transferred * 100 / total;
            return percent + "%  " + mb(transferred) + "/" + mb(total) + " MB  " + speed;
        }
        return mb(transferred) + " MB  " + speed;
    }

    private static String mb(long bytes) {
        return String.format("%.1f", bytes / 1024.0 / 1024.0);
    }

    /** 传输动作：接收独立 SFTP 通道、进度回调与取消标志。 */
    @FunctionalInterface
    private interface TransferAction {
        void run(SftpSession channel, SftpProgress progress, BooleanSupplier cancelled);
    }

    /** 在 FX 线程弹出覆盖确认并阻塞当前（传输）线程直到用户作答；窗口关闭中视为取消。 */
    private UiDialogs.OverwriteChoice askOverwrite(String name, boolean offerAll) {
        CompletableFuture<UiDialogs.OverwriteChoice> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (closing.get()) {
                future.complete(UiDialogs.OverwriteChoice.CANCEL);
            } else {
                future.complete(UiDialogs.confirmOverwrite(name, offerAll));
            }
        });
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return UiDialogs.OverwriteChoice.CANCEL;
        } catch (ExecutionException e) {
            return UiDialogs.OverwriteChoice.CANCEL;
        }
    }

    /** 递归累加本地目录下所有普通文件的字节数（用于文件夹上传的总进度）。 */
    private static long totalSize(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    /** 列出本地目录下的直接子项，按名称（不分大小写）排序。 */
    private static List<Path> listSorted(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase())).toList();
        } catch (IOException e) {
            throw new SftpOperationException(SftpOperationException.STATUS_UNKNOWN, "Failed to read local directory: " + dir);
        }
    }

    private static String joinRemote(String base, String name) {
        return base.endsWith("/") ? base + name : base + "/" + name;
    }

    /**
     * 一次文件夹上传的可变状态：累计进度 + 「全部覆盖/全部跳过」记忆。运行在传输线程，
     * 用独立 SFTP 通道串行执行（递归建目录、逐文件上传）。
     */
    private final class FolderUpload {

        private final SftpSession channel;
        private final SftpProgress progress;
        private final BooleanSupplier cancelled;
        private long totalBytes;
        private long sentBytes;
        private Boolean overwriteAll; // null=每次询问, TRUE=全部覆盖, FALSE=全部跳过

        FolderUpload(SftpSession channel, SftpProgress progress, BooleanSupplier cancelled) {
            this.channel = channel;
            this.progress = progress;
            this.cancelled = cancelled;
        }

        void run(Path localRoot, String remoteRoot) {
            totalBytes = totalSize(localRoot);
            progress.update(0, totalBytes);
            ensureDir(remoteRoot);
            walk(localRoot, remoteRoot);
            progress.update(totalBytes, totalBytes);
        }

        private void walk(Path localDir, String remoteDir) {
            for (Path child : listSorted(localDir)) {
                checkCancel();
                String remoteChild = joinRemote(remoteDir, child.getFileName().toString());
                if (Files.isDirectory(child)) {
                    ensureDir(remoteChild);
                    walk(child, remoteChild);
                } else {
                    uploadOne(child, remoteChild);
                }
            }
        }

        private void uploadOne(Path file, String remote) {
            long size = file.toFile().length();
            if (channel.statEntry(remote).isPresent() && !shouldOverwrite(file.getFileName().toString())) {
                sentBytes += size; // 跳过也推进累计进度，使进度条最终归满
                progress.update(sentBytes, totalBytes);
                return;
            }
            long base = sentBytes;
            channel.upload(file.toFile(), remote,
                    (transferred, total) -> progress.update(base + transferred, totalBytes), cancelled);
            sentBytes = base + size;
        }

        /** 远程目录不存在则创建；已是目录则跳过；若同名是文件则报错中止。 */
        private void ensureDir(String remote) {
            Optional<SftpEntry> st = channel.statEntry(remote);
            if (st.isEmpty()) {
                channel.mkdir(remote);
            } else if (!st.get().directory()) {
                throw new SftpOperationException(SftpOperationException.STATUS_UNKNOWN, "Remote path is a file, expected a directory: " + remote);
            }
        }

        /** 结合「全部」记忆判断是否覆盖；用户选取消则抛出取消异常中止整个文件夹。 */
        private boolean shouldOverwrite(String name) {
            if (overwriteAll != null) {
                return overwriteAll;
            }
            switch (askOverwrite(name, true)) {
                case OVERWRITE:
                    return true;
                case SKIP:
                    return false;
                case OVERWRITE_ALL:
                    overwriteAll = Boolean.TRUE;
                    return true;
                case SKIP_ALL:
                    overwriteAll = Boolean.FALSE;
                    return false;
                default:
                    throw new SftpCancelledException("folder upload cancelled");
            }
        }

        private void checkCancel() {
            if (cancelled.getAsBoolean()) {
                throw new SftpCancelledException("cancelled");
            }
        }
    }
}
