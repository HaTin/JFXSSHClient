package com.xxx.jfxssh.terminal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AWT 事件队列守卫：吞掉 JediTerm 一个已知的越界异常，避免刷栈。
 *
 * <p>JediTerm 3.72 在鼠标移动时做超链接探测（{@code TerminalPanel.handleHyperlinks /
 * findHyperlink}），当鼠标所在行号超过当前缓冲实际行数时会抛
 * {@link IndexOutOfBoundsException}（例如刚连上内容很少、或界面拆除时）。它只发生在
 * AWT 线程、不影响功能。这里仅拦截这一特定异常并记 debug，其它异常照常上抛。</p>
 */
public final class AwtEventGuard {

    private static final Logger log = LoggerFactory.getLogger(AwtEventGuard.class);
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private AwtEventGuard() {
    }

    /**
     * 安装守卫（须在 AWT EDT 上调用，幂等）。
     */
    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
            @Override
            protected void dispatchEvent(AWTEvent event) {
                try {
                    super.dispatchEvent(event);
                } catch (IndexOutOfBoundsException ex) {
                    if (isJediTermHyperlinkGlitch(ex)) {
                        log.debug("Ignored JediTerm hyperlink bounds glitch", ex);
                    } else {
                        throw ex;
                    }
                }
            }
        });
    }

    private static boolean isJediTermHyperlinkGlitch(Throwable t) {
        for (StackTraceElement frame : t.getStackTrace()) {
            String cls = frame.getClassName();
            String method = frame.getMethodName();
            if (cls.startsWith("com.jediterm")
                    && (method.contains("Hyperlink") || method.contains("hyperlink"))) {
                return true;
            }
        }
        return false;
    }
}
