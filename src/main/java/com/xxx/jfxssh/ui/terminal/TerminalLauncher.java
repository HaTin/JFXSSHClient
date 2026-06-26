package com.xxx.jfxssh.ui.terminal;

import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.storage.entity.Connection;

/**
 * 打开终端会话的回调，解耦连接树与标签页区域。
 */
@FunctionalInterface
public interface TerminalLauncher {

    /**
     * 为指定连接打开一个终端标签页。
     *
     * @param connection  连接（用于标签标题等）
     * @param config      已构建的 SSH 连接配置
     * @param onConnected 连接成功后在 FX 线程回调（可空，例如用于提示保存凭据）
     */
    void open(Connection connection, SshConnectionConfig config, Runnable onConnected);
}
