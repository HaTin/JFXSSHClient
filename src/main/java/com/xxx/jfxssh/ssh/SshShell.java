package com.xxx.jfxssh.ssh;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 交互式 SSH shell 通道（PTY）。
 *
 * <p>暴露服务器输出（读）/ 客户端输入（写）两个字节流，供终端模块的
 * TtyConnector 适配；并支持窗口尺寸变更。本类只做通道封装，不含终端解析。</p>
 */
public interface SshShell extends AutoCloseable {

    /**
     * @return 服务器 → 客户端 的输出流（读取终端显示数据）
     */
    InputStream getInputStream();

    /**
     * @return 客户端 → 服务器 的输入流（写入按键）
     */
    OutputStream getOutputStream();

    /**
     * 变更伪终端窗口尺寸。
     *
     * @param columns 列
     * @param rows    行
     */
    void resize(int columns, int rows);

    /**
     * @return 通道是否存活
     */
    boolean isOpen();

    /**
     * 阻塞直到通道关闭。
     *
     * @return 退出码（未知时为 0）
     */
    int waitForClose();

    /**
     * 关闭通道。
     */
    @Override
    void close();
}
