package com.xxx.jfxssh.ssh;

import java.io.File;
import java.util.List;

/**
 * 一个已打开的 SFTP 会话（基于某条 {@link SshSession}）。
 *
 * <p>仅提供首版所需的只读浏览与下载能力：列目录、解析路径、下载文件。
 * 不暴露底层 Mina 类型，禁止操作 UI。</p>
 *
 * <p><b>线程约束：</b>底层 SFTP 客户端<b>非线程安全</b>，调用方必须串行化所有方法调用
 * （UI 层为每个窗口分配单线程执行器）。</p>
 */
public interface SftpSession extends AutoCloseable {

    /**
     * @return 起始目录（通常为用户家目录）的规范绝对路径
     */
    String home();

    /**
     * 将给定路径解析为规范绝对路径（处理 {@code .} / {@code ..}）。
     *
     * @param path 远程路径
     * @return 规范绝对路径
     */
    String canonicalPath(String path);

    /**
     * 列出目录下的条目（已过滤 {@code .} 与 {@code ..}）。
     *
     * @param path 远程目录路径
     * @return 条目列表
     */
    List<SftpEntry> list(String path);

    /**
     * 下载远程文件到本地。
     *
     * @param remotePath 远程文件路径
     * @param localFile  本地目标文件（已存在则覆盖）
     */
    void download(String remotePath, File localFile);

    /**
     * @return SFTP 客户端是否仍存活
     */
    boolean isOpen();

    /**
     * 关闭 SFTP 客户端（<b>不</b>关闭其依附的 {@link SshSession}）。
     */
    @Override
    void close();
}
