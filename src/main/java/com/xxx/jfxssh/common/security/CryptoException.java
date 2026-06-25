package com.xxx.jfxssh.common.security;

/**
 * 加密相关运行时异常（避免空 catch，见 docs/CODING_STANDARDS.md）。
 */
public class CryptoException extends RuntimeException {

    /**
     * @param message 错误信息
     * @param cause   原始异常
     */
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
