package com.xxx.jfxssh.ui.terminal;

import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.ssh.SshService;
import com.xxx.jfxssh.ssh.SshSession;
import com.xxx.jfxssh.ssh.SshShell;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.terminal.SshTtyConnector;
import com.xxx.jfxssh.ui.dialog.UiDialogs;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.util.HashMap;
import java.util.Map;

/**
 * 终端标签页区域（见 docs/UI_DESIGN.md）。
 *
 * <p>一个 Tab 对应一个 SSH 会话。状态点：◐ 连接中、● 已连接、○ 断开。</p>
 *
 * <p><b>单 SwingNode 设计：</b>JavaFX 同一场景内存在多个 SwingNode 会导致键盘
 * 焦点整体失效，因此全程只用一个 SwingNode，内部用 Swing CardLayout 承载各终端
 * 控件；切换标签时把唯一的 SwingNode 移入当前标签并切到对应卡片。</p>
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
    private final SwingNode swingNode = new SwingNode();
    private final JPanel cards = new JPanel(new CardLayout());
    private final StackPane terminalHolder = new StackPane(swingNode);
    private final Map<Tab, Holder> holders = new HashMap<>();

    private int counter;

    /**
     * @param sshService SSH 服务
     */
    public TerminalTabsPane(SshService sshService) {
        this.sshService = sshService;
        tabs.setId("TerminalTabs");
        SwingUtilities.invokeLater(() -> swingNode.setContent(cards));
        addWelcomeTab();
        tabs.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldTab, newTab) -> onTabSelected(oldTab, newTab));
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
        String cardId = "term-" + (counter++);
        Tab tab = new Tab();
        tab.setUserData(cardId);
        tab.setOnClosed(e -> closeTab(tab));
        holders.put(tab, new Holder(cardId));
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
        connectIntoTab(tab, connection, config);
    }

    /** 移动唯一的 SwingNode 到选中的终端标签，并切到其卡片。 */
    private void onTabSelected(Tab oldTab, Tab newTab) {
        if (oldTab != null && holders.containsKey(oldTab) && oldTab.getContent() == terminalHolder) {
            oldTab.setContent(null);
        }
        if (newTab != null && holders.containsKey(newTab)) {
            newTab.setContent(terminalHolder);
            focus(holders.get(newTab));
        }
    }

    /** 在指定标签内（重新）建立连接并挂载终端。首连与重连复用此逻辑。 */
    private void connectIntoTab(Tab tab, Connection connection, SshConnectionConfig config) {
        Holder holder = holders.get(tab);
        if (holder == null) {
            return;
        }
        String name = displayName(connection);
        tab.setText(tabText(DOT_CONNECTING, name));
        removeWidget(holder);

        Thread worker = new Thread(() -> {
            try {
                SshSession session = sshService.connect(config);
                SshShell shell = session.openShell(COLUMNS, ROWS);
                SshTtyConnector connector = new SshTtyConnector(shell, name,
                        () -> Platform.runLater(() -> connectIntoTab(tab, connection, config)));
                SwingUtilities.invokeLater(() -> {
                    JediTermWidget widget = new JediTermWidget(COLUMNS, ROWS, new DefaultSettingsProvider());
                    widget.setTtyConnector(connector);
                    widget.start();
                    cards.add(widget, holder.cardId);
                    holder.widget = widget;
                    holder.connector = connector;
                    holder.session = session;
                    showCard(holder.cardId);
                    widget.getTerminalPanel().requestFocusInWindow();
                });
                Platform.runLater(() -> {
                    tab.setText(tabText(DOT_CONNECTED, name));
                    if (tabs.getSelectionModel().getSelectedItem() == tab) {
                        swingNode.requestFocus();
                    }
                });
                watchClose(tab, shell, name);
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

    private void watchClose(Tab tab, SshShell shell, String name) {
        Thread watcher = new Thread(() -> {
            shell.waitForClose();
            Platform.runLater(() -> {
                tab.setText(tabText(DOT_DISCONNECTED, name));
                Holder holder = holders.get(tab);
                if (holder != null && holder.widget != null) {
                    JediTermWidget widget = holder.widget;
                    SwingUtilities.invokeLater(() -> printHint(widget, I18n.t("terminal.disconnected_hint")));
                }
            });
        }, "ssh-watch-" + name);
        watcher.setDaemon(true);
        watcher.start();
    }

    private void focus(Holder holder) {
        Platform.runLater(swingNode::requestFocus);
        SwingUtilities.invokeLater(() -> {
            showCard(holder.cardId);
            if (holder.widget != null) {
                holder.widget.getTerminalPanel().requestFocusInWindow();
            }
        });
    }

    private void showCard(String cardId) {
        ((CardLayout) cards.getLayout()).show(cards, cardId);
    }

    private void printHint(JediTermWidget widget, String text) {
        widget.getTerminal().carriageReturn();
        widget.getTerminal().newLine();
        widget.getTerminal().writeCharacters(text);
        widget.getTerminal().carriageReturn();
        widget.getTerminal().newLine();
    }

    private void closeTab(Tab tab) {
        Holder holder = holders.remove(tab);
        if (holder != null) {
            removeWidget(holder);
        }
    }

    /** 释放 holder 当前的会话与控件（重连或关闭时调用）。 */
    private void removeWidget(Holder holder) {
        JediTermWidget oldWidget = holder.widget;
        SshTtyConnector oldConnector = holder.connector;
        SshSession oldSession = holder.session;
        holder.widget = null;
        holder.connector = null;
        holder.session = null;
        if (oldConnector != null) {
            oldConnector.close();
        }
        if (oldSession != null) {
            oldSession.close();
        }
        if (oldWidget != null) {
            SwingUtilities.invokeLater(() -> {
                cards.remove(oldWidget);
                oldWidget.close();
            });
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

    /** 每个终端标签的会话与控件。 */
    private static final class Holder {
        private final String cardId;
        private JediTermWidget widget;
        private SshTtyConnector connector;
        private SshSession session;

        Holder(String cardId) {
            this.cardId = cardId;
        }
    }
}
