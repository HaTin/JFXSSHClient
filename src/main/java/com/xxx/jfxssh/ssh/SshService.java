package com.xxx.jfxssh.ssh;

/**
 * SSH 连接管理接口（SSH 模块，见 docs/ARCHITECTURE.md、docs/API.md）。
 *
 * <p>职责：建立连接、认证（密码 / 公钥）、保活、关闭。不实现 shell / 终端 /
 * 命令执行。禁止直接操作 UI。</p>
 */
public interface SshService {

    /**
     * 建立并认证一个 SSH 连接。
     *
     * @param config 连接配置
     * @return 已建立的会话
     * @throws SshConnectException 连接或认证失败时抛出
     */
    SshSession connect(SshConnectionConfig config);

    /**
     * 测试连接：尝试连接并立即关闭。
     *
     * @param config 连接配置
     * @return 连接成功返回 true，否则 false
     */
    boolean test(SshConnectionConfig config);
}
