package com.xxx.jfxssh.ui.terminal;

import com.xxx.jfxssh.common.i18n.I18n;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;

/**
 * 终端标签页区域（见 docs/UI_DESIGN.md）。
 *
 * <p>主工作区，承载多标签页（一个 Tab 对应一个 SSH 会话）。本阶段仅实现
 * 界面布局：提供一个欢迎 Tab 占位，不创建会话、不嵌入终端模拟器。</p>
 */
public final class TerminalTabsPane {

    private final TabPane tabs = new TabPane();

    /**
     * 构建标签页区域。
     */
    public TerminalTabsPane() {
        tabs.setId("TerminalTabs");

        Label welcome = new Label();
        welcome.textProperty().bind(I18n.tp("tab.no_session"));

        Tab welcomeTab = new Tab();
        welcomeTab.textProperty().bind(I18n.tp("tab.welcome"));
        welcomeTab.setContent(new StackPane(welcome));
        welcomeTab.setClosable(false);
        tabs.getTabs().add(welcomeTab);
    }

    /**
     * @return 标签页节点
     */
    public TabPane getView() {
        return tabs;
    }
}
