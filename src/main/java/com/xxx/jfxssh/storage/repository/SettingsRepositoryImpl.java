package com.xxx.jfxssh.storage.repository;

import com.xxx.jfxssh.storage.Database;
import com.xxx.jfxssh.storage.DatabaseException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * {@link SettingsRepository} 的 SQLite 实现（使用 ON CONFLICT 实现 upsert）。
 */
public final class SettingsRepositoryImpl implements SettingsRepository {

    private final Database database;

    /**
     * @param database 数据库（构造器注入）
     */
    public SettingsRepositoryImpl(Database database) {
        this.database = database;
    }

    @Override
    public Optional<String> find(String key) {
        String sql = "SELECT value FROM settings WHERE key = ?";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to read setting: " + key, e);
        }
    }

    @Override
    public void upsert(String key, String value) {
        String now = OffsetDateTime.now().toString();
        String sql = "INSERT INTO settings (key, value, create_time, update_time) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value, update_time = excluded.update_time";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.setString(3, now);
            ps.setString(4, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to write setting: " + key, e);
        }
    }
}
