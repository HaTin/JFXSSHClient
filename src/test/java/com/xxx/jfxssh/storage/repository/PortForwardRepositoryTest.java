package com.xxx.jfxssh.storage.repository;

import com.xxx.jfxssh.common.AppPaths;
import com.xxx.jfxssh.common.AuthType;
import com.xxx.jfxssh.ssh.PortForwardSpec;
import com.xxx.jfxssh.storage.Database;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.storage.entity.PortForwardRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PortForwardRepository} 单元测试。
 */
class PortForwardRepositoryTest {

    private PortForwardRepository repository;
    private ConnectionRepository connectionRepository;
    private long connectionId;
    private long otherConnectionId;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        Database db = new Database(new AppPaths(dir));
        db.init();
        repository = new PortForwardRepositoryImpl(db);
        connectionRepository = new ConnectionRepositoryImpl(db);
        connectionId = createConnection("test");
        otherConnectionId = createConnection("other");
    }

    private long createConnection(String name) {
        Connection c = new Connection();
        c.setName(name);
        c.setHost("127.0.0.1");
        c.setPort(22);
        c.setUsername("user");
        c.setAuthType(AuthType.PASSWORD);
        c.setCreateTime("2026-06-27T00:00:00Z");
        c.setUpdateTime("2026-06-27T00:00:00Z");
        return connectionRepository.insert(c).getId();
    }

    @Test
    void insertAssignsId() {
        PortForwardRule rule = sampleRule(connectionId, "local", PortForwardSpec.Type.LOCAL);

        PortForwardRule saved = repository.insert(rule);

        assertNotNull(saved.getId());
        assertTrue(saved.getId() > 0);
    }

    @Test
    void updateModifiesRule() {
        PortForwardRule saved = repository.insert(sampleRule(connectionId, "name", PortForwardSpec.Type.LOCAL));
        saved.setName("updated");
        saved.setBindPort(1111);
        saved.setAutoStart(true);
        saved.setUpdateTime("2026-01-01T00:00:00Z");

        repository.update(saved);
        Optional<PortForwardRule> found = repository.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals("updated", found.get().getName());
        assertEquals(1111, found.get().getBindPort());
        assertTrue(found.get().isAutoStart());
    }

    @Test
    void deleteRemovesRule() {
        PortForwardRule saved = repository.insert(sampleRule(connectionId, "delete-me", PortForwardSpec.Type.LOCAL));

        repository.delete(saved.getId());

        assertFalse(repository.findById(saved.getId()).isPresent());
    }

    @Test
    void findByConnectionFiltersAndSorts() {
        repository.insert(sampleRule(connectionId, "b", PortForwardSpec.Type.LOCAL));
        repository.insert(sampleRule(connectionId, "a", PortForwardSpec.Type.REMOTE));
        repository.insert(sampleRule(otherConnectionId, "c", PortForwardSpec.Type.DYNAMIC));

        List<PortForwardRule> rules = repository.findByConnection(connectionId);

        assertEquals(2, rules.size());
        assertEquals("a", rules.get(0).getName());
        assertEquals("b", rules.get(1).getName());
    }

    @Test
    void findAutoStartByConnectionReturnsOnlyAutoStartRules() {
        PortForwardRule auto = sampleRule(connectionId, "auto", PortForwardSpec.Type.LOCAL);
        auto.setAutoStart(true);
        PortForwardRule manual = sampleRule(connectionId, "manual", PortForwardSpec.Type.LOCAL);
        manual.setAutoStart(false);
        repository.insert(auto);
        repository.insert(manual);

        List<PortForwardRule> rules = repository.findAutoStartByConnection(connectionId);

        assertEquals(1, rules.size());
        assertEquals("auto", rules.get(0).getName());
        assertTrue(rules.get(0).isAutoStart());
    }

    @Test
    void dynamicFieldsMayBeNull() {
        PortForwardRule rule = sampleRule(connectionId, "socks", PortForwardSpec.Type.DYNAMIC);
        rule.setDestHost(null);
        rule.setDestPort(0);

        PortForwardRule saved = repository.insert(rule);
        Optional<PortForwardRule> found = repository.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals(PortForwardSpec.Type.DYNAMIC, found.get().getType());
        assertEquals(0, found.get().getDestPort());
    }

    private PortForwardRule sampleRule(long connectionId, String name, PortForwardSpec.Type type) {
        PortForwardRule rule = new PortForwardRule();
        rule.setConnectionId(connectionId);
        rule.setName(name);
        rule.setType(type);
        rule.setBindHost("127.0.0.1");
        rule.setBindPort(type == PortForwardSpec.Type.DYNAMIC ? 1080 : 8080);
        rule.setDestHost(type == PortForwardSpec.Type.DYNAMIC ? null : "127.0.0.1");
        rule.setDestPort(type == PortForwardSpec.Type.DYNAMIC ? 0 : 3306);
        rule.setAutoStart(false);
        rule.setCreateTime("2026-06-27T00:00:00Z");
        rule.setUpdateTime("2026-06-27T00:00:00Z");
        return rule;
    }
}
