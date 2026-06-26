package com.xxx.jfxssh.ui.sftp;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** SFTP 浏览器通用格式化（字节大小、时间）。 */
final class SftpFormat {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private SftpFormat() {
    }

    /** 人类可读字节大小（B/KB/MB/...）。 */
    static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024.0;
            unit++;
        } while (value >= 1024.0 && unit < units.length - 1);
        return String.format("%.1f %s", value, units[unit]);
    }

    /** 毫秒纪元 → 本地时间字符串；0 返回 "-"。 */
    static String time(long epochMillis) {
        return epochMillis <= 0 ? "-" : TIME_FMT.format(Instant.ofEpochMilli(epochMillis));
    }
}
