package com.xxx.jfxssh.ssh;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

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
     * 查询远程条目属性；不存在返回 {@code empty}。
     *
     * <p>用于上传前的同名检测（是否需要覆盖确认）与文件夹上传时判断远程目录是否已存在。</p>
     *
     * @param path 远程路径
     * @return 条目（{@link SftpEntry#name()} 为路径最后一段），不存在返回 empty
     */
    Optional<SftpEntry> statEntry(String path);

    /**
     * 下载远程文件到本地。
     *
     * @param remotePath 远程文件路径
     * @param localFile  本地目标文件（已存在则覆盖）
     * @param progress   进度回调（可空）
     * @param cancelled  取消标志（可空）；返回 true 时中止并抛出 {@link SftpCancelledException}
     */
    void download(String remotePath, File localFile, SftpProgress progress, BooleanSupplier cancelled);

    /**
     * 上传本地文件到远程。
     *
     * @param localFile  本地源文件
     * @param remotePath 远程目标路径（已存在则覆盖）
     * @param progress   进度回调（可空）
     * @param cancelled  取消标志（可空）；返回 true 时中止并抛出 {@link SftpCancelledException}
     */
    void upload(File localFile, String remotePath, SftpProgress progress, BooleanSupplier cancelled);

    /**
     * 新建远程目录。
     *
     * @param path 目录路径
     */
    void mkdir(String path);

    /**
     * 重命名 / 移动远程条目。
     *
     * @param oldPath 原路径
     * @param newPath 新路径
     */
    void rename(String oldPath, String newPath);

    /**
     * 删除远程条目；目录则递归删除其内容后再删除自身。
     *
     * @param path      条目路径
     * @param directory 是否目录
     */
    void delete(String path, boolean directory);

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
