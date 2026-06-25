package com.xxx.jfxssh.storage.repository;

import java.util.Optional;

/**
 * 键值设置数据访问接口（Repository 层，见 docs/DATABASE.md 的 settings 表）。
 */
public interface SettingsRepository {

    /**
     * 按键查询。
     *
     * @param key 键
     * @return 值，不存在时为空
     */
    Optional<String> find(String key);

    /**
     * 写入（存在则更新）。
     *
     * @param key   键
     * @param value 值
     */
    void upsert(String key, String value);
}
