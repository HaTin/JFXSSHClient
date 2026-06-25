package com.xxx.jfxssh.service;

import com.xxx.jfxssh.ssh.HostKeyStore;

import java.util.Optional;

/**
 * 基于 {@link SettingsService}（settings 表）的主机密钥存储。
 *
 * <p>每个主机一条记录，key 形如 {@code hostkey:host:port}。</p>
 */
public final class SettingsHostKeyStore implements HostKeyStore {

    private final SettingsService settings;

    /**
     * @param settings 设置服务（构造器注入）
     */
    public SettingsHostKeyStore(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    public Optional<String> find(String host, int port) {
        return settings.get(key(host, port));
    }

    @Override
    public void save(String host, int port, String fingerprint) {
        settings.set(key(host, port), fingerprint);
    }

    private String key(String host, int port) {
        return "hostkey:" + host + ":" + port;
    }
}
