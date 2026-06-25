package com.xxx.jfxssh.storage;

/**
 * 数据库相关运行时异常。
 *
 * <p>避免吞异常（见 docs/CODING_STANDARDS.md：禁止空 catch）。</p>
 */
public class DatabaseException extends RuntimeException {

    /**
     * @param message 错误信息
     */
    public DatabaseException(String message) {
        super(message);
    }

    /**
     * @param message 错误信息
     * @param cause   原始异常
     */
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
