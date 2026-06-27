package com.xxx.jfxssh.ui.forward;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.ssh.SshService;
import com.xxx.jfxssh.ssh.SshSession;
import com.xxx.jfxssh.service.PortForwardService;
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
 * {@link PortForwardLauncher} 实现：后台建立 SSH 连接，成功后弹出独立的端口转发管理窗口。
 *
 * <p>每个连接一个 {@link PortForwardWindow}（独立 {@link SshSession}）。维护已打开窗口集合，
 * 应用退出时统一关闭。</p>
 */
public final class PortForwardWindowLauncher implements PortForwardLauncher {

    private static final Logger log = LoggerFactory.getLogger(PortForwardWindowLauncher.class);

    private final SshService sshService;
    private final PortForwardService portForwardService;
    private final Supplier<Window> ownerSupplier;
    private final Set<PortForwardWindow> openWindows = ConcurrentHashMap.newKeySet();

    /**
     * @param sshService         SSH 服务
     * @param portForwardService 端口转发规则服务
     * @param ownerSupplier      主窗口提供者（构造期 Scene 尚未就绪，故延迟获取，可返回 null）
     */
    public PortForwardWindowLauncher(SshService sshService,
                                     PortForwardService portForwardService,
                                     Supplier<Window> ownerSupplier) {
        this.sshService = sshService;
        this.portForwardService = portForwardService;
        this.ownerSupplier = ownerSupplier;
    }

    @Override
    public void open(Connection connection, SshConnectionConfig config) {
        String name = displayName(connection);
        String target = config.getHost() + ":" + config.getPort();
        Thread worker = new Thread(() -> {
            try {
                SshSession ssh = sshService.connect(config);
                Platform.runLater(() -> {
                    Window owner = ownerSupplier == null ? null : ownerSupplier.get();
                    PortForwardWindow window =
                            new PortForwardWindow(name, connection, ssh, sshService, config, portForwardService, owner, openWindows::remove);
                    openWindows.add(window);
                    window.show();
                });
            } catch (RuntimeException ex) {
                log.warn("Port forward connect failed for {}: {}", target, ex.getMessage());
                Platform.runLater(() -> UiDialogs.error(I18n.t("msg.forward.connect.fail", target)));
            }
        }, "forward-connect-" + name);
        worker.setDaemon(true);
        worker.start();
    }

    /** 关闭所有已打开的端口转发窗口（应用退出时调用）。 */
    public void closeAll() {
        for (PortForwardWindow window : openWindows) {
            window.close();
        }
        openWindows.clear();
    }

    private String displayName(Connection c) {
        return c.getName() != null && !c.getName().isBlank() ? c.getName() : c.getHost();
    }
}
