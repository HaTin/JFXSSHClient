package com.xxx.jfxssh.common;

/**
 * 认证方式枚举（见 docs/DATABASE.md 的 connections.auth_type）。
 */
public enum AuthType {

    /** 密码认证。 */
    PASSWORD,

    /** 公钥认证。 */
    PRIVATE_KEY
}
