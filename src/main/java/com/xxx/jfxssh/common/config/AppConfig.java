package com.xxx.jfxssh.common.config;

import com.xxx.jfxssh.common.AppPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;

/**
 * 应用配置管理。
 *
 * <p>基础设施层组件，负责读写本地配置文件（{@code config.properties}）。
 * 不包含任何业务逻辑；连接、SSH 等数据由后续 Service/Repository 层管理。</p>
 *
 * <p>本阶段仅承载应用级偏好（如主题），用于在 Service 层就绪前驱动 UI。</p>
 */
public final class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    /** 主题配置项。 */
    public static final String KEY_THEME = "theme";

    /** 主题默认值。 */
    public static final String DEFAULT_THEME = "dark";

    /** 语言配置项（值为语言代码，如 en / zh_CN）。 */
    public static final String KEY_LANGUAGE = "language";

    private final AppPaths paths;
    private final Properties properties = new Properties();

    /**
     * @param paths 应用路径解析器
     */
    public AppConfig(AppPaths paths) {
        this.paths = paths;
    }

    /**
     * 从磁盘加载配置；文件不存在时使用默认值。
     */
    public void load() {
        if (!Files.exists(paths.configFile())) {
            log.info("Config file not found, using defaults: {}", paths.configFile());
            return;
        }
        try (InputStream in = Files.newInputStream(paths.configFile())) {
            properties.load(in);
            log.info("Config loaded: {}", paths.configFile());
        } catch (IOException e) {
            log.warn("Failed to load config, using defaults", e);
        }
    }

    /**
     * 将配置写入磁盘。
     */
    public void save() {
        try {
            Files.createDirectories(paths.appDir());
            try (OutputStream out = Files.newOutputStream(paths.configFile())) {
                properties.store(out, "JFX SSH Client config");
            }
            log.debug("Config saved: {}", paths.configFile());
        } catch (IOException e) {
            log.warn("Failed to save config", e);
        }
    }

    /**
     * 读取配置项。
     *
     * @param key          键
     * @param defaultValue 缺省值
     * @return 配置值或缺省值
     */
    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * 写入配置项（仅内存，需调用 {@link #save()} 持久化）。
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, String value) {
        properties.setProperty(key, value);
    }
}
