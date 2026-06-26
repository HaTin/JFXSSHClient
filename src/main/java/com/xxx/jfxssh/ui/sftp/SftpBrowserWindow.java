package com.xxx.jfxssh.ui.sftp;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ssh.SftpEntry;
import com.xxx.jfxssh.ssh.SftpProgress;
import com.xxx.jfxssh.ssh.SftpSession;
import com.xxx.jfxssh.ssh.SshSession;
import com.xxx.jfxssh.ui.dialog.UiDialogs;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 双栏 SFTP 文件管理窗口（每个连接一个）。
 *
 * <p>左 {@link LocalPane}（本地）、右 {@link RemotePane}（远程），中间上传 / 下载按钮，
 * 底部共享进度条 + 状态栏。所有远程调用与传输经<b>单线程执行器</b>串行化，进度回调节流后
 * 切回 FX 线程。关闭窗口释放执行器、SFTP 与底层 SSH 会话。</p>
 */
public final class SftpBrowserWindow {

    private static final Logger log = LoggerFactory.getLogger(SftpBrowserWindow.class);

    private final Stage stage;
    private final SftpSession sftp;
    private final SshSession ssh;
    private final ExecutorService executor;
    private final Consumer<SftpBrowserWindow> onClose;
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private final LocalPane localPane;
    private final RemotePane remotePane;
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label();
    private Button uploadButton;
    private Button downloadButton;

    /**
     * @param title   连接显示名（窗口标题）
     * @param sftp    已打开的 SFTP 会话
     * @param ssh     依附的 SSH 会话（窗口关闭时一并关闭）
     * @param owner   父窗口（可空）
     * @param onClose 关闭回调（从启动器注册表移除）
     */
    public SftpBrowserWindow(String title, SftpSession sftp, SshSession ssh,
                             Window owner, Consumer<SftpBrowserWindow> onClose) {
        this.sftp = sftp;
        this.ssh = ssh;
        this.onClose = onClose;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sftp-ops-" + title);
            t.setDaemon(true);
            return t;
        });

        this.localPane = new LocalPane(statusLabel::setText);
        this.remotePane = new RemotePane(sftp, executor, statusLabel::setText);

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

    /** 关闭窗口并释放执行器、SFTP 与 SSH 会话（幂等）。 */
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        executor.shutdownNow();
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
        HBox panes = new HBox(6, localPane.getView(), buildMiddle(), remotePane.getView());
        HBox.setHgrow(localPane.getView(), Priority.ALWAYS);
        HBox.setHgrow(remotePane.getView(), Priority.ALWAYS);
        VBox.setVgrow(panes, Priority.ALWAYS);

        progressBar.setMaxWidth(220);
        progressBar.setVisible(false);
        HBox statusBar = new HBox(10, progressBar, statusLabel);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(4, 8, 4, 8));
        statusBar.setStyle("-fx-border-color: -fx-accent; -fx-border-width: 1 0 0 0;");

        VBox root = new VBox(panes, statusBar);
        return root;
    }

    private VBox buildMiddle() {
        uploadButton = new Button(I18n.t("sftp.button.upload"));
        uploadButton.setMaxWidth(Double.MAX_VALUE);
        uploadButton.setOnAction(e -> upload());
        downloadButton = new Button(I18n.t("sftp.button.download"));
        downloadButton.setMaxWidth(Double.MAX_VALUE);
        downloadButton.setOnAction(e -> download());

        VBox box = new VBox(10, uploadButton, downloadButton);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(0, 4, 0, 4));
        return box;
    }

    /** 本地选中文件 → 远程当前目录。 */
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
        transfer(true, entry.name(),
                progress -> sftp.upload(local, remote, progress),
                remotePane::refresh);
    }

    /** 远程选中文件 → 本地当前目录。 */
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
        transfer(false, entry.name(),
                progress -> sftp.download(remote, local.toFile(), progress),
                localPane::refresh);
    }

    /**
     * 在执行器上执行一次传输并驱动进度条。
     *
     * @param uploading  true 上传 / false 下载（仅用于状态文案）
     * @param name       文件名
     * @param action     实际传输动作（接收进度回调）
     * @param onDone     成功后在 FX 线程的刷新动作
     */
    private void transfer(boolean uploading, String name, Consumer<SftpProgress> action, Runnable onDone) {
        setTransferring(true);
        statusLabel.setText(I18n.t(uploading ? "sftp.status.uploading" : "sftp.status.downloading", name));
        progressBar.setProgress(0);
        progressBar.setVisible(true);

        // 节流：仅当百分比变化 >= 1% 时回 FX 线程
        long[] lastPercent = {-1};
        SftpProgress progress = (transferred, total) -> {
            if (total <= 0) {
                return;
            }
            long percent = transferred * 100 / total;
            if (percent != lastPercent[0]) {
                lastPercent[0] = percent;
                Platform.runLater(() -> progressBar.setProgress(percent / 100.0));
            }
        };

        executor.submit(() -> {
            try {
                action.accept(progress);
                Platform.runLater(() -> {
                    progressBar.setProgress(1);
                    progressBar.setVisible(false);
                    statusLabel.setText(I18n.t(
                            uploading ? "sftp.status.upload_done" : "sftp.status.download_done", name));
                    setTransferring(false);
                    onDone.run();
                });
            } catch (RuntimeException ex) {
                log.warn("Transfer failed for {}: {}", name, ex.getMessage());
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    setTransferring(false);
                    UiDialogs.error(I18n.t(
                            uploading ? "sftp.error.upload" : "sftp.error.download", name));
                });
            }
        });
    }

    private void setTransferring(boolean transferring) {
        uploadButton.setDisable(transferring);
        downloadButton.setDisable(transferring);
    }
}
