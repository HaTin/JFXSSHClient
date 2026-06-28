package com.xxx.jfxssh.storage.entity;

import com.xxx.jfxssh.common.AuthType;

/**
 * 连接实体（对应 docs/DATABASE.md 的 connections 表）。
 *
 * <p>纯数据载体，不含持久化或业务逻辑。{@code passwordEnc} 仅存密文，
 * 禁止存放明文密码（加密方案见 docs/ARCHITECTURE.md）。</p>
 */
public final class Connection {

    private Long id;
    private String name;
    private String host;
    private int port;
    private String username;
    private AuthType authType;
    private String passwordEnc;
    private String privateKeyPath;
    private String privateKeyEnc;
    private String passphraseEnc;
    private Long groupId;
    private String remark;
    private String terminalType;
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

    /** @return 连接名称 */
    public String getName() {
        return name;
    }

    /** @param name 连接名称 */
    public void setName(String name) {
        this.name = name;
    }

    /** @return 主机 */
    public String getHost() {
        return host;
    }

    /** @param host 主机 */
    public void setHost(String host) {
        this.host = host;
    }

    /** @return 端口 */
    public int getPort() {
        return port;
    }

    /** @param port 端口 */
    public void setPort(int port) {
        this.port = port;
    }

    /** @return 用户名 */
    public String getUsername() {
        return username;
    }

    /** @param username 用户名 */
    public void setUsername(String username) {
        this.username = username;
    }

    /** @return 认证方式 */
    public AuthType getAuthType() {
        return authType;
    }

    /** @param authType 认证方式 */
    public void setAuthType(AuthType authType) {
        this.authType = authType;
    }

    /** @return 加密后的密码（密文，可空） */
    public String getPasswordEnc() {
        return passwordEnc;
    }

    /** @param passwordEnc 加密后的密码（密文，禁止明文） */
    public void setPasswordEnc(String passwordEnc) {
        this.passwordEnc = passwordEnc;
    }

    /** @return 私钥路径（可空） */
    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    /** @param privateKeyPath 私钥路径 */
    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    /** @return 私钥内容密文（AES-256-GCM，可空） */
    public String getPrivateKeyEnc() {
        return privateKeyEnc;
    }

    /** @param privateKeyEnc 私钥内容密文（禁止明文） */
    public void setPrivateKeyEnc(String privateKeyEnc) {
        this.privateKeyEnc = privateKeyEnc;
    }

    /** @return 私钥口令密文（AES-256-GCM，可空） */
    public String getPassphraseEnc() {
        return passphraseEnc;
    }

    /** @param passphraseEnc 私钥口令密文（禁止明文） */
    public void setPassphraseEnc(String passphraseEnc) {
        this.passphraseEnc = passphraseEnc;
    }

    /** @return 所属分组 id（可空，未分组为 null） */
    public Long getGroupId() {
        return groupId;
    }

    /** @param groupId 所属分组 id */
    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    /** @return 备注 */
    public String getRemark() {
        return remark;
    }

    /** @param remark 备注 */
    public void setRemark(String remark) {
        this.remark = remark;
    }

    /** @return 终端类型（PTY type，如 xterm-256color；可空表示默认） */
    public String getTerminalType() {
        return terminalType;
    }

    /** @param terminalType 终端类型 */
    public void setTerminalType(String terminalType) {
        this.terminalType = terminalType;
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
