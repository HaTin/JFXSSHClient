package com.xxx.jfxssh.terminal;

import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TerminalPanel;
import com.jediterm.terminal.ui.settings.SettingsProvider;

/**
 * JediTerm 控件子类，暴露运行时刷新字体的能力。
 *
 * <p>通过自定义的 {@link RefreshableTerminalPanel} 调用受保护的
 * {@code reinitFontAndResize()}，使修改字体 / 字号后能立即重排已打开的终端
 * （并按新字号重新计算行列、通知服务端窗口尺寸变化）。</p>
 */
public final class JfxTermWidget extends JediTermWidget {

    /**
     * @param columns  初始列
     * @param rows     初始行
     * @param settings 配置
     */
    public JfxTermWidget(int columns, int rows, SettingsProvider settings) {
        super(columns, rows, settings);
    }

    @Override
    protected TerminalPanel createTerminalPanel(SettingsProvider settings,
                                                StyleState styleState,
                                                TerminalTextBuffer buffer) {
        return new RefreshableTerminalPanel(settings, buffer, styleState);
    }

    /**
     * 重新读取字体并重排（在 Swing EDT 调用）。
     */
    public void refreshFont() {
        if (getTerminalPanel() instanceof RefreshableTerminalPanel panel) {
            panel.refreshFont();
        }
    }

    /** 暴露受保护的字体重排方法。 */
    private static final class RefreshableTerminalPanel extends TerminalPanel {

        RefreshableTerminalPanel(SettingsProvider settings, TerminalTextBuffer buffer, StyleState styleState) {
            super(settings, buffer, styleState);
        }

        void refreshFont() {
            reinitFontAndResize();
        }
    }
}
