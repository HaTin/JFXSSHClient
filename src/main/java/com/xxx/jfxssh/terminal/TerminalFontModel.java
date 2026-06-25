package com.xxx.jfxssh.terminal;

/**
 * 共享、可变的终端字体模型。
 *
 * <p>所有终端的配置读取同一个模型，修改字体 / 字号后调用各终端的刷新即可
 * 立即对所有已打开终端生效（区别于主题的"仅新终端"策略）。</p>
 */
public final class TerminalFontModel {

    private volatile String name;
    private volatile int size;

    /**
     * @param name 字体族
     * @param size 字号
     */
    public TerminalFontModel(String name, int size) {
        this.name = name;
        this.size = size;
    }

    /** @return 字体族 */
    public String name() {
        return name;
    }

    /** @return 字号 */
    public int size() {
        return size;
    }

    /**
     * @param name 字体族
     * @param size 字号
     */
    public void set(String name, int size) {
        this.name = name;
        this.size = size;
    }
}
