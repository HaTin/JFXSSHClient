package com.xxx.jfxssh.service;

/**
 * 导入 / 导出失败异常。
 */
public class ConnectionPortException extends RuntimeException {

    /**
     * @param message 错误信息
     * @param cause   原始异常
     */
    public ConnectionPortException(String message, Throwable cause) {
        super(message, cause);
    }
}
