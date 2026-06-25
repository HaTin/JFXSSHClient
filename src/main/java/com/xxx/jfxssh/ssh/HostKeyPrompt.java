package com.xxx.jfxssh.ssh;

/**
 * 主机密钥变更时的用户确认回调（由 UI 实现，校验器不依赖具体界面框架）。
 */
public interface HostKeyPrompt {

    /**
     * 已记录的指纹与本次收到的不一致时调用，提示用户确认。
     *
     * @param host                主机
     * @param port                端口
     * @param storedFingerprint   已记录指纹
     * @param receivedFingerprint 本次收到指纹
     * @return 用户选择继续返回 true；取消返回 false
     */
    boolean onMismatch(String host, int port, String storedFingerprint, String receivedFingerprint);
}
