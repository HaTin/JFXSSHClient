package com.xxx.jfxssh.storage;

import com.xxx.jfxssh.common.AppPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    @Test
    void initIsIdempotent(@TempDir Path dir) {
        Database db = new Database(new AppPaths(dir));
        db.init();
        assertDoesNotThrow(db::init); // 二次初始化（迁移幂等）不应报错
    }

    @Test
    void schemaHasTerminalTypeColumnAndVersion(@TempDir Path dir) throws Exception {
        Database db = new Database(new AppPaths(dir));
        db.init();
        try (Connection conn = db.openConnection();
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM pragma_table_info('connections') WHERE name = 'terminal_type'")) {
                assertTrue(rs.next() && rs.getInt(1) == 1, "connections.terminal_type column should exist");
            }
            try (ResultSet rs = st.executeQuery("SELECT MAX(version) FROM schema_version")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
        }
    }
}
