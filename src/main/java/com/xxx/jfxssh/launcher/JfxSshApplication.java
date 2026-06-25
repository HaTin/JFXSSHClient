package com.xxx.jfxssh.launcher;

import com.xxx.jfxssh.common.AppPaths;
import com.xxx.jfxssh.common.Constants;
import com.xxx.jfxssh.common.config.AppConfig;
import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.storage.Database;
import com.xxx.jfxssh.ui.main.MainWindow;
import com.xxx.jfxssh.ui.theme.ThemeManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * JavaFX 应用入口。
 *
 * <p>负责装配基础设施（路径、配置、国际化、数据库）并展示主窗口。
 * 不包含业务逻辑、SSH 或终端实现。</p>
 */
public final class JfxSshApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(JfxSshApplication.class);

    @Override
    public void start(Stage stage) {
        log.info("Starting {} ...", Constants.APP_NAME);

        AppPaths paths = new AppPaths();

        AppConfig config = new AppConfig(paths);
        config.load();

        I18n.init(resolveLocale(config));

        Database database = new Database(paths);
        database.init();

        MainWindow mainWindow = new MainWindow(config);
        Scene scene = new Scene(mainWindow.getRoot(),
                Constants.DEFAULT_WINDOW_WIDTH, Constants.DEFAULT_WINDOW_HEIGHT);

        ThemeManager themeManager = new ThemeManager(scene, config);
        themeManager.applyConfiguredTheme();
        mainWindow.bindThemeMenu(themeManager);

        stage.titleProperty().bind(I18n.tp("app.title"));
        stage.setScene(scene);
        stage.show();

        log.info("{} started.", Constants.APP_NAME);
    }

    private Locale resolveLocale(AppConfig config) {
        String saved = config.get(AppConfig.KEY_LANGUAGE, "");
        if (!saved.isBlank()) {
            return I18n.parse(saved);
        }
        // 无记录则跟随系统语言（中文→zh_CN，否则回退 en）
        return "zh".equals(Locale.getDefault().getLanguage()) ? I18n.ZH_CN : I18n.EN;
    }
}

