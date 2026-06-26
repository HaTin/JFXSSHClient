package com.xxx.jfxssh.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;

import java.awt.Font;

/**
 * 跟随应用主题（深色 / 浅色）并使用配置字体的 JediTerm 配置。
 *
 * <p>覆盖默认前景 / 背景色与终端字体。配置在创建时固定：每个终端按创建时的
 * 主题与字体取值，此后修改设置只影响新建终端（见 docs/UI_DESIGN.md）。</p>
 */
public final class ThemedTerminalSettings extends DefaultSettingsProvider {

    private static final TerminalColor DARK_FG = new TerminalColor(0xD0, 0xD0, 0xD0);
    private static final TerminalColor DARK_BG = new TerminalColor(0x1E, 0x1E, 0x1E);
    private static final TerminalColor LIGHT_FG = new TerminalColor(0x1F, 0x1F, 0x1F);
    private static final TerminalColor LIGHT_BG = new TerminalColor(0xFF, 0xFF, 0xFF);

    private final boolean dark;
    private final TerminalFontModel font;

    /**
     * @param dark 是否深色（创建后固定）
     * @param font 共享字体模型（运行时可变，刷新后对已有终端生效）
     */
    public ThemedTerminalSettings(boolean dark, TerminalFontModel font) {
        this.dark = dark;
        this.font = font;
    }

    @Override
    public TextStyle getDefaultStyle() {
        return dark ? new TextStyle(DARK_FG, DARK_BG) : new TextStyle(LIGHT_FG, LIGHT_BG);
    }

    @Override
    public Font getTerminalFont() {
        return new Font(font.name(), Font.PLAIN, font.size());
    }

    @Override
    public float getTerminalFontSize() {
        return font.size();
    }

    @Override
    public int getBufferMaxLinesCount() {
        // 回滚历史行数。堆外内存主要来自 NIO/渲染（已用 JVM 参数解决），
        // 这里给足历史；每行占用很小，10000 行内存代价可忽略。
        return 10000;
    }

    @Override
    public int maxRefreshRate() {
        // 降低重绘频率（默认 50fps）：减少 SwingNode 重绘 + Java2D 原生纹理上传，
        // 缓解长文本/高吞吐输出时的堆外内存增长
        return 30;
    }
}
