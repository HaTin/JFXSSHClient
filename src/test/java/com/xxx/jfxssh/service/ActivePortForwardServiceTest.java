package com.xxx.jfxssh.service;

import com.xxx.jfxssh.common.AppPaths;
import com.xxx.jfxssh.common.AuthType;
import com.xxx.jfxssh.ssh.EmbeddedSshServer;
import com.xxx.jfxssh.ssh.PortForwardSpec;
import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.ssh.SshService;
import com.xxx.jfxssh.storage.Database;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.storage.entity.PortForwardRule;
import com.xxx.jfxssh.storage.repository.ConnectionRepositoryImpl;
import com.xxx.jfxssh.storage.repository.PortForwardRepositoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ActivePortForwardService} 单元测试。
 *
 * <p>使用真实 SQLite 与 {@link com.xxx.jfxssh.ssh.MinaSshService} 集成测试。
 * 由于需要真实 SSH 服务器，测试依赖 {@link com.xxx.jfxssh.ssh.EmbeddedSshServer}。</p>
 */
class ActivePortForwardServiceTest {

    private EmbeddedSshServer server;
    private ActivePortForwardService service;
    private Connection connection;
    private SshConnectionConfig config;

    @BeforeEach
    void setUp(@TempDir Path dir) throws Exception {
        server = new EmbeddedSshServer();
        Database db = new Database(new AppPaths(dir));
        db.init();
        ConnectionService connectionService = new ConnectionServiceImpl(new ConnectionRepositoryImpl(db));
        PortForwardService portForwardService = new PortForwardServiceImpl(new PortForwardRepositoryImpl(db));
        SshService sshService = new com.xxx.jfxssh.ssh.MinaSshService();
        service = new ActivePortForwardServiceImpl(sshService);

        connection = new Connection();
        connection.setName("test");
        connection.setHost("127.0.0.1");
        connection.setPort(server.port());
        connection.setUsername(EmbeddedSshServer.USER);
        connection.setAuthType(AuthType.PASSWORD);
        connection = connectionService.save(connection);

        config = SshConnectionConfig.builder("127.0.0.1", EmbeddedSshServer.USER)
                .port(server.port())
                .authType(AuthType.PASSWORD)
                .password(EmbeddedSshServer.PASSWORD)
                .connectTimeout(Duration.ofSeconds(5))
                .authTimeout(Duration.ofSeconds(5))
                .build();

        // 保存规则以便启动
        PortForwardRule rule = new PortForwardRule();
        rule.setConnectionId(connection.getId());
        rule.setName("dynamic");
        rule.setType(PortForwardSpec.Type.DYNAMIC);
        rule.setBindHost("127.0.0.1");
        rule.setBindPort(0);
        portForwardService.save(rule);
    }

    @AfterEach
    void tearDown() throws Exception {
        service.stopAll();
        server.close();
    }

    @Test
    void startsAndStopsForward() {
        PortForwardSpec spec = new PortForwardSpec(
                "dynamic", PortForwardSpec.Type.DYNAMIC, "127.0.0.1", 0, "", 0);

        int boundPort = service.startForward(connection, config, spec);

        assertTrue(boundPort > 0);
        List<ActivePortForwardService.ActiveForwardInfo> active = service.getActiveForwards(connection.getId());
        assertEquals(1, active.size());
        assertEquals(boundPort, active.get(0).bindPort());

        service.stopForward(connection.getId(), "dynamic");
        assertTrue(service.getActiveForwards(connection.getId()).isEmpty());
    }

    @Test
    void stopAllClosesAllForwards() {
        PortForwardSpec spec1 = new PortForwardSpec(
                "dynamic1", PortForwardSpec.Type.DYNAMIC, "127.0.0.1", 0, "", 0);
        PortForwardSpec spec2 = new PortForwardSpec(
                "dynamic2", PortForwardSpec.Type.DYNAMIC, "127.0.0.1", 0, "", 0);

        service.startForward(connection, config, spec1);
        service.startForward(connection, config, spec2);
        assertEquals(2, service.getActiveForwards(connection.getId()).size());

        service.stopAll();
        assertTrue(service.getActiveForwards().isEmpty());
    }
}
