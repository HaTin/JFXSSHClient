package com.xxx.jfxssh.ui.theme;

import com.xxx.jfxssh.common.config.AppConfig;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 主题管理器。
 *
 * <p>支持 Light / Dark 两套主题，切换实时生效、无需重启（见 docs/UI_DESIGN.md）。
 * 通过替换 Scene 的样式表实现，并将选择持久化到 {@link AppConfig}。</p>
 */
public final class ThemeManager {

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);

    /** Light 主题标识。 */
    public static final String THEME_LIGHT = "light";

    /** Dark 主题标识。 */
    public static final String THEME_DARK = "dark";

    private static final String LIGHT_CSS = "/css/light.css";
    private static final String DARK_CSS = "/css/dark.css";

    private final Scene scene;
    private final AppConfig config;

    /**
     * @param scene  目标场景
     * @param config 应用配置（读取/保存当前主题）
     */
    public ThemeManager(Scene scene, AppConfig config) {
        this.scene = scene;
        this.config = config;
    }

    /**
     * 应用配置中记录的主题（缺省为 Dark）。
     */
    public void applyConfiguredTheme() {
        apply(config.get(AppConfig.KEY_THEME, AppConfig.DEFAULT_THEME));
    }

    /**
     * 切换到 Light 主题。
     */
    public void applyLight() {
        apply(THEME_LIGHT);
    }

    /**
     * 切换到 Dark 主题。
     */
    public void applyDark() {
        apply(THEME_DARK);
    }

    private void apply(String theme) {
        String resource = THEME_LIGHT.equalsIgnoreCase(theme) ? LIGHT_CSS : DARK_CSS;
        String normalized = THEME_LIGHT.equalsIgnoreCase(theme) ? THEME_LIGHT : THEME_DARK;

        java.net.URL url = ThemeManager.class.getResource(resource);
        if (url == null) {
            log.warn("Theme resource not found: {}", resource);
            return;
        }

        scene.getStylesheets().clear();
        scene.getStylesheets().add(url.toExternalForm());

        config.set(AppConfig.KEY_THEME, normalized);
        config.save();
        log.info("Theme applied: {}", normalized);
    }
}
