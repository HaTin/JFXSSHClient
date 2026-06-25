package com.xxx.jfxssh.ui.main;

import com.xxx.jfxssh.common.Constants;
import com.xxx.jfxssh.common.config.AppConfig;
import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.service.ConnectionService;
import com.xxx.jfxssh.service.CredentialVault;
import com.xxx.jfxssh.service.GroupService;
import com.xxx.jfxssh.ssh.SshService;
import com.xxx.jfxssh.ui.status.StatusBar;
import com.xxx.jfxssh.ui.terminal.TerminalTabsPane;
import com.xxx.jfxssh.ui.theme.ThemeManager;
import com.xxx.jfxssh.ui.tree.ConnectionTreeView;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;

import java.util.Locale;

/**
 * 主窗口框架（见 docs/UI_DESIGN.md）。
 *
 * <p>BorderPane 布局组装四大区域：顶部 MenuBar、左侧 {@link ConnectionTreeView}、
 * 中间 {@link TerminalTabsPane}、底部 {@link StatusBar}。连接树接入后端服务，
 * 支持连接 / 分组的增删改查与凭据加密；所有可见文案经 {@link I18n} 绑定。</p>
 */
public final class MainWindow {

    private final AppConfig config;
    private final TerminalTabsPane terminalTabs;
    private final ConnectionTreeView connectionTree;
    private final BorderPane root = new BorderPane();

    private MenuItem lightThemeItem;
    private MenuItem darkThemeItem;

    /**
     * 构建主窗口界面骨架。
     *
     * @param config            应用配置（用于持久化语言选择）
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
        this.terminalTabs = new TerminalTabsPane(sshService);
        this.connectionTree = new ConnectionTreeView(
                connectionService, groupService, terminalTabs::openTerminal, vault);
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
     * 将主题菜单项与主题管理器绑定（构造场景后调用）。
     *
     * @param themeManager 主题管理器
     */
    public void bindThemeMenu(ThemeManager themeManager) {
        lightThemeItem.setOnAction(e -> themeManager.applyLight());
        darkThemeItem.setOnAction(e -> themeManager.applyDark());

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

        Menu file = menu("menu.file");
        file.getItems().addAll(
                newConnection,
                item("menu.file.import"),
                item("menu.file.export"),
                item("menu.file.settings"),
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
        view.getItems().addAll(lightThemeItem, darkThemeItem, buildLanguageMenu(), item("menu.view.reset_layout"));

        Menu help = menu("menu.help");
        help.getItems().addAll(item("menu.help.about"), item("menu.help.documentation"));

        return new MenuBar(file, connection, tools, view, help);
    }

    private Menu buildLanguageMenu() {
        Menu language = menu("menu.view.language");
        ToggleGroup group = new ToggleGroup();
        language.getItems().addAll(
                languageItem("lang.en", I18n.EN, group),
                languageItem("lang.zh_CN", I18n.ZH_CN, group));
        return language;
    }

    private RadioMenuItem languageItem(String key, Locale locale, ToggleGroup group) {
        RadioMenuItem mi = new RadioMenuItem();
        mi.textProperty().bind(I18n.tp(key));
        mi.setToggleGroup(group);
        mi.setSelected(I18n.code(locale).equals(I18n.code(I18n.currentLocale())));
        mi.setOnAction(e -> switchLanguage(locale));
        return mi;
    }

    private void switchLanguage(Locale locale) {
        I18n.setLocale(locale);
        config.set(AppConfig.KEY_LANGUAGE, I18n.code(locale));
        config.save();
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
