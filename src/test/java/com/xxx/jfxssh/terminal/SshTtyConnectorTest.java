package com.xxx.jfxssh.terminal;

import com.xxx.jfxssh.common.AuthType;
import com.xxx.jfxssh.ssh.EmbeddedSshServer;
import com.xxx.jfxssh.ssh.MinaSshService;
import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.ssh.SshSession;
import com.xxx.jfxssh.ssh.SshShell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SshTtyConnectorTest {

    private EmbeddedSshServer server;
    private MinaSshService service;

    @BeforeEach
    void setUp() throws IOException {
        server = new EmbeddedSshServer();
        service = new MinaSshService();
    }

    @AfterEach
    void tearDown() throws IOException {
        service.close();
        server.close();
    }

    private SshShell openShell() {
        SshConnectionConfig config = SshConnectionConfig.builder("127.0.0.1", EmbeddedSshServer.USER)
                .port(server.port())
                .authType(AuthType.PASSWORD)
                .password(EmbeddedSshServer.PASSWORD)
                .build();
        SshSession session = service.connect(config);
        return session.openShell(80, 24);
    }

    @Test
    void writeReadRoundTrip() throws Exception {
        SshShell shell = openShell();
        SshTtyConnector connector = new SshTtyConnector(shell, "test", null);
        assertTrue(connector.isConnected());
        connector.write("echo MARK_7\n");

        StringBuilder out = new StringBuilder();
        char[] buf = new char[1024];
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && out.indexOf("MARK_7") < 0) {
            if (connector.ready()) {
                int n = connector.read(buf, 0, buf.length);
                if (n < 0) {
                    break;
                }
                out.append(buf, 0, n);
            } else {
                Thread.sleep(30);
            }
        }
        assertTrue(out.indexOf("MARK_7") >= 0);
        connector.close();
    }

    @Test
    void writeAfterCloseDoesNotThrowAndEnterTriggersReconnectOnce() throws Exception {
        SshShell shell = openShell();
        AtomicInteger reconnects = new AtomicInteger();
        SshTtyConnector connector = new SshTtyConnector(shell, "test", reconnects::incrementAndGet);

        connector.write("exit\n");
        shell.waitForClose();
        Thread.sleep(200);
        assertFalse(connector.isConnected());

        // writing to a closed channel must be swallowed, not thrown
        assertDoesNotThrow(() -> connector.write("ls\n".getBytes()));

        // the newline above already requested a reconnect; another Enter is a no-op (one-shot)
        connector.write("\r".getBytes());
        assertEquals(1, reconnects.get());
    }
}
