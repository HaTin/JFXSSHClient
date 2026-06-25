package com.xxx.jfxssh.ui.terminal;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.ssh.SshService;
import com.xxx.jfxssh.ssh.SshSession;
import com.xxx.jfxssh.ssh.SshShell;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.terminal.SshTtyConnector;
import com.xxx.jfxssh.terminal.TerminalView;
import com.xxx.jfxssh.ui.dialog.UiDialogs;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 终端标签页区域（见 docs/UI_DESIGN.md）。
 *
 * <p>一个 Tab 对应一个 SSH 会话。打开连接时新建标签，后台建立连接 + 打开 shell，
 * 成功后将终端视图挂入标签；标题前缀以状态点表示：◐ 连接中、● 已连接、○ 断开。</p>
 */
public final class TerminalTabsPane {

    private static final Logger log = LoggerFactory.getLogger(TerminalTabsPane.class);

    private static final int COLUMNS = 80;
    private static final int ROWS = 24;
    private static final String DOT_CONNECTING = "◐";
    private static final String DOT_CONNECTED = "●";
    private static final String DOT_DISCONNECTED = "○";

    private final SshService sshService;
    private final TabPane tabs = new TabPane();

    /**
     * @param sshService SSH 服务
     */
    public TerminalTabsPane(SshService sshService) {
        this.sshService = sshService;
        tabs.setId("TerminalTabs");
        addWelcomeTab();
        // 切换到某终端标签时，把焦点交给它的终端
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null && sel.getUserData() instanceof TerminalView view) {
                view.requestTerminalFocus();
            }
        });
    }

    /**
     * @return 标签页节点
     */
    public TabPane getView() {
        return tabs;
    }

    /**
     * 为连接打开终端标签页。
     *
     * @param connection 连接
     * @param config     SSH 连接配置
     */
    public void openTerminal(Connection connection, SshConnectionConfig config) {
        Tab tab = new Tab();
        tab.setOnClosed(e -> closeTabView(tab));
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
        connectIntoTab(tab, connection, config);
    }

    /**
     * 在指定标签内（重新）建立连接并挂载终端。首连与重连复用此逻辑。
     */
    private void connectIntoTab(Tab tab, Connection connection, SshConnectionConfig config) {
        String name = displayName(connection);

        // 重连时先释放旧视图
        closeTabView(tab);

        TerminalView view = new TerminalView();
        tab.setText(tabText(DOT_CONNECTING, name));
        tab.setContent(view.getNode());
        tab.setUserData(view);

        Thread worker = new Thread(() -> {
            try {
                SshSession session = sshService.connect(config);
                SshShell shell = session.openShell(COLUMNS, ROWS);
                SshTtyConnector connector = new SshTtyConnector(shell, name,
                        () -> Platform.runLater(() -> connectIntoTab(tab, connection, config)));
                view.attach(connector, session);
                Platform.runLater(() -> {
                    tab.setText(tabText(DOT_CONNECTED, name));
                    view.requestTerminalFocus();
                });
                watchClose(view, shell, tab, name);
            } catch (RuntimeException ex) {
                log.warn("Terminal connect failed for {}: {}", name, ex.getMessage());
                Platform.runLater(() -> {
                    tab.setText(tabText(DOT_DISCONNECTED, name));
                    UiDialogs.error(I18n.t("msg.connect.fail", config.getHost() + ":" + config.getPort()));
                });
            }
        }, "ssh-terminal-" + name);
        worker.setDaemon(true);
        worker.start();
    }

    private void watchClose(TerminalView view, SshShell shell, Tab tab, String name) {
        Thread watcher = new Thread(() -> {
            shell.waitForClose();
            Platform.runLater(() -> {
                tab.setText(tabText(DOT_DISCONNECTED, name));
                view.showHint(I18n.t("terminal.disconnected_hint"));
            });
        }, "ssh-watch-" + name);
        watcher.setDaemon(true);
        watcher.start();
    }

    private void closeTabView(Tab tab) {
        if (tab.getUserData() instanceof TerminalView view) {
            view.close();
        }
    }

    private String tabText(String dot, String name) {
        return dot + " " + name;
    }

    private String displayName(Connection c) {
        return c.getName() != null && !c.getName().isBlank() ? c.getName() : c.getHost();
    }

    private void addWelcomeTab() {
        Label welcome = new Label();
        welcome.textProperty().bind(I18n.tp("tab.no_session"));

        Tab welcomeTab = new Tab();
        welcomeTab.textProperty().bind(I18n.tp("tab.welcome"));
        welcomeTab.setContent(new StackPane(welcome));
        welcomeTab.setClosable(false);
        tabs.getTabs().add(welcomeTab);
    }
}
