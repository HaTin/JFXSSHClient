package com.xxx.jfxssh.ssh;

import com.xxx.jfxssh.common.AuthType;

import java.time.Duration;

/**
 * SSH 连接配置（值对象）。
 *
 * <p>描述一次连接所需的参数，使 SSH 模块与存储实体（Connection）解耦：
 * Service 层负责把 Connection 映射为本配置。密码 / 口令为内存中的明文，
 * 仅用于建立连接，不做持久化（持久化为密文，见 ARCHITECTURE.md 加密方案）。</p>
 */
public final class SshConnectionConfig {

    private final String host;
    private final int port;
    private final String username;
    private final AuthType authType;
    private final String password;
    private final String privateKeyPath;
    private final String passphrase;
    private final Duration connectTimeout;
    private final Duration authTimeout;
    private final Duration keepAliveInterval;
    private final String ptyType;

    private SshConnectionConfig(Builder b) {
        this.host = b.host;
        this.port = b.port;
        this.username = b.username;
        this.authType = b.authType;
        this.password = b.password;
        this.privateKeyPath = b.privateKeyPath;
        this.passphrase = b.passphrase;
        this.connectTimeout = b.connectTimeout;
        this.authTimeout = b.authTimeout;
        this.keepAliveInterval = b.keepAliveInterval;
        this.ptyType = b.ptyType;
    }

    /** @return 主机 */
    public String getHost() {
        return host;
    }

    /** @return 端口 */
    public int getPort() {
        return port;
    }

    /** @return 用户名 */
    public String getUsername() {
        return username;
    }

    /** @return 认证方式 */
    public AuthType getAuthType() {
        return authType;
    }

    /** @return 明文密码（密码认证用，可空） */
    public String getPassword() {
        return password;
    }

    /** @return 私钥路径（公钥认证用，可空） */
    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    /** @return 私钥口令（可空） */
    public String getPassphrase() {
        return passphrase;
    }

    /** @return 连接超时 */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /** @return 认证超时 */
    public Duration getAuthTimeout() {
        return authTimeout;
    }

    /** @return 保活心跳间隔（为 0 表示禁用） */
    public Duration getKeepAliveInterval() {
        return keepAliveInterval;
    }

    /** @return 终端类型（PTY type，如 xterm-256color / vt100 / ansi / linux） */
    public String getPtyType() {
        return ptyType;
    }

    /**
     * 创建构建器。
     *
     * @param host     主机
     * @param username 用户名
     * @return 构建器
     */
    public static Builder builder(String host, String username) {
        return new Builder(host, username);
    }

    /**
     * {@link SshConnectionConfig} 构建器。
     */
    public static final class Builder {

        private final String host;
        private final String username;
        private int port = 22;
        private AuthType authType = AuthType.PASSWORD;
        private String password;
        private String privateKeyPath;
        private String passphrase;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration authTimeout = Duration.ofSeconds(10);
        private Duration keepAliveInterval = Duration.ofSeconds(30);
        private String ptyType = "xterm-256color";

        private Builder(String host, String username) {
            this.host = host;
            this.username = username;
        }

        /** @param port 端口 @return this */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /** @param authType 认证方式 @return this */
        public Builder authType(AuthType authType) {
            this.authType = authType;
            return this;
        }

        /** @param password 明文密码 @return this */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /** @param privateKeyPath 私钥路径 @return this */
        public Builder privateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
            return this;
        }

        /** @param passphrase 私钥口令 @return this */
        public Builder passphrase(String passphrase) {
            this.passphrase = passphrase;
            return this;
        }

        /** @param connectTimeout 连接超时 @return this */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /** @param authTimeout 认证超时 @return this */
        public Builder authTimeout(Duration authTimeout) {
            this.authTimeout = authTimeout;
            return this;
        }

        /** @param keepAliveInterval 保活心跳间隔（0 禁用） @return this */
        public Builder keepAliveInterval(Duration keepAliveInterval) {
            this.keepAliveInterval = keepAliveInterval;
            return this;
        }

        /** @param ptyType 终端类型（PTY type） @return this */
        public Builder ptyType(String ptyType) {
            this.ptyType = ptyType;
            return this;
        }

        /** @return 构建的配置 */
        public SshConnectionConfig build() {
            return new SshConnectionConfig(this);
        }
    }
}
