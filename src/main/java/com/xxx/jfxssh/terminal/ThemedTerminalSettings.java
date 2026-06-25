package com.xxx.jfxssh.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;

/**
 * 跟随应用主题（深色 / 浅色）的 JediTerm 配置。
 *
 * <p>覆盖默认前景 / 背景色。配色在创建时固定：每个终端按创建时的主题取色，
 * 此后切换主题不影响已存在的终端，只有新建终端使用新配色（见 docs/UI_DESIGN.md）。</p>
 */
public final class ThemedTerminalSettings extends DefaultSettingsProvider {

    private static final TerminalColor DARK_FG = new TerminalColor(0xD0, 0xD0, 0xD0);
    private static final TerminalColor DARK_BG = new TerminalColor(0x1E, 0x1E, 0x1E);
    private static final TerminalColor LIGHT_FG = new TerminalColor(0x1F, 0x1F, 0x1F);
    private static final TerminalColor LIGHT_BG = new TerminalColor(0xFF, 0xFF, 0xFF);

    private final boolean dark;

    /**
     * @param dark 是否深色（创建后固定）
     */
    public ThemedTerminalSettings(boolean dark) {
        this.dark = dark;
    }

    @Override
    public TextStyle getDefaultStyle() {
        return dark ? new TextStyle(DARK_FG, DARK_BG) : new TextStyle(LIGHT_FG, LIGHT_BG);
    }
}
