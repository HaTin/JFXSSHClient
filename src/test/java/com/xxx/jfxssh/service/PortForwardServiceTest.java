package com.xxx.jfxssh.service;

import com.xxx.jfxssh.common.AppPaths;
import com.xxx.jfxssh.common.AuthType;
import com.xxx.jfxssh.ssh.PortForwardSpec;
import com.xxx.jfxssh.storage.Database;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.storage.entity.PortForwardRule;
import com.xxx.jfxssh.storage.repository.ConnectionRepositoryImpl;
import com.xxx.jfxssh.storage.repository.PortForwardRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PortForwardService} 单元测试。
 */
class PortForwardServiceTest {

    private PortForwardService service;
    private ConnectionService connectionService;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        Database db = new Database(new AppPaths(dir));
        db.init();
        service = new PortForwardServiceImpl(new PortForwardRepositoryImpl(db));
        connectionService = new ConnectionServiceImpl(new ConnectionRepositoryImpl(db));
    }

    @Test
    void saveAssignsIdAndTimestamps() {
        long connectionId = createConnection().getId();
        PortForwardRule rule = sampleRule(connectionId, "test-local", PortForwardSpec.Type.LOCAL);

        PortForwardRule saved = service.save(rule);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreateTime());
        assertNotNull(saved.getUpdateTime());
        assertEquals(connectionId, saved.getConnectionId());
    }

    @Test
    void saveValidatesFields() {
        long connectionId = createConnection().getId();

        PortForwardRule blankName = sampleRule(connectionId, "", PortForwardSpec.Type.LOCAL);
        assertThrows(IllegalArgumentException.class, () -> service.save(blankName));

        PortForwardRule nullType = sampleRule(connectionId, "x", PortForwardSpec.Type.LOCAL);
        nullType.setType(null);
        assertThrows(IllegalArgumentException.class, () -> service.save(nullType));

        PortForwardRule badPort = sampleRule(connectionId, "x", PortForwardSpec.Type.LOCAL);
        badPort.setBindPort(70000);
        assertThrows(IllegalArgumentException.class, () -> service.save(badPort));

        PortForwardRule missingDest = sampleRule(connectionId, "x", PortForwardSpec.Type.LOCAL);
        missingDest.setDestHost("");
        assertThrows(IllegalArgumentException.class, () -> service.save(missingDest));

        PortForwardRule dynamicOk = sampleRule(connectionId, "socks", PortForwardSpec.Type.DYNAMIC);
        dynamicOk.setDestHost(null);
        dynamicOk.setDestPort(0);
        assertNotNull(service.save(dynamicOk));
    }

    @Test
    void updateChangesRule() {
        long connectionId = createConnection().getId();
        PortForwardRule saved = service.save(sampleRule(connectionId, "original", PortForwardSpec.Type.LOCAL));
        saved.setName("renamed");
        saved.setBindPort(12345);

        PortForwardRule updated = service.update(saved);

        assertEquals("renamed", updated.getName());
        assertEquals(12345, updated.getBindPort());
        assertNotNull(updated.getUpdateTime());
    }

    @Test
    void deleteRemovesRule() {
        long connectionId = createConnection().getId();
        PortForwardRule saved = service.save(sampleRule(connectionId, "to-delete", PortForwardSpec.Type.LOCAL));

        service.delete(saved.getId());

        List<PortForwardRule> rules = service.findByConnection(connectionId);
        assertTrue(rules.isEmpty());
    }

    @Test
    void findByConnectionReturnsOnlyOwnedRules() {
        long c1 = createConnection("c1").getId();
        long c2 = createConnection("c2").getId();

        service.save(sampleRule(c1, "c1-rule", PortForwardSpec.Type.LOCAL));
        service.save(sampleRule(c2, "c2-rule", PortForwardSpec.Type.DYNAMIC));

        List<PortForwardRule> c1Rules = service.findByConnection(c1);
        assertEquals(1, c1Rules.size());
        assertEquals("c1-rule", c1Rules.get(0).getName());
    }

    @Test
    void cascadeDeleteWhenConnectionRemoved() {
        Connection c = createConnection();
        service.save(sampleRule(c.getId(), "owned", PortForwardSpec.Type.LOCAL));

        connectionService.delete(c.getId());

        assertTrue(service.findByConnection(c.getId()).isEmpty());
    }

    private Connection createConnection() {
        return createConnection("test");
    }

    private Connection createConnection(String name) {
        Connection c = new Connection();
        c.setName(name);
        c.setHost("127.0.0.1");
        c.setPort(22);
        c.setUsername("user");
        c.setAuthType(AuthType.PASSWORD);
        return connectionService.save(c);
    }

    private PortForwardRule sampleRule(long connectionId, String name, PortForwardSpec.Type type) {
        PortForwardRule rule = new PortForwardRule();
        rule.setConnectionId(connectionId);
        rule.setName(name);
        rule.setType(type);
        rule.setBindHost("127.0.0.1");
        rule.setBindPort(type == PortForwardSpec.Type.DYNAMIC ? 0 : 8080);
        rule.setDestHost(type == PortForwardSpec.Type.DYNAMIC ? null : "127.0.0.1");
        rule.setDestPort(type == PortForwardSpec.Type.DYNAMIC ? 0 : 3306);
        return rule;
    }
}
