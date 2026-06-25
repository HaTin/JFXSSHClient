package com.xxx.jfxssh.ui.dialog;

import com.xxx.jfxssh.common.config.AppConfig;
import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ui.theme.ThemeManager;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.util.Locale;

/**
 * 设置窗口（见 docs/UI_DESIGN.md）：TabPane 三页 General / Terminal / SSH。
 *
 * <p>语言、主题为实时生效；终端字体、SSH 选项保存后对新建终端 / 新连接生效。
 * 光标样式、主机密钥校验为预留项（当前置灰）。</p>
 */
public final class SettingsDialog {

    private final AppConfig config;
    private final ThemeManager themeManager;
    private final Dialog<ButtonType> dialog = new Dialog<>();

    // General
    private final ComboBox<Locale> languageBox = new ComboBox<>();
    private final ComboBox<String> themeBox = new ComboBox<>();
    private final CheckBox autoSaveBox = new CheckBox();

    // Terminal
    private final ComboBox<String> fontBox = new ComboBox<>();
    private final Spinner<Integer> fontSizeSpinner = new Spinner<>(10, 24, 14);
    private final ComboBox<String> cursorBox = new ComboBox<>();

    // SSH
    private final Spinner<Integer> keepAliveSpinner = new Spinner<>(0, 600, 30);
    private final Spinner<Integer> timeoutSpinner = new Spinner<>(1, 120, 10);
    private final CheckBox hostKeyVerifyBox = new CheckBox();

    /**
     * @param config       应用配置
     * @param themeManager 主题管理器（用于实时切换主题）
     */
    public SettingsDialog(AppConfig config, ThemeManager themeManager) {
        this.config = config;
        this.themeManager = themeManager;

        dialog.setTitle(I18n.t("menu.file.settings"));
        ButtonType ok = new ButtonType(I18n.t("button.ok"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(I18n.t("button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, cancel);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(generalTab(), terminalTab(), sshTab());
        dialog.getDialogPane().setContent(tabs);

        load();
        dialog.setResultConverter(bt -> {
            if (bt == ok) {
                apply();
            }
            return bt;
        });
    }

    /**
     * 显示设置窗口。
     */
    public void showAndWait() {
        dialog.showAndWait();
    }

    private Tab generalTab() {
        languageBox.getItems().setAll(I18n.EN, I18n.ZH_CN);
        languageBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Locale locale) {
                return I18n.ZH_CN.equals(locale) ? I18n.t("lang.zh_CN") : I18n.t("lang.en");
            }

            @Override
            public Locale fromString(String s) {
                return I18n.EN;
            }
        });
        themeBox.getItems().setAll(ThemeManager.THEME_LIGHT, ThemeManager.THEME_DARK);
        themeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return ThemeManager.THEME_DARK.equals(value)
                        ? I18n.t("menu.view.dark_theme") : I18n.t("menu.view.light_theme");
            }

            @Override
            public String fromString(String s) {
                return ThemeManager.THEME_DARK;
            }
        });
        autoSaveBox.setText(I18n.t("settings.general.auto_save"));

        GridPane grid = grid();
        grid.addRow(0, new Label(I18n.t("settings.general.language")), languageBox);
        grid.addRow(1, new Label(I18n.t("settings.general.theme")), themeBox);
        grid.add(autoSaveBox, 1, 2);
        return new Tab(I18n.t("settings.tab.general"), grid);
    }

    private Tab terminalTab() {
        fontBox.getItems().setAll("Monospaced", "DejaVu Sans Mono", "JetBrains Mono",
                "Consolas", "SF Mono", "Courier New");
        cursorBox.getItems().setAll("STEADY_BLOCK", "STEADY_UNDERLINE", "STEADY_VERTICAL_BAR");
        cursorBox.setDisable(true); // 预留：JediTerm 暂无光标样式设置钩子

        GridPane grid = grid();
        grid.addRow(0, new Label(I18n.t("settings.terminal.font")), fontBox);
        grid.addRow(1, new Label(I18n.t("settings.terminal.font_size")), fontSizeSpinner);
        grid.addRow(2, new Label(I18n.t("settings.terminal.cursor")), cursorBox);
        return new Tab(I18n.t("settings.tab.terminal"), grid);
    }

    private Tab sshTab() {
        hostKeyVerifyBox.setText(I18n.t("settings.ssh.host_key_verify"));
        hostKeyVerifyBox.setDisable(true); // 预留：known_hosts 校验未实现

        GridPane grid = grid();
        grid.addRow(0, new Label(I18n.t("settings.ssh.keepalive")), keepAliveSpinner);
        grid.addRow(1, new Label(I18n.t("settings.ssh.timeout")), timeoutSpinner);
        grid.add(hostKeyVerifyBox, 1, 2);
        return new Tab(I18n.t("settings.tab.ssh"), grid);
    }

    private GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        return grid;
    }

    private void load() {
        languageBox.setValue(I18n.currentLocale());
        themeBox.setValue(themeManager.isDark() ? ThemeManager.THEME_DARK : ThemeManager.THEME_LIGHT);
        autoSaveBox.setSelected(config.getBoolean(AppConfig.KEY_AUTOSAVE, true));

        fontBox.setValue(config.get(AppConfig.KEY_TERMINAL_FONT, AppConfig.DEFAULT_TERMINAL_FONT));
        fontSizeSpinner.getValueFactory().setValue(
                config.getInt(AppConfig.KEY_TERMINAL_FONT_SIZE, AppConfig.DEFAULT_TERMINAL_FONT_SIZE));
        cursorBox.setValue(config.get(AppConfig.KEY_TERMINAL_CURSOR, "STEADY_BLOCK"));

        keepAliveSpinner.getValueFactory().setValue(
                config.getInt(AppConfig.KEY_SSH_KEEPALIVE, AppConfig.DEFAULT_SSH_KEEPALIVE));
        timeoutSpinner.getValueFactory().setValue(
                config.getInt(AppConfig.KEY_SSH_TIMEOUT, AppConfig.DEFAULT_SSH_TIMEOUT));
        hostKeyVerifyBox.setSelected(config.getBoolean(AppConfig.KEY_SSH_HOSTKEY_VERIFY, false));
    }

    private void apply() {
        // General：语言 / 主题实时生效
        Locale locale = languageBox.getValue();
        if (locale != null && !I18n.code(locale).equals(I18n.code(I18n.currentLocale()))) {
            I18n.setLocale(locale);
            config.set(AppConfig.KEY_LANGUAGE, I18n.code(locale));
        }
        if (ThemeManager.THEME_LIGHT.equals(themeBox.getValue())) {
            themeManager.applyLight();
        } else {
            themeManager.applyDark();
        }
        config.set(AppConfig.KEY_AUTOSAVE, Boolean.toString(autoSaveBox.isSelected()));

        // Terminal / SSH：保存，对新建终端 / 新连接生效
        config.set(AppConfig.KEY_TERMINAL_FONT, fontBox.getValue());
        config.set(AppConfig.KEY_TERMINAL_FONT_SIZE, Integer.toString(fontSizeSpinner.getValue()));
        config.set(AppConfig.KEY_TERMINAL_CURSOR, cursorBox.getValue());
        config.set(AppConfig.KEY_SSH_KEEPALIVE, Integer.toString(keepAliveSpinner.getValue()));
        config.set(AppConfig.KEY_SSH_TIMEOUT, Integer.toString(timeoutSpinner.getValue()));
        config.set(AppConfig.KEY_SSH_HOSTKEY_VERIFY, Boolean.toString(hostKeyVerifyBox.isSelected()));
        config.save();
    }
}
