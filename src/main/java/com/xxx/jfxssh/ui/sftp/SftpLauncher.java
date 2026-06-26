package com.xxx.jfxssh.ui.sftp;

import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.storage.entity.Connection;

/**
 * 打开 SFTP 浏览器的回调（类比 {@link com.xxx.jfxssh.ui.terminal.TerminalLauncher}）。
 *
 * <p>认证已在调用前解析完成（与开终端共用同一套流程），此处只负责建立 SFTP 并展示窗口。</p>
 */
@FunctionalInterface
public interface SftpLauncher {

    /**
     * 为指定连接打开一个独立的 SFTP 浏览窗口。
     *
     * @param connection 连接（用于窗口标题）
     * @param config     已构建的 SSH 连接配置
     */
    void open(Connection connection, SshConnectionConfig config);
}
