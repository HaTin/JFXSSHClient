/**
 * SSH 传输模块。
 *
 * <p>基于 Apache Mina SSHD 封装连接的建立、认证（密码 / 公钥）、保活与关闭。
 * 已实现：SshService（连接管理）。不包含 shell / 终端 / 命令执行，禁止操作 UI
 * （见 docs/ARCHITECTURE.md）。</p>
 */
package com.xxx.jfxssh.ssh;
