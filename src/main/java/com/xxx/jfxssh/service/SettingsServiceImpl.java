package com.xxx.jfxssh.service;

import com.xxx.jfxssh.storage.repository.SettingsRepository;

import java.util.Optional;

/**
 * {@link SettingsService} 的默认实现，委派 {@link SettingsRepository}。
 */
public final class SettingsServiceImpl implements SettingsService {

    private final SettingsRepository repository;

    /**
     * @param repository 设置仓库（构造器注入）
     */
    public SettingsServiceImpl(SettingsRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<String> get(String key) {
        return repository.find(key);
    }

    @Override
    public void set(String key, String value) {
        repository.upsert(key, value);
    }
}
