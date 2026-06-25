package com.xxx.jfxssh.launcher;

import com.xxx.jfxssh.common.AppPaths;
import com.xxx.jfxssh.common.Constants;
import com.xxx.jfxssh.common.config.AppConfig;
import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.service.ConnectionService;
import com.xxx.jfxssh.service.ConnectionServiceImpl;
import com.xxx.jfxssh.service.CredentialVault;
import com.xxx.jfxssh.service.GroupService;
import com.xxx.jfxssh.service.GroupServiceImpl;
import com.xxx.jfxssh.service.SettingsHostKeyStore;
import com.xxx.jfxssh.service.SettingsService;
import com.xxx.jfxssh.service.SettingsServiceImpl;
import com.xxx.jfxssh.ssh.KnownHostsVerifier;
import com.xxx.jfxssh.ssh.MinaSshService;
import com.xxx.jfxssh.storage.Database;
import com.xxx.jfxssh.storage.repository.ConnectionRepositoryImpl;
import com.xxx.jfxssh.storage.repository.GroupRepositoryImpl;
import com.xxx.jfxssh.storage.repository.SettingsRepositoryImpl;
import com.xxx.jfxssh.ui.dialog.FxHostKeyPrompt;
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
 * <p>装配基础设施（路径、配置、国际化、数据库）与业务服务（连接 / 分组 / SSH），
 * 注入主窗口并展示。退出时停止 SSH 客户端。</p>
 */
public final class JfxSshApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(JfxSshApplication.class);

    private MinaSshService sshService;

    @Override
    public void start(Stage stage) {
        log.info("Starting {} ...", Constants.APP_NAME);

        AppPaths paths = new AppPaths();

        AppConfig config = new AppConfig(paths);
        config.load();

        I18n.init(resolveLocale(config));

        Database database = new Database(paths);
        database.init();

        ConnectionService connectionService =
                new ConnectionServiceImpl(new ConnectionRepositoryImpl(database));
        GroupService groupService =
                new GroupServiceImpl(new GroupRepositoryImpl(database));
        SettingsService settingsService =
                new SettingsServiceImpl(new SettingsRepositoryImpl(database));
        CredentialVault vault = new CredentialVault(settingsService);
        KnownHostsVerifier hostKeyVerifier = new KnownHostsVerifier(
                new SettingsHostKeyStore(settingsService),
                new FxHostKeyPrompt(),
                () -> config.getBoolean(AppConfig.KEY_SSH_HOSTKEY_VERIFY, AppConfig.DEFAULT_SSH_HOSTKEY_VERIFY));
        sshService = new MinaSshService(hostKeyVerifier);

        MainWindow mainWindow = new MainWindow(config, connectionService, groupService, sshService, vault);
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

    @Override
    public void stop() {
        if (sshService != null) {
            sshService.close();
        }
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
