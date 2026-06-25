package com.xxx.jfxssh.launcher;

import javafx.application.Application;

/**
 * 程序主入口。
 *
 * <p>独立于 {@link javafx.application.Application} 的入口类，规避以 classpath
 * 方式启动时出现的 "JavaFX runtime components are missing" 问题。</p>
 */
public final class Main {

    private Main() {
    }

    /**
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Application.launch(JfxSshApplication.class, args);
    }
}
