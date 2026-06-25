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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.HashMap;
import java.util.Map;

/**
 * 终端标签页区域（见 docs/UI_DESIGN.md）。
 *
 * <p>一个标签对应一个 SSH 会话。状态点：◐ 连接中、● 已连接、○ 断开。</p>
 *
 * <p><b>单 SwingNode + 固定布局：</b>JavaFX 多个 SwingNode 会让键盘焦点整体失效，
 * 而把同一个 SwingNode 在标签间反复换父节点又会白屏/镜像。因此这里用一个永不
 * 移动的 SwingNode 承载 Swing CardLayout：每个终端是一张卡片，顶部自定义标签条
 * 驱动 CardLayout 切卡。</p>
 */
public final class TerminalTabsPane {

    private static final Logger log = LoggerFactory.getLogger(TerminalTabsPane.class);

    private static final int COLUMNS = 80;
    private static final int ROWS = 24;
    private static final String WELCOME = "welcome";
    private static final String DOT_CONNECTING = "◐";
    private static final String DOT_CONNECTED = "●";
    private static final String DOT_DISCONNECTED = "○";

    private static final String STYLE_TAB = "-fx-padding: 3 8 3 8;";
    private static final String STYLE_TAB_SELECTED =
            "-fx-padding: 3 8 3 8; -fx-background-color: derive(-fx-accent, 50%);";

    private final SshService sshService;
    private final BorderPane root = new BorderPane();
    private final HBox tabBar = new HBox(2);
    private final SwingNode swingNode = new SwingNode();
    private final JPanel cards = new JPanel(new CardLayout());
    private final Map<String, Entry> entries = new HashMap<>();

    private String selectedCardId;
    private int counter;

    /**
     * @param sshService SSH 服务
     */
    public TerminalTabsPane(SshService sshService) {
        this.sshService = sshService;
        root.setId("TerminalTabs");
        tabBar.setPadding(new Insets(2));
        tabBar.setAlignment(Pos.CENTER_LEFT);
        root.setTop(tabBar);
        root.setCenter(new StackPane(swingNode));

        SwingUtilities.invokeLater(() -> {
            JPanel welcome = new JPanel(new BorderLayout());
            welcome.add(new JLabel(I18n.t("tab.no_session"), SwingConstants.CENTER), BorderLayout.CENTER);
            cards.add(welcome, WELCOME);
            swingNode.setContent(cards);
        });
    }

    /**
     * @return 终端区域节点
     */
    public BorderPane getView() {
        return root;
    }

    /**
     * 为连接打开终端标签。
     *
     * @param connection 连接
     * @param config     SSH 连接配置
     */
    public void openTerminal(Connection connection, SshConnectionConfig config) {
        String cardId = "term-" + (counter++);
        String name = displayName(connection);

        Entry entry = new Entry(cardId, name, connection, config);
        entry.label = new Label(tabText(DOT_CONNECTING, name));
        Button closeButton = new Button("✕");
        closeButton.setStyle("-fx-padding: 0 4 0 4; -fx-background-color: transparent;");
        closeButton.setOnAction(e -> closeCard(cardId));
        entry.header = new HBox(4, entry.label, closeButton);
        entry.header.setAlignment(Pos.CENTER_LEFT);
        entry.header.setStyle(STYLE_TAB);
        entry.header.setOnMouseClicked(e -> selectCard(cardId));

        entries.put(cardId, entry);
        tabBar.getChildren().add(entry.header);
        selectCard(cardId);
        connectIntoCard(cardId);
    }

    private void selectCard(String cardId) {
        selectedCardId = cardId;
        for (Entry e : entries.values()) {
            e.header.setStyle(e.cardId.equals(cardId) ? STYLE_TAB_SELECTED : STYLE_TAB);
        }
        Entry entry = entries.get(cardId);
        Platform.runLater(swingNode::requestFocus);
        SwingUtilities.invokeLater(() -> {
            ((CardLayout) cards.getLayout()).show(cards, cardId);
            cards.revalidate();
            cards.repaint();
            if (entry != null && entry.widget != null) {
                entry.widget.getTerminalPanel().requestFocusInWindow();
            }
        });
    }

    private void connectIntoCard(String cardId) {
        Entry entry = entries.get(cardId);
        if (entry == null) {
            return;
        }
        entry.label.setText(tabText(DOT_CONNECTING, entry.name));
        releaseSession(entry);

        Thread worker = new Thread(() -> {
            try {
                SshSession session = sshService.connect(entry.config);
                SshShell shell = session.openShell(COLUMNS, ROWS);
                SshTtyConnector connector = new SshTtyConnector(shell, entry.name,
                        () -> Platform.runLater(() -> connectIntoCard(cardId)));
                SwingUtilities.invokeLater(() -> {
                    JediTermWidget widget = new JediTermWidget(COLUMNS, ROWS, new DefaultSettingsProvider());
                    widget.setTtyConnector(connector);
                    widget.start();
                    cards.add(widget, cardId);
                    entry.widget = widget;
                    entry.connector = connector;
                    entry.session = session;
                    if (cardId.equals(selectedCardId)) {
                        ((CardLayout) cards.getLayout()).show(cards, cardId);
                        widget.getTerminalPanel().requestFocusInWindow();
                    }
                    cards.revalidate();
                    cards.repaint();
                });
                Platform.runLater(() -> {
                    entry.label.setText(tabText(DOT_CONNECTED, entry.name));
                    if (cardId.equals(selectedCardId)) {
                        swingNode.requestFocus();
                    }
                });
                watchClose(cardId, shell);
            } catch (RuntimeException ex) {
                log.warn("Terminal connect failed for {}: {}", entry.name, ex.getMessage());
                Platform.runLater(() -> {
                    entry.label.setText(tabText(DOT_DISCONNECTED, entry.name));
                    UiDialogs.error(I18n.t("msg.connect.fail",
                            entry.config.getHost() + ":" + entry.config.getPort()));
                });
            }
        }, "ssh-terminal-" + entry.name);
        worker.setDaemon(true);
        worker.start();
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
        // 选中其他标签，否则回到欢迎卡
        String next = entries.keySet().stream().findFirst().orElse(WELCOME);
        if (WELCOME.equals(next)) {
            selectedCardId = null;
            SwingUtilities.invokeLater(() -> {
                ((CardLayout) cards.getLayout()).show(cards, WELCOME);
                cards.revalidate();
                cards.repaint();
            });
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
            SwingUtilities.invokeLater(() -> {
                cards.remove(oldWidget);
                oldWidget.close();
            });
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
        private final Connection connection;
        private final SshConnectionConfig config;
        private Label label;
        private HBox header;
        private volatile JediTermWidget widget;
        private volatile SshTtyConnector connector;
        private volatile SshSession session;

        Entry(String cardId, String name, Connection connection, SshConnectionConfig config) {
            this.cardId = cardId;
            this.name = name;
            this.connection = connection;
            this.config = config;
        }
    }
}
