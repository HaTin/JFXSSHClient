package com.xxx.jfxssh.service;

import com.xxx.jfxssh.common.AppPaths;
import com.xxx.jfxssh.common.AuthType;
import com.xxx.jfxssh.storage.Database;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.storage.entity.Group;
import com.xxx.jfxssh.storage.repository.ConnectionRepositoryImpl;
import com.xxx.jfxssh.storage.repository.GroupRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupServiceTest {

    private GroupService groups;
    private ConnectionService connections;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        Database db = new Database(new AppPaths(dir));
        db.init();
        groups = new GroupServiceImpl(new GroupRepositoryImpl(db));
        connections = new ConnectionServiceImpl(new ConnectionRepositoryImpl(db));
    }

    private Group group(String name, Long parentId) {
        Group g = new Group();
        g.setName(name);
        g.setParentId(parentId);
        return g;
    }

    @Test
    void saveRenameFindTree() {
        Group root = groups.save(group("Production", null));
        groups.save(group("Web", root.getId()));
        groups.rename(root.getId(), "Prod");

        List<GroupNode> tree = groups.findTree();
        assertEquals(1, tree.size());
        assertEquals("Prod", tree.get(0).getGroup().getName());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("Web", tree.get(0).getChildren().get(0).getGroup().getName());
    }

    @Test
    void deleteCascadesChildrenAndUngroupsConnections() {
        Group root = groups.save(group("Production", null));
        Group child = groups.save(group("Web", root.getId()));

        Connection c = new Connection();
        c.setName("web01");
        c.setHost("10.0.0.1");
        c.setPort(22);
        c.setAuthType(AuthType.PASSWORD);
        c.setGroupId(child.getId());
        c = connections.save(c);

        groups.delete(root.getId());

        assertTrue(groups.findAll().isEmpty());
        Connection after = connections.findById(c.getId()).orElseThrow();
        assertNull(after.getGroupId());
    }

    @Test
    void validationRejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> groups.save(group(" ", null)));
    }
}
