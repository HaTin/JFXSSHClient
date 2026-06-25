package com.xxx.jfxssh.service;

import java.util.Optional;

/**
 * 应用设置业务接口（Service 层，见 docs/API.md）。
 *
 * <p>键值形式的持久化设置（落 settings 表）。当前用于安全参数等。</p>
 */
public interface SettingsService {

    /**
     * 读取设置。
     *
     * @param key 键
     * @return 值，不存在时为空
     */
    Optional<String> get(String key);

    /**
     * 写入设置。
     *
     * @param key   键
     * @param value 值
     */
    void set(String key, String value);
}
