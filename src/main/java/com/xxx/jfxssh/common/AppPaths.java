package com.xxx.jfxssh.common;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 应用文件路径解析。
 *
 * <p>统一约定应用数据目录，避免硬编码路径（见 docs/CODING_STANDARDS.md）。
 * 数据目录为用户主目录下的 {@code .jfxssh}。</p>
 */
public final class AppPaths {

    private static final String APP_DIR_NAME = ".jfxssh";
    private static final String DB_FILE_NAME = "jfxssh.db";
    private static final String CONFIG_FILE_NAME = "config.properties";

    private final Path appDir;

    /**
     * 使用用户主目录构造默认路径。
     */
    public AppPaths() {
        this(Paths.get(System.getProperty("user.home"), APP_DIR_NAME));
    }

    /**
     * 使用指定应用目录构造（便于测试）。
     *
     * @param appDir 应用数据目录
     */
    public AppPaths(Path appDir) {
        this.appDir = appDir;
    }

    /**
     * @return 应用数据目录
     */
    public Path appDir() {
        return appDir;
    }

    /**
     * @return SQLite 数据库文件路径
     */
    public Path databaseFile() {
        return appDir.resolve(DB_FILE_NAME);
    }

    /**
     * @return 配置文件路径
     */
    public Path configFile() {
        return appDir.resolve(CONFIG_FILE_NAME);
    }
}
