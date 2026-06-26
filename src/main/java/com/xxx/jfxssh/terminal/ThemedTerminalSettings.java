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
        // 限制回滚缓冲行数，避免大量输出占用过多内存
        return 2000;
    }
}
