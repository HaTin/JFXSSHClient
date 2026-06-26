package com.xxx.jfxssh.ssh;

/**
 * SFTP 操作失败（含服务器返回的状态码），用于向 UI 传达具体原因（如权限不足）。
 *
 * <p>状态码取值见 {@code SSH_FX_*}（如 2=文件不存在、3=权限不足、11=已存在、18=目录非空）。</p>
 */
public final class SftpOperationException extends RuntimeException {

    /** 未知 / 非 SFTP 协议错误。 */
    public static final int STATUS_UNKNOWN = -1;

    private final int status;

    /**
     * @param status        SFTP 状态码（{@code SSH_FX_*}），未知传 {@link #STATUS_UNKNOWN}
     * @param message        诊断信息（服务器原文 + 上下文）
     */
    public SftpOperationException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** @return SFTP 状态码 */
    public int status() {
        return status;
    }
}
