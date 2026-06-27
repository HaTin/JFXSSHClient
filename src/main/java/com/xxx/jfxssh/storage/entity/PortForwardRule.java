package com.xxx.jfxssh.storage.entity;

import com.xxx.jfxssh.ssh.PortForwardSpec;

/**
 * 端口转发规则实体（对应 docs/DATABASE.md 的 port_forwards 表）。
 *
 * <p>纯数据载体，不含持久化或业务逻辑。规则按连接保存，删除连接时级联删除。</p>
 */
public final class PortForwardRule {

    private Long id;
    private Long connectionId;
    private String name;
    private PortForwardSpec.Type type;
    private String bindHost;
    private int bindPort;
    private String destHost;
    private int destPort;
    private boolean autoStart;
    private String createTime;
    private String updateTime;

    /** @return 主键，未持久化时为 null */
    public Long getId() {
        return id;
    }

    /** @param id 主键 */
    public void setId(Long id) {
        this.id = id;
    }

    /** @return 所属连接 id */
    public Long getConnectionId() {
        return connectionId;
    }

    /** @param connectionId 所属连接 id */
    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    /** @return 规则名 */
    public String getName() {
        return name;
    }

    /** @param name 规则名 */
    public void setName(String name) {
        this.name = name;
    }

    /** @return 转发类型 */
    public PortForwardSpec.Type getType() {
        return type;
    }

    /** @param type 转发类型 */
    public void setType(PortForwardSpec.Type type) {
        this.type = type;
    }

    /** @return 绑定主机 */
    public String getBindHost() {
        return bindHost;
    }

    /** @param bindHost 绑定主机 */
    public void setBindHost(String bindHost) {
        this.bindHost = bindHost;
    }

    /** @return 绑定端口（0 表示自动） */
    public int getBindPort() {
        return bindPort;
    }

    /** @param bindPort 绑定端口（0 表示自动） */
    public void setBindPort(int bindPort) {
        this.bindPort = bindPort;
    }

    /** @return 目标主机（DYNAMIC 类型可为 null） */
    public String getDestHost() {
        return destHost;
    }

    /** @param destHost 目标主机（DYNAMIC 类型可为 null） */
    public void setDestHost(String destHost) {
        this.destHost = destHost;
    }

    /** @return 目标端口（DYNAMIC 类型可为 0） */
    public int getDestPort() {
        return destPort;
    }

    /** @param destPort 目标端口（DYNAMIC 类型可为 0） */
    public void setDestPort(int destPort) {
        this.destPort = destPort;
    }

    /** @return 连接成功后是否自动启动 */
    public boolean isAutoStart() {
        return autoStart;
    }

    /** @param autoStart 连接成功后是否自动启动 */
    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    /** @return 创建时间（ISO-8601） */
    public String getCreateTime() {
        return createTime;
    }

    /** @param createTime 创建时间（ISO-8601） */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    /** @return 更新时间（ISO-8601） */
    public String getUpdateTime() {
        return updateTime;
    }

    /** @param updateTime 更新时间（ISO-8601） */
    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }
}
