package com.xxx.jfxssh.ssh;

/**
 * 传输被用户取消时抛出（区别于真正的失败）。
 */
public final class SftpCancelledException extends RuntimeException {

    public SftpCancelledException(String message) {
        super(message);
    }
}
