package com.xxx.jfxssh.service;

import com.xxx.jfxssh.ssh.PortForwardSpec;
import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.ssh.SshService;
import com.xxx.jfxssh.storage.entity.Connection;

import java.util.List;

/**
 * 后台端口转发服务。
 *
 * <p>独立于 UI 窗口持有活跃的 SSH 会话与 {@link com.xxx.jfxssh.ssh.PortForward} 句柄。
 * 关闭 {@link com.xxx.jfxssh.ui.forward.PortForwardWindow} 不会停止此处管理的转发；
 * 应用退出时调用 {@link #stopAll()} 统一释放资源。</p>
 */
public interface ActivePortForwardService {

    /**
     * 启动一条后台转发。
     *
     * <p>若该连接尚无活跃 SSH 会话，会自动建立；否则复用已有会话。</p>
     *
     * @param connection 所属连接（用于展示与资源分组）
     * @param config     SSH 连接配置
     * @param spec       转发规则
     * @return 实际绑定端口
     */
    int startForward(Connection connection, SshConnectionConfig config, PortForwardSpec spec);

    /**
     * 停止某连接下指定名称的后台转发。
     *
     * @param connectionId 连接 id
     * @param ruleName     规则名
     */
    void stopForward(long connectionId, String ruleName);

    /**
     * 停止某连接的所有后台转发并关闭其 SSH 会话。
     *
     * @param connectionId 连接 id
     */
    void stopAll(long connectionId);

    /** 停止所有后台转发并关闭所有 SSH 会话（应用退出时调用）。 */
    void stopAll();

    /**
     * 查询某连接下所有活跃转发。
     *
     * @param connectionId 连接 id
     * @return 活跃转发信息列表
     */
    List<ActiveForwardInfo> getActiveForwards(long connectionId);

    /**
     * 查询所有活跃转发。
     *
     * @return 活跃转发信息列表
     */
    List<ActiveForwardInfo> getActiveForwards();

    /** 一条活跃转发的只读信息。 */
    final class ActiveForwardInfo {
        private final long connectionId;
        private final String connectionName;
        private final String ruleName;
        private final PortForwardSpec.Type type;
        private final String bindHost;
        private final int bindPort;
        private final String destHost;
        private final int destPort;

        public ActiveForwardInfo(long connectionId, String connectionName, String ruleName,
                                 PortForwardSpec.Type type, String bindHost, int bindPort,
                                 String destHost, int destPort) {
            this.connectionId = connectionId;
            this.connectionName = connectionName;
            this.ruleName = ruleName;
            this.type = type;
            this.bindHost = bindHost;
            this.bindPort = bindPort;
            this.destHost = destHost;
            this.destPort = destPort;
        }

        public long connectionId() {
            return connectionId;
        }

        public String connectionName() {
            return connectionName;
        }

        public String ruleName() {
            return ruleName;
        }

        public PortForwardSpec.Type type() {
            return type;
        }

        public String bindHost() {
            return bindHost;
        }

        public int bindPort() {
            return bindPort;
        }

        public String destHost() {
            return destHost;
        }

        public int destPort() {
            return destPort;
        }
    }
}
