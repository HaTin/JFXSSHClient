package com.xxx.jfxssh.ui.sftp;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ssh.SftpSession;
import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.ssh.SshService;
import com.xxx.jfxssh.ssh.SshSession;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.ui.dialog.UiDialogs;
import javafx.application.Platform;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * {@link SftpLauncher} 实现：后台建立 SSH 连接并打开 SFTP，成功后弹出独立浏览窗口。
 *
 * <p>每个连接一个 {@link SftpBrowserWindow}（独立 {@link SshSession}，与终端互不影响）。
 * 维护已打开窗口集合，应用退出时统一关闭。</p>
 */
public final class SftpBrowserLauncher implements SftpLauncher {

    private static final Logger log = LoggerFactory.getLogger(SftpBrowserLauncher.class);

    private final SshService sshService;
    private final Supplier<Window> ownerSupplier;
    private final Set<SftpBrowserWindow> openWindows = ConcurrentHashMap.newKeySet();

    /**
     * @param sshService    SSH 服务
     * @param ownerSupplier 主窗口提供者（构造期 Scene 尚未就绪，故延迟获取，可返回 null）
     */
    public SftpBrowserLauncher(SshService sshService, Supplier<Window> ownerSupplier) {
        this.sshService = sshService;
        this.ownerSupplier = ownerSupplier;
    }

    @Override
    public void open(Connection connection, SshConnectionConfig config) {
        String name = displayName(connection);
        String target = config.getHost() + ":" + config.getPort();
        Thread worker = new Thread(() -> {
            try {
                SshSession ssh = sshService.connect(config);
                SftpSession sftp = ssh.openSftp();
                Platform.runLater(() -> {
                    Window owner = ownerSupplier == null ? null : ownerSupplier.get();
                    SftpBrowserWindow window =
                            new SftpBrowserWindow(name, sftp, ssh, owner, openWindows::remove);
                    openWindows.add(window);
                    window.show();
                });
            } catch (RuntimeException ex) {
                log.warn("SFTP open failed for {}: {}", target, ex.getMessage());
                Platform.runLater(() -> UiDialogs.error(I18n.t("msg.sftp.connect.fail", target)));
            }
        }, "sftp-connect-" + name);
        worker.setDaemon(true);
        worker.start();
    }

    /** 关闭所有已打开的 SFTP 窗口（应用退出时调用）。 */
    public void closeAll() {
        for (SftpBrowserWindow window : openWindows) {
            window.close();
        }
        openWindows.clear();
    }

    private String displayName(Connection c) {
        return c.getName() != null && !c.getName().isBlank() ? c.getName() : c.getHost();
    }
}
