package com.xxx.jfxssh.storage;

import com.xxx.jfxssh.common.AppPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;

/**
 * 数据库初始化模块。
 *
 * <p>负责创建 SQLite 数据库文件、建表（执行 {@code db/schema.sql}）并维护
 * schema 版本。仅做初始化与连接获取，不包含任何业务查询（业务由 Repository
 * 层在后续里程碑实现）。</p>
 */
public final class Database {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private static final String SCHEMA_RESOURCE = "/db/schema.sql";
    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final AppPaths paths;
    private final String jdbcUrl;

    /**
     * @param paths 应用路径解析器
     */
    public Database(AppPaths paths) {
        this.paths = paths;
        this.jdbcUrl = "jdbc:sqlite:" + paths.databaseFile().toString();
    }

    /**
     * 初始化数据库：确保目录存在、建表、写入 schema 版本。
     *
     * @throws DatabaseException 初始化失败时抛出
     */
    public void init() {
        try {
            Files.createDirectories(paths.appDir());
        } catch (IOException e) {
            throw new DatabaseException("Failed to create app directory: " + paths.appDir(), e);
        }

        try (Connection conn = openConnection()) {
            enableForeignKeys(conn);
            applySchema(conn);
            ensureSchemaVersion(conn);
            log.info("Database initialized: {}", paths.databaseFile());
        } catch (SQLException e) {
            throw new DatabaseException("Failed to initialize database", e);
        }
    }

    /**
     * 打开一个新的数据库连接（已开启外键约束）。
     *
     * @return JDBC 连接
     * @throws SQLException 连接失败时抛出
     */
    public Connection openConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    private void enableForeignKeys(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
    }

    private void applySchema(Connection conn) throws SQLException {
        String schema = readSchema();
        try (Statement st = conn.createStatement()) {
            for (String statement : schema.split(";")) {
                String sql = statement.trim();
                if (!sql.isEmpty()) {
                    st.execute(sql);
                }
            }
        }
    }

    private void ensureSchemaVersion(Connection conn) throws SQLException {
        boolean empty;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM schema_version")) {
            empty = rs.next() && rs.getInt(1) == 0;
        }
        if (empty) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO schema_version (version, applied_time) VALUES (?, ?)")) {
                ps.setInt(1, CURRENT_SCHEMA_VERSION);
                ps.setString(2, OffsetDateTime.now().toString());
                ps.executeUpdate();
            }
            log.info("Schema version set to {}", CURRENT_SCHEMA_VERSION);
        }
    }

    private String readSchema() {
        try (InputStream in = Database.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new DatabaseException("Schema resource not found: " + SCHEMA_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DatabaseException("Failed to read schema resource", e);
        }
    }
}
