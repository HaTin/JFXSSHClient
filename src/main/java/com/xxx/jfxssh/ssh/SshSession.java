package com.xxx.jfxssh.ssh;

/**
 * 一个已建立的 SSH 会话（连接管理视角）。
 *
 * <p>仅表示连接的存活与关闭，不涉及 shell / 终端 / 命令执行（终端为后续模块）。
 * 实现持有底层传输会话，但不向外暴露，禁止操作 UI。</p>
 */
public interface SshSession extends AutoCloseable {

    /** @return 主机 */
    String host();

    /** @return 端口 */
    int port();

    /** @return 用户名 */
    String username();

    /** @return 连接是否存活 */
    boolean isOpen();

    /**
     * 打开交互式 shell 通道（PTY）。
     *
     * @param columns 初始列数
     * @param rows    初始行数
     * @return shell 通道
     * @throws SshConnectException 打开失败时抛出
     */
    SshShell openShell(int columns, int rows);

    /**
     * 基于本会话打开一个 SFTP 客户端。
     *
     * @return SFTP 会话
     * @throws SshConnectException 打开 SFTP 子系统失败时抛出
     */
    SftpSession openSftp();

    /**
     * 关闭连接。
     */
    @Override
    void close();
}
