package com.xxx.jfxssh.ui.forward;

import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.storage.entity.Connection;

/**
 * 打开端口转发管理窗口的回调（类比 {@link com.xxx.jfxssh.ui.sftp.SftpLauncher}）。
 *
 * <p>认证已在调用前解析完成（与开终端 / SFTP 共用同一套流程）。</p>
 */
@FunctionalInterface
public interface PortForwardLauncher {

    /**
     * 为指定连接打开一个独立的端口转发管理窗口。
     *
     * @param connection 连接（用于窗口标题）
     * @param config     已构建的 SSH 连接配置
     */
    void open(Connection connection, SshConnectionConfig config);
}
