package com.xxx.jfxssh.ssh;

/**
 * 文件传输进度回调（上传 / 下载）。
 *
 * <p>在传输线程上被频繁回调，实现方应自行节流并切回 UI 线程更新界面。</p>
 */
@FunctionalInterface
public interface SftpProgress {

    /**
     * @param transferred 已传输字节数
     * @param total       总字节数；未知时为 -1
     */
    void update(long transferred, long total);
}
