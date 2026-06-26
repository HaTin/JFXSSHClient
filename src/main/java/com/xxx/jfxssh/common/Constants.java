package com.xxx.jfxssh.common;

/**
 * 全局常量。
 *
 * <p>集中管理应用级常量，避免硬编码（见 docs/CODING_STANDARDS.md）。</p>
 */
public final class Constants {

    private Constants() {
    }

    /** 应用名称。 */
    public static final String APP_NAME = "JFX SSH Client";

    /** 应用版本。 */
    public static final String APP_VERSION = "1.0.0";

    /** SSH 默认端口。 */
    public static final int DEFAULT_PORT = 22;

    /** 主窗口默认宽度。 */
    public static final double DEFAULT_WINDOW_WIDTH = 1100;

    /** 主窗口默认高度。 */
    public static final double DEFAULT_WINDOW_HEIGHT = 720;

    /** 连接树默认宽度（见 UI_DESIGN.md）。 */
    public static final double CONNECTION_TREE_WIDTH = 250;

    /** 终端默认字体大小（见 UI_DESIGN.md）。 */
    public static final int DEFAULT_FONT_SIZE = 14;
}
