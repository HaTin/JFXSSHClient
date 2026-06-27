package com.xxx.jfxssh.storage.repository;

import com.xxx.jfxssh.storage.Database;
import com.xxx.jfxssh.storage.DatabaseException;
import com.xxx.jfxssh.storage.entity.PortForwardRule;
import com.xxx.jfxssh.ssh.PortForwardSpec;
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
 * {@link PortForwardRepository} 的 SQLite 实现。
 *
 * <p>每次操作从 {@link Database} 获取连接并即时关闭；SQL 异常统一包装为
 * {@link DatabaseException}。删除连接时依赖外键级联删除其规则。</p>
 */
public final class PortForwardRepositoryImpl implements PortForwardRepository {

    private static final Logger log = LoggerFactory.getLogger(PortForwardRepositoryImpl.class);

    private static final String COLUMNS =
            "id, connection_id, name, type, bind_host, bind_port, dest_host, dest_port, auto_start, create_time, update_time";

    private final Database database;

    /**
     * @param database 数据库（构造器注入）
     */
    public PortForwardRepositoryImpl(Database database) {
        this.database = database;
    }

    @Override
    public PortForwardRule insert(PortForwardRule rule) {
        String sql = "INSERT INTO port_forwards (connection_id, name, type, bind_host, bind_port, "
                + "dest_host, dest_port, auto_start, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindFields(ps, rule);
            ps.setString(9, rule.getCreateTime());
            ps.setString(10, rule.getUpdateTime());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    rule.setId(keys.getLong(1));
                }
            }
            return rule;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to insert port forward rule", e);
        }
    }

    @Override
    public void update(PortForwardRule rule) {
        if (rule.getId() == null) {
            throw new DatabaseException("Port forward rule id is required for update");
        }
        String sql = "UPDATE port_forwards SET connection_id = ?, name = ?, type = ?, bind_host = ?, "
                + "bind_port = ?, dest_host = ?, dest_port = ?, auto_start = ?, update_time = ? WHERE id = ?";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindFields(ps, rule);
            ps.setString(9, rule.getUpdateTime());
            ps.setLong(10, rule.getId());
            int rows = ps.executeUpdate();
            if (rows == 0) {
                log.warn("Update affected no rows, id={}", rule.getId());
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update port forward rule", e);
        }
    }

    @Override
    public void delete(long id) {
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM port_forwards WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to delete port forward rule", e);
        }
    }

    @Override
    public Optional<PortForwardRule> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM port_forwards WHERE id = ?";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query port forward rule by id", e);
        }
    }

    @Override
    public List<PortForwardRule> findByConnection(long connectionId) {
        String sql = "SELECT " + COLUMNS + " FROM port_forwards WHERE connection_id = ? ORDER BY name";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, connectionId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query port forward rules by connection", e);
        }
    }

    @Override
    public List<PortForwardRule> findAutoStartByConnection(long connectionId) {
        String sql = "SELECT " + COLUMNS + " FROM port_forwards WHERE connection_id = ? AND auto_start = 1 ORDER BY name";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, connectionId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query auto-start port forward rules", e);
        }
    }

    /** 绑定参数 1..8（connection_id..auto_start），时间戳与 id 由调用方按需绑定。 */
    private void bindFields(PreparedStatement ps, PortForwardRule rule) throws SQLException {
        ps.setLong(1, rule.getConnectionId());
        ps.setString(2, rule.getName());
        ps.setString(3, rule.getType() == null ? null : rule.getType().name());
        ps.setString(4, rule.getBindHost());
        ps.setInt(5, rule.getBindPort());
        ps.setString(6, rule.getDestHost());
        if (rule.getDestPort() == 0 && rule.getType() == PortForwardSpec.Type.DYNAMIC) {
            ps.setNull(7, Types.INTEGER);
        } else {
            ps.setInt(7, rule.getDestPort());
        }
        ps.setInt(8, rule.isAutoStart() ? 1 : 0);
    }

    private List<PortForwardRule> mapAll(ResultSet rs) throws SQLException {
        List<PortForwardRule> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    private PortForwardRule map(ResultSet rs) throws SQLException {
        PortForwardRule rule = new PortForwardRule();
        rule.setId(rs.getLong("id"));
        rule.setConnectionId(rs.getLong("connection_id"));
        rule.setName(rs.getString("name"));
        rule.setType(parseType(rs.getString("type")));
        rule.setBindHost(rs.getString("bind_host"));
        rule.setBindPort(rs.getInt("bind_port"));
        rule.setDestHost(rs.getString("dest_host"));
        int destPort = rs.getInt("dest_port");
        rule.setDestPort(rs.wasNull() ? 0 : destPort);
        rule.setAutoStart(rs.getInt("auto_start") == 1);
        rule.setCreateTime(rs.getString("create_time"));
        rule.setUpdateTime(rs.getString("update_time"));
        return rule;
    }

    private PortForwardSpec.Type parseType(String value) {
        if (value == null) {
            return PortForwardSpec.Type.LOCAL;
        }
        try {
            return PortForwardSpec.Type.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown port forward type '{}', falling back to LOCAL", value);
            return PortForwardSpec.Type.LOCAL;
        }
    }
}
