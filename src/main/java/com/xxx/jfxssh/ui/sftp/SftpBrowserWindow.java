package com.xxx.jfxssh.ui.sftp;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ssh.SftpCancelledException;
import com.xxx.jfxssh.ssh.SftpEntry;
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
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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

    /** 本地选中文件 → 远程当前目录（左栏右键「上传」）。 */
    private void upload() {
        LocalPane.LocalEntry entry = localPane.selected();
        if (entry == null) {
            return;
        }
        if (entry.directory()) {
            UiDialogs.error(I18n.t("sftp.error.dir_unsupported"));
            return;
        }
        File local = entry.path().toFile();
        String remote = remotePane.resolve(entry.name());
        startTransfer(true, entry.name(),
                (channel, progress, cancelled) -> channel.upload(local, remote, progress, cancelled),
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
                    removeRow(row);
                    statusLabel.setText(I18n.t(
                            uploading ? "sftp.status.upload_done" : "sftp.status.download_done", name));
                    onDone.run();
                });
            } catch (SftpCancelledException ce) {
                Platform.runLater(() -> {
                    removeRow(row);
                    statusLabel.setText(I18n.t("sftp.status.cancelled", name));
                    onDone.run();
                });
            } catch (RuntimeException ex) {
                log.warn("Transfer failed for {}: {}", name, ex.getMessage());
                String message = SftpErrors.message(
                        uploading ? "sftp.error.upload" : "sftp.error.download", name, ex);
                Platform.runLater(() -> {
                    removeRow(row);
                    statusLabel.setText(I18n.t("sftp.status.failed", name));
                    UiDialogs.error(message);
                });
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
}
