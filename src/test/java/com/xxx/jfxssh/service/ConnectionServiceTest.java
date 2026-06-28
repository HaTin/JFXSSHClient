package com.xxx.jfxssh.service;

import com.xxx.jfxssh.common.AppPaths;
import com.xxx.jfxssh.common.AuthType;
import com.xxx.jfxssh.storage.Database;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.storage.repository.ConnectionRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionServiceTest {

    private ConnectionService service;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        Database db = new Database(new AppPaths(dir));
        db.init();
        service = new ConnectionServiceImpl(new ConnectionRepositoryImpl(db));
    }

    private static Connection sample() {
        Connection c = new Connection();
        c.setName("web01");
        c.setHost("10.0.0.1");
        c.setPort(22);
        c.setUsername("root");
        c.setAuthType(AuthType.PASSWORD);
        return c;
    }

    @Test
    void saveAssignsIdAndTimestamps() {
        Connection saved = service.save(sample());
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreateTime());
        assertNotNull(saved.getUpdateTime());
        assertEquals(1, service.findAll().size());
    }

    @Test
    void findByIdReturnsSaved() {
        Connection saved = service.save(sample());
        assertEquals("web01", service.findById(saved.getId()).orElseThrow().getName());
    }

    @Test
    void updateChangesFields() {
        Connection saved = service.save(sample());
        saved.setName("web01-edited");
        saved.setPort(2222);
        service.update(saved);
        Connection after = service.findById(saved.getId()).orElseThrow();
        assertEquals("web01-edited", after.getName());
        assertEquals(2222, after.getPort());
    }

    @Test
    void deleteRemoves() {
        Connection saved = service.save(sample());
        service.delete(saved.getId());
        assertTrue(service.findAll().isEmpty());
    }

    @Test
    void findByGroupNullReturnsUngrouped() {
        service.save(sample());
        assertEquals(1, service.findByGroup(null).size());
    }

    @Test
    void validationRejectsBlankNameHostAndBadPort() {
        Connection blankName = sample();
        blankName.setName(" ");
        assertThrows(IllegalArgumentException.class, () -> service.save(blankName));

        Connection blankHost = sample();
        blankHost.setHost("");
        assertThrows(IllegalArgumentException.class, () -> service.save(blankHost));

        Connection badPort = sample();
        badPort.setPort(70000);
        assertThrows(IllegalArgumentException.class, () -> service.save(badPort));
    }

    @Test
    void privateKeyContentAndPassphrasePersist() {
        // 私钥认证不再要求文件路径；改为持久化加密后的私钥内容与口令密文。
        Connection c = sample();
        c.setAuthType(AuthType.PRIVATE_KEY);
        c.setPrivateKeyEnc("enc-key-blob");
        c.setPassphraseEnc("enc-pass-blob");
        Connection saved = service.save(c);
        Connection after = service.findById(saved.getId()).orElseThrow();
        assertEquals(AuthType.PRIVATE_KEY, after.getAuthType());
        assertEquals("enc-key-blob", after.getPrivateKeyEnc());
        assertEquals("enc-pass-blob", after.getPassphraseEnc());
    }

    @Test
    void terminalTypePersists() {
        Connection c = sample();
        c.setTerminalType("vt100");
        Connection saved = service.save(c);
        assertEquals("vt100", service.findById(saved.getId()).orElseThrow().getTerminalType());
    }
}
