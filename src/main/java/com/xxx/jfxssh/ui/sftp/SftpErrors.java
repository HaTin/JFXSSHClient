package com.xxx.jfxssh.ui.sftp;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ssh.SftpOperationException;

import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;

/** 把底层异常翻译成可读的失败原因（权限不足 / 文件不存在 / 已存在 / 目录非空 …）。 */
final class SftpErrors {

    // SFTP 状态码（SSH_FX_*）
    private static final int NO_SUCH_FILE = 2;
    private static final int PERMISSION_DENIED = 3;
    private static final int OP_UNSUPPORTED = 8;
    private static final int NO_SUCH_PATH = 10;
    private static final int FILE_ALREADY_EXISTS = 11;
    private static final int DIR_NOT_EMPTY = 18;

    private SftpErrors() {
    }

    /**
     * @return 已本地化的失败原因；无法判定时返回 null
     */
    static String reason(Throwable ex) {
        if (ex instanceof SftpOperationException oe) {
            return switch (oe.status()) {
                case PERMISSION_DENIED -> I18n.t("sftp.reason.permission");
                case NO_SUCH_FILE, NO_SUCH_PATH -> I18n.t("sftp.reason.no_such_file");
                case FILE_ALREADY_EXISTS -> I18n.t("sftp.reason.exists");
                case DIR_NOT_EMPTY -> I18n.t("sftp.reason.not_empty");
                case OP_UNSUPPORTED -> I18n.t("sftp.reason.unsupported");
                default -> null;
            };
        }
        if (ex instanceof AccessDeniedException) {
            return I18n.t("sftp.reason.permission");
        }
        if (ex instanceof NoSuchFileException) {
            return I18n.t("sftp.reason.no_such_file");
        }
        if (ex instanceof FileAlreadyExistsException) {
            return I18n.t("sftp.reason.exists");
        }
        if (ex instanceof DirectoryNotEmptyException) {
            return I18n.t("sftp.reason.not_empty");
        }
        return null;
    }

    /**
     * 组合操作失败消息：{@code baseKey} 形如 "... {0}: {1}"，{0}=名称、{1}=原因。
     *
     * @param baseKey 两参消息键（如 sftp.error.upload）
     * @param name    文件 / 目录名
     * @param ex      失败异常
     * @return 已本地化的完整消息
     */
    static String message(String baseKey, String name, Throwable ex) {
        String reason = reason(ex);
        if (reason == null) {
            reason = I18n.t("sftp.reason.unknown");
        }
        return I18n.t(baseKey, name, reason);
    }
}
