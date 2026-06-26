package com.xxx.jfxssh.ssh;

/**
 * 一条远程文件 / 目录项（SFTP 列表视角）。
 *
 * <p>从底层 SFTP 属性映射而来的纯数据载体，不暴露任何 Mina 类型，供 UI 直接展示。</p>
 *
 * @param name                文件名（不含路径）
 * @param directory           是否目录
 * @param symlink             是否符号链接
 * @param regularFile         是否普通文件
 * @param size                字节大小（目录通常为 0）
 * @param modifiedEpochMillis 最后修改时间（毫秒，UTC 纪元）；不可用时为 0
 * @param permissions         POSIX 权限位的八进制字符串（如 {@code 0644}）
 */
public record SftpEntry(
        String name,
        boolean directory,
        boolean symlink,
        boolean regularFile,
        long size,
        long modifiedEpochMillis,
        String permissions) {
}
