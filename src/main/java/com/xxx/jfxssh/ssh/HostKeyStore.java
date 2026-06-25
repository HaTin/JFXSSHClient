package com.xxx.jfxssh.ssh;

import java.util.Optional;

/**
 * 主机密钥（指纹）存储，用于 known_hosts 校验（TOFU）。
 */
public interface HostKeyStore {

    /**
     * 查询已记录的主机密钥指纹。
     *
     * @param host 主机
     * @param port 端口
     * @return 指纹，未记录时为空
     */
    Optional<String> find(String host, int port);

    /**
     * 记录 / 更新主机密钥指纹。
     *
     * @param host        主机
     * @param port        端口
     * @param fingerprint 指纹
     */
    void save(String host, int port, String fingerprint);
}
