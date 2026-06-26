package com.xxx.jfxssh.service;

import com.xxx.jfxssh.common.AppPaths;
import com.xxx.jfxssh.common.AuthType;
import com.xxx.jfxssh.storage.Database;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.storage.entity.Group;
import com.xxx.jfxssh.storage.repository.ConnectionRepositoryImpl;
import com.xxx.jfxssh.storage.repository.GroupRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionPortServiceTest {

    private record Env(ConnectionService connections, GroupService groups, ConnectionPortService port) {
    }

    private Env env(Path dir) {
        Database db = new Database(new AppPaths(dir));
        db.init();
        ConnectionService cs = new ConnectionServiceImpl(new ConnectionRepositoryImpl(db));
        GroupService gs = new GroupServiceImpl(new GroupRepositoryImpl(db));
        return new Env(cs, gs, new ConnectionPortService(cs, gs));
    }

    @Test
    void exportThenImportIntoFreshDbRecreatesStructureWithoutPassword(@TempDir Path dir) throws Exception {
        // source DB with a nested group + a connection in it
        Env src = env(dir.resolve("src"));
        Group prod = src.groups().save(group("Production", null));
        Group web = src.groups().save(group("Web", prod.getId()));
        Connection c = new Connection();
        c.setName("web01");
        c.setHost("10.0.0.1");
        c.setPort(2222);
        c.setUsername("root");
        c.setAuthType(AuthType.PASSWORD);
        c.setPasswordEnc("SHOULD-NOT-BE-EXPORTED");
        c.setGroupId(web.getId());
        c.setTerminalType("vt100");
        src.connections().save(c);

        Path file = dir.resolve("export.json");
        int[] exported = src.port().exportTo(file);
        assertEquals(1, exported[0]);
        assertEquals(2, exported[1]);

        // password must not appear in the file (neither the ciphertext nor a password field)
        String json = Files.readString(file);
        assertFalse(json.contains("SHOULD-NOT-BE-EXPORTED"));
        assertFalse(json.contains("passwordEnc"));
        assertFalse(json.contains("password_enc"));

        // import into a fresh DB
        Env dst = env(dir.resolve("dst"));
        int[] imported = dst.port().importFrom(file);
        assertEquals(1, imported[0]);
        assertEquals(0, imported[1]);

        assertEquals(2, dst.groups().findAll().size());
        List<Connection> all = dst.connections().findAll();
        assertEquals(1, all.size());
        Connection got = all.get(0);
        assertEquals("web01", got.getName());
        assertEquals(2222, got.getPort());
        assertEquals("vt100", got.getTerminalType());
        assertNull(got.getPasswordEnc(), "password must not be imported");
        // connection sits under the recreated "Web" group
        Group web2 = dst.groups().findById(got.getGroupId()).orElseThrow();
        assertEquals("Web", web2.getName());
    }

    @Test
    void importIsIdempotent(@TempDir Path dir) {
        Env src = env(dir.resolve("s"));
        Group g = src.groups().save(group("Prod", null));
        Connection c = new Connection();
        c.setName("a");
        c.setHost("h");
        c.setPort(22);
        c.setUsername("u");
        c.setAuthType(AuthType.PASSWORD);
        c.setGroupId(g.getId());
        src.connections().save(c);
        Path file = dir.resolve("e.json");
        src.port().exportTo(file);

        int[] first = src.port().importFrom(file);   // into same DB
        int[] second = src.port().importFrom(file);
        assertEquals(0, first[0]);                    // already present -> skipped
        assertTrue(second[0] == 0);                   // still no duplicates
        assertEquals(1, src.connections().findAll().size());
        assertEquals(1, src.groups().findAll().size());
    }

    private Group group(String name, Long parentId) {
        Group g = new Group();
        g.setName(name);
        g.setParentId(parentId);
        return g;
    }
}
