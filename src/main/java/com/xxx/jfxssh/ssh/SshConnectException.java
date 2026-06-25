package com.xxx.jfxssh.ssh;

/**
 * SSH 连接 / 认证失败异常。
 *
 * <p>包装底层传输异常，避免向上层泄漏 Mina 类型（见 docs/CODING_STANDARDS.md）。</p>
 */
public class SshConnectException extends RuntimeException {

    /**
     * @param message 错误信息
     */
    public SshConnectException(String message) {
        super(message);
    }

    /**
     * @param message 错误信息
     * @param cause   原始异常
     */
    public SshConnectException(String message, Throwable cause) {
        super(message, cause);
    }
}
