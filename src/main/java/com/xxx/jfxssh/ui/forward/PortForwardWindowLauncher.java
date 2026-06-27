package com.xxx.jfxssh.ui.forward;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.service.ActivePortForwardService;
import com.xxx.jfxssh.service.PortForwardService;
import com.xxx.jfxssh.ssh.SshConnectionConfig;
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
 * {@link PortForwardLauncher} 实现：弹出独立的端口转发管理窗口。
 *
 * <p>每个连接可打开一个 {@link PortForwardWindow}；窗口关闭后，已启动的转发由
 * {@link ActivePortForwardService} 继续在后台运行。本启动器只维护窗口注册表。</p>
 */
public final class PortForwardWindowLauncher implements PortForwardLauncher {

    private static final Logger log = LoggerFactory.getLogger(PortForwardWindowLauncher.class);

    private final PortForwardService portForwardService;
    private final ActivePortForwardService activeForwardService;
    private final Supplier<Window> ownerSupplier;
    private final Set<PortForwardWindow> openWindows = ConcurrentHashMap.newKeySet();

    /**
     * @param portForwardService   端口转发规则持久化服务
     * @param activeForwardService 后台转发服务
     * @param ownerSupplier        主窗口提供者（构造期 Scene 尚未就绪，故延迟获取，可返回 null）
     */
    public PortForwardWindowLauncher(PortForwardService portForwardService,
                                     ActivePortForwardService activeForwardService,
                                     Supplier<Window> ownerSupplier) {
        this.portForwardService = portForwardService;
        this.activeForwardService = activeForwardService;
        this.ownerSupplier = ownerSupplier;
    }

    @Override
    public void open(Connection connection, SshConnectionConfig config) {
        String name = displayName(connection);
        String target = config.getHost() + ":" + config.getPort();

        // 同一连接若已有打开窗口，直接置前（简化处理：这里仍允许打开多个）
        Platform.runLater(() -> {
            try {
                Window owner = ownerSupplier == null ? null : ownerSupplier.get();
                PortForwardWindow window = new PortForwardWindow(
                        name, connection, config, portForwardService, activeForwardService,
                        owner, openWindows::remove);
                openWindows.add(window);
                window.show();
            } catch (RuntimeException ex) {
                log.warn("Port forward window failed for {}: {}", target, ex.getMessage());
                UiDialogs.error(I18n.t("msg.forward.connect.fail", target));
            }
        });
    }

    /** 关闭所有已打开的端口转发窗口 UI（不停止后台转发）。 */
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
