package com.xxx.jfxssh.terminal;

import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.xxx.jfxssh.ssh.SshSession;
import javafx.embed.swing.SwingNode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;

/**
 * 终端视图：把 JediTerm 的 Swing 控件经 {@link SwingNode} 嵌入 JavaFX（见 ARCHITECTURE.md）。
 *
 * <p>JediTerm 控件须在 Swing EDT 上创建并启动；本视图负责线程切换与生命周期，
 * 持有底层 SSH 会话以便关闭时一并释放。</p>
 */
public final class TerminalView {

    private static final Logger log = LoggerFactory.getLogger(TerminalView.class);

    private static final int INITIAL_COLUMNS = 80;
    private static final int INITIAL_ROWS = 24;

    private final SwingNode swingNode = new SwingNode();
    private final StackPane root = new StackPane(swingNode);

    private JediTermWidget widget;
    private SshTtyConnector connector;
    private SshSession session;

    /**
     * @return 可加入 JavaFX 场景的节点
     */
    public Region getNode() {
        return root;
    }

    /**
     * 在 EDT 上创建 JediTerm 控件、绑定连接器并启动读取。
     *
     * @param connector TtyConnector（封装 SSH shell）
     * @param session   底层 SSH 会话（关闭时释放）
     */
    public void attach(SshTtyConnector connector, SshSession session) {
        this.connector = connector;
        this.session = session;
        SwingUtilities.invokeLater(() -> {
            widget = new JediTermWidget(INITIAL_COLUMNS, INITIAL_ROWS, new DefaultSettingsProvider());
            widget.setTtyConnector(connector);
            widget.start();
            swingNode.setContent(widget);
        });
    }

    /**
     * 关闭终端：断开连接器与会话，释放控件。
     */
    public void close() {
        if (connector != null) {
            connector.close();
        }
        if (session != null) {
            session.close();
        }
        if (widget != null) {
            SwingUtilities.invokeLater(() -> {
                try {
                    widget.close();
                } catch (RuntimeException e) {
                    log.warn("Error closing terminal widget", e);
                }
            });
        }
    }
}
