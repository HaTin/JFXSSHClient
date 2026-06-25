package com.xxx.jfxssh.ui.terminal;

import com.jediterm.terminal.ui.JediTermWidget;
import com.xxx.jfxssh.common.config.AppConfig;
import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.ssh.SshService;
import com.xxx.jfxssh.ssh.SshSession;
import com.xxx.jfxssh.ssh.SshShell;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.terminal.SshTtyConnector;
import com.xxx.jfxssh.terminal.ThemedTerminalSettings;
import com.xxx.jfxssh.ui.dialog.UiDialogs;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;

/**
 * 终端标签页区域（见 docs/UI_DESIGN.md）。
 *
 * <p>一个标签对应一个 SSH 会话。状态点：◐ 连接中、● 已连接、○ 断开。
 * 仅在 SSH 连接成功后才创建标签；连接失败只提示，不留空标签。</p>
 *
 * <p><b>单 SwingNode + 内容切换：</b>JavaFX 多个 SwingNode 会让键盘焦点整体失效，
 * 因此全程只用一个永不移动的 SwingNode；切换标签时用 {@code setContent} 把它的
 * 内容直接设为选中终端的控件——强制重新渲染，避免 CardLayout 切卡后画面滞留
 * （需点击才刷新）的问题。未显示的会话仍在后台保持。</p>
 */
public final class TerminalTabsPane {

    private static final Logger log = LoggerFactory.getLogger(TerminalTabsPane.class);

    private static final int COLUMNS = 80;
    private static final int ROWS = 24;
    private static final String DOT_CONNECTED = "●";
    private static final String DOT_DISCONNECTED = "○";

    private static final String STYLE_TAB = "-fx-padding: 3 8 3 8;";
    private static final String STYLE_TAB_SELECTED =
            "-fx-padding: 3 8 3 8; -fx-background-color: derive(-fx-accent, 50%);";

    private final SshService sshService;
    private final AppConfig config;
    private final BorderPane root = new BorderPane();
    private final HBox tabBar = new HBox(2);
    private final SwingNode swingNode = new SwingNode();
    private final Map<String, Entry> entries = new HashMap<>();

    private volatile boolean dark = true;

    private volatile JComponent welcomePanel;
    private String selectedCardId;
    private int counter;

    /**
     * @param sshService SSH 服务
     * @param config     应用配置（终端字体等）
     */
    public TerminalTabsPane(SshService sshService, AppConfig config) {
        this.sshService = sshService;
        this.config = config;
        root.setId("TerminalTabs");
        tabBar.setPadding(new Insets(2));
        tabBar.setAlignment(Pos.CENTER_LEFT);
        root.setTop(tabBar);
        // 允许收缩到任意宽度，避免 SwingNode 最小尺寸导致拖动分隔条时被挤压留白
        StackPane center = new StackPane(swingNode);
        center.setMinSize(0, 0);
        root.setCenter(center);
        root.setMinWidth(0);

        SwingUtilities.invokeLater(() -> {
            JPanel welcome = new JPanel(new BorderLayout());
            welcome.add(new JLabel(I18n.t("tab.no_session"), SwingConstants.CENTER), BorderLayout.CENTER);
            welcomePanel = welcome;
            swingNode.setContent(welcome);
        });
    }

    /**
     * @return 终端区域节点
     */
    public BorderPane getView() {
        return root;
    }

    /**
     * 应用深色 / 浅色主题。已连接的终端保持原配色（避免 JediTerm 运行时重绘的
     * 缓存问题），仅影响此后新建的终端；欢迎面板会随主题更新。
     *
     * @param dark 是否深色
     */
    public void applyDarkTheme(boolean dark) {
        this.dark = dark;
        Color bg = dark ? new Color(0x1E, 0x1E, 0x1E) : Color.WHITE;
        Color fg = dark ? new Color(0xD0, 0xD0, 0xD0) : new Color(0x1F, 0x1F, 0x1F);
        SwingUtilities.invokeLater(() -> {
            if (welcomePanel != null) {
                welcomePanel.setBackground(bg);
                welcomePanel.setForeground(fg);
                welcomePanel.repaint();
            }
        });
    }

    /**
     * 为连接打开终端：先后台建立 SSH 连接，成功才创建标签。
     *
     * @param connection 连接
     * @param config     SSH 连接配置
     */
    public void openTerminal(Connection connection, SshConnectionConfig config) {
        String name = displayName(connection);
        String target = config.getHost() + ":" + config.getPort();
        Thread worker = new Thread(() -> {
            try {
                SshSession session = sshService.connect(config);
                SshShell shell = session.openShell(COLUMNS, ROWS);
                Platform.runLater(() -> createTab(name, config, session, shell));
            } catch (RuntimeException ex) {
                log.warn("Connect failed for {}: {}", target, ex.getMessage());
                Platform.runLater(() -> UiDialogs.error(I18n.t("msg.connect.fail", target)));
            }
        }, "ssh-connect-" + name);
        worker.setDaemon(true);
        worker.start();
    }

    /** 连接成功后创建标签并挂载终端（FX 线程）。 */
    private void createTab(String name, SshConnectionConfig config, SshSession session, SshShell shell) {
        String cardId = "term-" + (counter++);
        Entry entry = new Entry(cardId, name, config);

        entry.label = new Label(tabText(DOT_CONNECTED, name));
        Button closeButton = new Button("✕");
        closeButton.setStyle("-fx-padding: 0 4 0 4; -fx-background-color: transparent;");
        closeButton.setOnAction(e -> closeCard(cardId));
        entry.header = new HBox(4, entry.label, closeButton);
        entry.header.setAlignment(Pos.CENTER_LEFT);
        entry.header.setOnMouseClicked(e -> selectCard(cardId));

        entries.put(cardId, entry);
        tabBar.getChildren().add(entry.header);

        selectedCardId = cardId;
        attachSession(entry, session, shell);
        updateTabStyles();
    }

    /** 在已有标签内挂载一个会话（创建 JediTerm 控件、绑定连接器、监听关闭）。 */
    private void attachSession(Entry entry, SshSession session, SshShell shell) {
        SshTtyConnector connector = new SshTtyConnector(shell, entry.name,
                () -> Platform.runLater(() -> reconnect(entry.cardId)));
        SwingUtilities.invokeLater(() -> {
            JediTermWidget widget = new JediTermWidget(COLUMNS, ROWS, new ThemedTerminalSettings(
                    dark,
                    config.get(AppConfig.KEY_TERMINAL_FONT, AppConfig.DEFAULT_TERMINAL_FONT),
                    config.getInt(AppConfig.KEY_TERMINAL_FONT_SIZE, AppConfig.DEFAULT_TERMINAL_FONT_SIZE)));
            // 允许控件收缩到 0，避免分隔条拖动时的留白挤压
            widget.setMinimumSize(new Dimension(0, 0));
            widget.getTerminalPanel().setMinimumSize(new Dimension(0, 0));
            widget.setTtyConnector(connector);
            widget.start();
            entry.widget = widget;
            entry.connector = connector;
            entry.session = session;
            if (entry.cardId.equals(selectedCardId)) {
                showContent(widget);
            }
        });
        Platform.runLater(() -> entry.label.setText(tabText(DOT_CONNECTED, entry.name)));
        watchClose(entry.cardId, shell);
    }

    /** 断开后按回车触发：在同一标签重连，失败保留标签并标记断开。 */
    private void reconnect(String cardId) {
        Entry entry = entries.get(cardId);
        if (entry == null) {
            return;
        }
        entry.label.setText(tabText(DOT_DISCONNECTED, entry.name) + " ...");
        releaseSession(entry);
        String target = entry.config.getHost() + ":" + entry.config.getPort();
        Thread worker = new Thread(() -> {
            try {
                SshSession session = sshService.connect(entry.config);
                SshShell shell = session.openShell(COLUMNS, ROWS);
                Platform.runLater(() -> {
                    if (entries.containsKey(cardId)) {
                        attachSession(entry, session, shell);
                    } else {
                        session.close();
                    }
                });
            } catch (RuntimeException ex) {
                log.warn("Reconnect failed for {}: {}", target, ex.getMessage());
                Platform.runLater(() -> {
                    entry.label.setText(tabText(DOT_DISCONNECTED, entry.name));
                    UiDialogs.error(I18n.t("msg.connect.fail", target));
                });
            }
        }, "ssh-reconnect-" + entry.name);
        worker.setDaemon(true);
        worker.start();
    }

    private void selectCard(String cardId) {
        selectedCardId = cardId;
        updateTabStyles();
        Entry entry = entries.get(cardId);
        JComponent content = entry != null ? entry.widget : null;
        showContent(content);
    }

    /** 将 SwingNode 内容切换为给定控件（null 显示欢迎面板），并聚焦。 */
    private void showContent(JComponent component) {
        Platform.runLater(swingNode::requestFocus);
        SwingUtilities.invokeLater(() -> {
            JComponent target = component != null ? component : welcomePanel;
            swingNode.setContent(target);
            if (target instanceof JediTermWidget terminal) {
                terminal.getTerminalPanel().requestFocusInWindow();
            }
        });
    }

    private void updateTabStyles() {
        for (Entry e : entries.values()) {
            e.header.setStyle(e.cardId.equals(selectedCardId) ? STYLE_TAB_SELECTED : STYLE_TAB);
        }
    }

    private void watchClose(String cardId, SshShell shell) {
        Thread watcher = new Thread(() -> {
            shell.waitForClose();
            Platform.runLater(() -> {
                Entry entry = entries.get(cardId);
                if (entry == null) {
                    return;
                }
                entry.label.setText(tabText(DOT_DISCONNECTED, entry.name));
                JediTermWidget widget = entry.widget;
                if (widget != null) {
                    SwingUtilities.invokeLater(() -> printHint(widget, I18n.t("terminal.disconnected_hint")));
                }
            });
        }, "ssh-watch-" + cardId);
        watcher.setDaemon(true);
        watcher.start();
    }

    private void closeCard(String cardId) {
        Entry entry = entries.remove(cardId);
        if (entry == null) {
            return;
        }
        tabBar.getChildren().remove(entry.header);
        releaseSession(entry);
        String next = entries.keySet().stream().findFirst().orElse(null);
        if (next == null) {
            selectedCardId = null;
            showContent(null);
        } else {
            selectCard(next);
        }
    }

    /** 释放会话与控件（重连或关闭时调用），不移除标签头。 */
    private void releaseSession(Entry entry) {
        SshTtyConnector oldConnector = entry.connector;
        SshSession oldSession = entry.session;
        JediTermWidget oldWidget = entry.widget;
        entry.connector = null;
        entry.session = null;
        entry.widget = null;
        if (oldConnector != null) {
            oldConnector.close();
        }
        if (oldSession != null) {
            oldSession.close();
        }
        if (oldWidget != null) {
            SwingUtilities.invokeLater(oldWidget::close);
        }
    }

    private void printHint(JediTermWidget widget, String text) {
        widget.getTerminal().carriageReturn();
        widget.getTerminal().newLine();
        widget.getTerminal().writeCharacters(text);
        widget.getTerminal().carriageReturn();
        widget.getTerminal().newLine();
    }

    private String tabText(String dot, String name) {
        return dot + " " + name;
    }

    private String displayName(Connection c) {
        return c.getName() != null && !c.getName().isBlank() ? c.getName() : c.getHost();
    }

    /** 每个终端标签的状态。widget/connector/session 在 EDT 创建、可能被 FX 读，故 volatile。 */
    private static final class Entry {
        private final String cardId;
        private final String name;
        private final SshConnectionConfig config;
        private Label label;
        private HBox header;
        private volatile JediTermWidget widget;
        private volatile SshTtyConnector connector;
        private volatile SshSession session;

        Entry(String cardId, String name, SshConnectionConfig config) {
            this.cardId = cardId;
            this.name = name;
            this.config = config;
        }
    }
}
