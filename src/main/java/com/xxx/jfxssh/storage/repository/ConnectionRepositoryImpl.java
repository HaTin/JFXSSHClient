package com.xxx.jfxssh.storage.repository;

import com.xxx.jfxssh.common.AuthType;
import com.xxx.jfxssh.storage.Database;
import com.xxx.jfxssh.storage.DatabaseException;
import com.xxx.jfxssh.storage.entity.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link ConnectionRepository} 的 SQLite 实现。
 *
 * <p>每次操作从 {@link Database} 获取连接并即时关闭。SQL 异常统一包装为
 * {@link DatabaseException}（见 docs/CODING_STANDARDS.md：禁止空 catch）。</p>
 */
public final class ConnectionRepositoryImpl implements ConnectionRepository {

    private static final Logger log = LoggerFactory.getLogger(ConnectionRepositoryImpl.class);

    private static final String COLUMNS =
            "id, name, host, port, username, auth_type, password_enc, "
                    + "private_key_path, group_id, remark, terminal_type, create_time, update_time";

    private final Database database;

    /**
     * @param database 数据库（构造器注入）
     */
    public ConnectionRepositoryImpl(Database database) {
        this.database = database;
    }

    @Override
    public Connection insert(Connection c) {
        String sql = "INSERT INTO connections (name, host, port, username, auth_type, "
                + "password_enc, private_key_path, group_id, remark, terminal_type, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindFields(ps, c);
            ps.setString(11, c.getCreateTime());
            ps.setString(12, c.getUpdateTime());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    c.setId(keys.getLong(1));
                }
            }
            return c;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to insert connection", e);
        }
    }

    @Override
    public void update(Connection c) {
        String sql = "UPDATE connections SET name = ?, host = ?, port = ?, username = ?, "
                + "auth_type = ?, password_enc = ?, private_key_path = ?, group_id = ?, "
                + "remark = ?, terminal_type = ?, update_time = ? WHERE id = ?";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindFields(ps, c);
            ps.setString(11, c.getUpdateTime());
            ps.setLong(12, requireId(c));
            int rows = ps.executeUpdate();
            if (rows == 0) {
                log.warn("Update affected no rows, id={}", c.getId());
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update connection", e);
        }
    }

    @Override
    public void delete(long id) {
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM connections WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to delete connection", e);
        }
    }

    @Override
    public Optional<Connection> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM connections WHERE id = ?";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query connection by id", e);
        }
    }

    @Override
    public List<Connection> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM connections ORDER BY name";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return mapAll(rs);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query connections", e);
        }
    }

    @Override
    public List<Connection> findByGroup(Long groupId) {
        String where = groupId == null ? "group_id IS NULL" : "group_id = ?";
        String sql = "SELECT " + COLUMNS + " FROM connections WHERE " + where + " ORDER BY name";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (groupId != null) {
                ps.setLong(1, groupId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query connections by group", e);
        }
    }

    /** 绑定参数 1..10（name..terminal_type），时间戳与 id 由调用方按需绑定。 */
    private void bindFields(PreparedStatement ps, Connection c) throws SQLException {
        ps.setString(1, c.getName());
        ps.setString(2, c.getHost());
        ps.setInt(3, c.getPort());
        ps.setString(4, c.getUsername());
        ps.setString(5, c.getAuthType() == null ? null : c.getAuthType().name());
        ps.setString(6, c.getPasswordEnc());
        ps.setString(7, c.getPrivateKeyPath());
        if (c.getGroupId() == null) {
            ps.setNull(8, Types.INTEGER);
        } else {
            ps.setLong(8, c.getGroupId());
        }
        ps.setString(9, c.getRemark());
        ps.setString(10, c.getTerminalType());
    }

    private List<Connection> mapAll(ResultSet rs) throws SQLException {
        List<Connection> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    private Connection map(ResultSet rs) throws SQLException {
        Connection c = new Connection();
        c.setId(rs.getLong("id"));
        c.setName(rs.getString("name"));
        c.setHost(rs.getString("host"));
        c.setPort(rs.getInt("port"));
        c.setUsername(rs.getString("username"));
        c.setAuthType(parseAuth(rs.getString("auth_type")));
        c.setPasswordEnc(rs.getString("password_enc"));
        c.setPrivateKeyPath(rs.getString("private_key_path"));
        long groupId = rs.getLong("group_id");
        c.setGroupId(rs.wasNull() ? null : groupId);
        c.setRemark(rs.getString("remark"));
        c.setTerminalType(rs.getString("terminal_type"));
        c.setCreateTime(rs.getString("create_time"));
        c.setUpdateTime(rs.getString("update_time"));
        return c;
    }

    private AuthType parseAuth(String value) {
        if (value == null) {
            return AuthType.PASSWORD;
        }
        try {
            return AuthType.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown auth_type '{}', falling back to PASSWORD", value);
            return AuthType.PASSWORD;
        }
    }

    private long requireId(Connection c) {
        if (c.getId() == null) {
            throw new DatabaseException("Connection id is required for update");
        }
        return c.getId();
    }
}
