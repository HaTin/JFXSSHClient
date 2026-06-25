package com.xxx.jfxssh.ui.main;

import com.xxx.jfxssh.common.Constants;
import com.xxx.jfxssh.common.config.AppConfig;
import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.service.ConnectionService;
import com.xxx.jfxssh.service.CredentialVault;
import com.xxx.jfxssh.service.GroupService;
import com.xxx.jfxssh.ssh.SshService;
import com.xxx.jfxssh.ui.dialog.SettingsDialog;
import com.xxx.jfxssh.ui.status.StatusBar;
import com.xxx.jfxssh.ui.terminal.TerminalTabsPane;
import com.xxx.jfxssh.ui.theme.ThemeManager;
import com.xxx.jfxssh.ui.tree.ConnectionTreeView;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;

/**
 * 主窗口框架（见 docs/UI_DESIGN.md）。
 *
 * <p>BorderPane 布局组装四大区域：顶部 MenuBar、左侧 {@link ConnectionTreeView}、
 * 中间 {@link TerminalTabsPane}、底部 {@link StatusBar}。连接树接入后端服务，
 * 支持连接 / 分组的增删改查与凭据加密；语言 / 主题 / 终端 / SSH 设置在设置窗口
 * （File → Settings）。所有可见文案经 {@link I18n} 绑定。</p>
 */
public final class MainWindow {

    private final AppConfig config;
    private final TerminalTabsPane terminalTabs;
    private final ConnectionTreeView connectionTree;
    private final BorderPane root = new BorderPane();

    private MenuItem lightThemeItem;
    private MenuItem darkThemeItem;
    private MenuItem settingsItem;
    private ThemeManager themeManager;

    /**
     * 构建主窗口界面骨架。
     *
     * @param config            应用配置
     * @param connectionService 连接服务
     * @param groupService      分组服务
     * @param sshService        SSH 服务
     * @param vault             凭据保险库
     */
    public MainWindow(AppConfig config,
                      ConnectionService connectionService,
                      GroupService groupService,
                      SshService sshService,
                      CredentialVault vault) {
        this.config = config;
        this.terminalTabs = new TerminalTabsPane(sshService, config);
        this.connectionTree = new ConnectionTreeView(
                connectionService, groupService, terminalTabs::openTerminal, vault, config);
        root.setTop(buildMenuBar());
        root.setCenter(buildCenter());
        root.setBottom(new StatusBar().getView());
    }

    /**
     * @return 主窗口根节点
     */
    public Parent getRoot() {
        return root;
    }

    /**
     * 将主题菜单项与主题管理器绑定，并接入设置窗口（构造场景后调用）。
     *
     * @param themeManager 主题管理器
     */
    public void bindThemeMenu(ThemeManager themeManager) {
        this.themeManager = themeManager;
        lightThemeItem.setOnAction(e -> themeManager.applyLight());
        darkThemeItem.setOnAction(e -> themeManager.applyDark());
        settingsItem.setOnAction(e -> {
            new SettingsDialog(config, themeManager).showAndWait();
            // 字体 / 字号立即应用到所有已打开终端
            terminalTabs.applyFont(
                    config.get(AppConfig.KEY_TERMINAL_FONT, AppConfig.DEFAULT_TERMINAL_FONT),
                    config.getInt(AppConfig.KEY_TERMINAL_FONT_SIZE, AppConfig.DEFAULT_TERMINAL_FONT_SIZE));
        });

        // 终端配色跟随应用主题
        terminalTabs.applyDarkTheme(themeManager.isDark());
        themeManager.darkProperty().addListener((obs, was, dark) -> terminalTabs.applyDarkTheme(dark));
    }

    private SplitPane buildCenter() {
        SplitPane split = new SplitPane(
                connectionTree.getView(),
                terminalTabs.getView());
        split.setDividerPositions(
                Constants.CONNECTION_TREE_WIDTH / Constants.DEFAULT_WINDOW_WIDTH);
        return split;
    }

    private MenuBar buildMenuBar() {
        MenuItem newConnection = item("menu.file.new_connection");
        newConnection.setOnAction(e -> connectionTree.newConnection());
        settingsItem = item("menu.file.settings");

        Menu file = menu("menu.file");
        file.getItems().addAll(
                newConnection,
                item("menu.file.import"),
                item("menu.file.export"),
                settingsItem,
                exitItem());

        Menu connection = menu("menu.connection");
        connection.getItems().addAll(
                item("menu.connection.connect"),
                item("menu.connection.disconnect"),
                item("menu.connection.reconnect"),
                item("menu.connection.close_session"));

        // 未上线功能：V1 置灰（见 UI_DESIGN.md V1 菜单可见性）
        Menu tools = menu("menu.tools");
        tools.getItems().addAll(
                disabled(item("menu.tools.sftp")),
                disabled(item("menu.tools.port_forward")),
                disabled(item("menu.tools.plugin_manager")));

        lightThemeItem = item("menu.view.light_theme");
        darkThemeItem = item("menu.view.dark_theme");
        Menu view = menu("menu.view");
        // 语言已归位到设置窗口（File → Settings → General）
        view.getItems().addAll(lightThemeItem, darkThemeItem, item("menu.view.reset_layout"));

        Menu help = menu("menu.help");
        help.getItems().addAll(item("menu.help.about"), item("menu.help.documentation"));

        return new MenuBar(file, connection, tools, buildKeyboardMenu(), view, help);
    }

    /** 键盘菜单：点击向当前活动终端发送对应控制序列。 */
    private Menu buildKeyboardMenu() {
        Menu control = menu("menu.keyboard.control");
        control.getItems().addAll(
                keyItem("Ctrl+C", 3), keyItem("Ctrl+D", 4), keyItem("Ctrl+Z", 26),
                keyItem("Ctrl+L", 12), keyItem("Ctrl+A", 1), keyItem("Ctrl+E", 5),
                keyItem("Ctrl+U", 21), keyItem("Ctrl+K", 11), keyItem("Ctrl+W", 23),
                keyItem("Ctrl+R", 18), keyItem("Ctrl+G", 7), keyItem("Ctrl+\\", 28));

        Menu special = menu("menu.keyboard.special");
        special.getItems().addAll(
                keyItem("Tab", 9), keyItem("Esc", 27), keyItem("Enter", 13), keyItem("Backspace", 127),
                keyItem("Delete", 27, 91, 51, 126), keyItem("Insert", 27, 91, 50, 126),
                keyItem("Home", 27, 91, 72), keyItem("End", 27, 91, 70),
                keyItem("PageUp", 27, 91, 53, 126), keyItem("PageDown", 27, 91, 54, 126));

        Menu arrows = menu("menu.keyboard.arrows");
        arrows.getItems().addAll(
                keyItem("Up", 27, 91, 65), keyItem("Down", 27, 91, 66),
                keyItem("Right", 27, 91, 67), keyItem("Left", 27, 91, 68));

        Menu function = menu("menu.keyboard.function");
        function.getItems().addAll(
                keyItem("F1", 27, 79, 80), keyItem("F2", 27, 79, 81),
                keyItem("F3", 27, 79, 82), keyItem("F4", 27, 79, 83),
                keyItem("F5", 27, 91, 49, 53, 126), keyItem("F6", 27, 91, 49, 55, 126),
                keyItem("F7", 27, 91, 49, 56, 126), keyItem("F8", 27, 91, 49, 57, 126),
                keyItem("F9", 27, 91, 50, 48, 126), keyItem("F10", 27, 91, 50, 49, 126),
                keyItem("F11", 27, 91, 50, 51, 126), keyItem("F12", 27, 91, 50, 52, 126));

        Menu keyboard = menu("menu.keyboard");
        keyboard.getItems().addAll(control, special, arrows, function);
        return keyboard;
    }

    private MenuItem keyItem(String label, int... codes) {
        byte[] data = new byte[codes.length];
        for (int i = 0; i < codes.length; i++) {
            data[i] = (byte) codes[i];
        }
        MenuItem mi = new MenuItem(label);
        mi.setOnAction(e -> terminalTabs.sendToActiveTerminal(data));
        return mi;
    }

    private MenuItem exitItem() {
        MenuItem exit = item("menu.file.exit");
        exit.setOnAction(e -> Platform.exit());
        return exit;
    }

    private Menu menu(String key) {
        Menu m = new Menu();
        m.textProperty().bind(I18n.tp(key));
        return m;
    }

    private MenuItem item(String key) {
        MenuItem mi = new MenuItem();
        mi.textProperty().bind(I18n.tp(key));
        return mi;
    }

    private MenuItem disabled(MenuItem item) {
        item.setDisable(true);
        return item;
    }
}
