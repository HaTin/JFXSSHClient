package com.xxx.jfxssh.storage.repository;

import com.xxx.jfxssh.storage.Database;
import com.xxx.jfxssh.storage.DatabaseException;
import com.xxx.jfxssh.storage.entity.Group;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link GroupRepository} 的 SQLite 实现。
 *
 * <p>每次操作从 {@link Database} 获取连接并即时关闭；SQL 异常统一包装为
 * {@link DatabaseException}。删除依赖数据库外键级联（子分组级联删除、
 * 连接 group_id 置空）。</p>
 */
public final class GroupRepositoryImpl implements GroupRepository {

    private static final String COLUMNS = "id, name, parent_id, sort, create_time, update_time";

    private final Database database;

    /**
     * @param database 数据库（构造器注入）
     */
    public GroupRepositoryImpl(Database database) {
        this.database = database;
    }

    @Override
    public Group insert(Group g) {
        String sql = "INSERT INTO groups (name, parent_id, sort, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, g.getName());
            setNullableLong(ps, 2, g.getParentId());
            ps.setInt(3, g.getSort());
            ps.setString(4, g.getCreateTime());
            ps.setString(5, g.getUpdateTime());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    g.setId(keys.getLong(1));
                }
            }
            return g;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to insert group", e);
        }
    }

    @Override
    public void update(Group g) {
        if (g.getId() == null) {
            throw new DatabaseException("Group id is required for update");
        }
        String sql = "UPDATE groups SET name = ?, parent_id = ?, sort = ?, update_time = ? WHERE id = ?";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, g.getName());
            setNullableLong(ps, 2, g.getParentId());
            ps.setInt(3, g.getSort());
            ps.setString(4, g.getUpdateTime());
            ps.setLong(5, g.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update group", e);
        }
    }

    @Override
    public void delete(long id) {
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM groups WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to delete group", e);
        }
    }

    @Override
    public Optional<Group> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM groups WHERE id = ?";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query group by id", e);
        }
    }

    @Override
    public List<Group> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM groups ORDER BY sort, name";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return mapAll(rs);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query groups", e);
        }
    }

    @Override
    public List<Group> findByParent(Long parentId) {
        String where = parentId == null ? "parent_id IS NULL" : "parent_id = ?";
        String sql = "SELECT " + COLUMNS + " FROM groups WHERE " + where + " ORDER BY sort, name";
        try (java.sql.Connection conn = database.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (parentId != null) {
                ps.setLong(1, parentId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query groups by parent", e);
        }
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setLong(index, value);
        }
    }

    private List<Group> mapAll(ResultSet rs) throws SQLException {
        List<Group> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    private Group map(ResultSet rs) throws SQLException {
        Group g = new Group();
        g.setId(rs.getLong("id"));
        g.setName(rs.getString("name"));
        long parentId = rs.getLong("parent_id");
        g.setParentId(rs.wasNull() ? null : parentId);
        g.setSort(rs.getInt("sort"));
        g.setCreateTime(rs.getString("create_time"));
        g.setUpdateTime(rs.getString("update_time"));
        return g;
    }
}
