package com.xxx.jfxssh.ssh;

/**
 * 端口转发失败（如本地绑定端口被占用），携带可读原因供 UI 展示。
 */
public final class PortForwardException extends RuntimeException {

    public PortForwardException(String message, Throwable cause) {
        super(message, cause);
    }
}
