package com.xxx.jfxssh.ui.main;

import com.xxx.jfxssh.common.Constants;
import com.xxx.jfxssh.common.config.AppConfig;
import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ui.theme.ThemeManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.Locale;

/**
 * 主窗口框架。
 *
 * <p>按 docs/UI_DESIGN.md 搭建 BorderPane 布局：顶部 MenuBar、左侧连接树、
 * 中间终端 Tab 区、底部状态栏。所有可见文案经 {@link I18n} 绑定，支持运行时
 * 语言切换（见 docs/I18N.md）。本阶段仅为界面框架，不含业务逻辑、SSH 或终端。</p>
 */
public final class MainWindow {

    private final AppConfig config;
    private final BorderPane root = new BorderPane();

    private MenuItem lightThemeItem;
    private MenuItem darkThemeItem;

    /**
     * 构建主窗口界面骨架。
     *
     * @param config 应用配置（用于持久化语言选择）
     */
    public MainWindow(AppConfig config) {
        this.config = config;
        root.setTop(buildMenuBar());
        root.setCenter(buildCenter());
        root.setBottom(buildStatusBar());
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
    }

    private MenuBar buildMenuBar() {
        Menu file = menu("menu.file");
        file.getItems().addAll(
                item("menu.file.new_connection"),
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

        RadioMenuItem en = languageItem("lang.en", I18n.EN, group);
        RadioMenuItem zh = languageItem("lang.zh_CN", I18n.ZH_CN, group);

        language.getItems().addAll(en, zh);
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

    private SplitPane buildCenter() {
        SplitPane split = new SplitPane(buildConnectionTree(), buildTerminalTabs());
        split.setDividerPositions(
                Constants.CONNECTION_TREE_WIDTH / Constants.DEFAULT_WINDOW_WIDTH);
        return split;
    }

    private TreeView<String> buildConnectionTree() {
        TreeItem<String> rootItem = new TreeItem<>(I18n.t("tree.connections"));
        rootItem.setExpanded(true);

        TreeItem<String> production = new TreeItem<>(I18n.t("tree.group.production"));
        TreeItem<String> testing = new TreeItem<>(I18n.t("tree.group.testing"));
        TreeItem<String> local = new TreeItem<>(I18n.t("tree.group.local"));
        rootItem.getChildren().addAll(production, testing, local);

        // 树节点文本不是属性，无法直接绑定：监听语言变化后刷新值
        I18n.currentLocaleProperty().addListener((obs, oldLocale, newLocale) -> {
            rootItem.setValue(I18n.t("tree.connections"));
            production.setValue(I18n.t("tree.group.production"));
            testing.setValue(I18n.t("tree.group.testing"));
            local.setValue(I18n.t("tree.group.local"));
        });

        TreeView<String> tree = new TreeView<>(rootItem);
        tree.setId("ConnectionTree");
        tree.setMinWidth(150);
        tree.setPrefWidth(Constants.CONNECTION_TREE_WIDTH);
        return tree;
    }

    private TabPane buildTerminalTabs() {
        TabPane tabs = new TabPane();
        tabs.setId("TerminalTabs");

        Label welcome = new Label();
        welcome.textProperty().bind(I18n.tp("tab.no_session"));

        Tab welcomeTab = new Tab();
        welcomeTab.textProperty().bind(I18n.tp("tab.welcome"));
        welcomeTab.setContent(new StackPane(welcome));
        welcomeTab.setClosable(false);
        tabs.getTabs().add(welcomeTab);
        return tabs;
    }

    private HBox buildStatusBar() {
        Label left = new Label();
        left.textProperty().bind(I18n.tp("status.ready"));
        Label middle = new Label("UTF-8");
        Label right = new Label("SSH");

        Region spacerLeft = new Region();
        Region spacerRight = new Region();
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);
        HBox.setHgrow(spacerRight, Priority.ALWAYS);

        HBox bar = new HBox(left, spacerLeft, middle, spacerRight, right);
        bar.getStyleClass().add("status-bar");
        bar.setPrefHeight(24);
        bar.setPadding(new Insets(0, 12, 0, 12));
        bar.setSpacing(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }
}
