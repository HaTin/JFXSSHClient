package com.xxx.jfxssh.ssh;

import com.xxx.jfxssh.common.AuthType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端口转发集成测试：基于 {@link EmbeddedSshServer}（已开启 AcceptAllForwardingFilter）。
 * 本地转发做端到端验证（经隧道访问 echo 后端），动态转发做开启/关闭冒烟。
 */
class MinaPortForwardTest {

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

    private SshSession connect() {
        SshConnectionConfig config = SshConnectionConfig.builder("127.0.0.1", EmbeddedSshServer.USER)
                .port(server.port())
                .authType(AuthType.PASSWORD)
                .password(EmbeddedSshServer.PASSWORD)
                .build();
        return service.connect(config);
    }

    @Test
    void localForwardTunnelsToBackend() throws Exception {
        // 本地起一个 echo 后端
        try (ServerSocket backend = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            int backendPort = backend.getLocalPort();
            Thread echo = new Thread(() -> {
                try (Socket s = backend.accept();
                     InputStream in = s.getInputStream();
                     OutputStream out = s.getOutputStream()) {
                    byte[] buf = new byte[1024];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        out.flush();
                    }
                } catch (IOException ignore) {
                    // 连接关闭即退出
                }
            }, "echo-backend");
            echo.setDaemon(true);
            echo.start();

            try (SshSession ssh = connect()) {
                PortForward fwd = ssh.openForward(new PortForwardSpec(
                        "test-local", PortForwardSpec.Type.LOCAL, "127.0.0.1", 0,
                        "127.0.0.1", backendPort));
                assertTrue(fwd.isOpen());
                assertTrue(fwd.boundPort() > 0);

                byte[] payload = "hello-forward".getBytes(StandardCharsets.UTF_8);
                try (Socket client = new Socket("127.0.0.1", fwd.boundPort())) {
                    client.getOutputStream().write(payload);
                    client.getOutputStream().flush();
                    byte[] echoed = new byte[payload.length];
                    int read = 0;
                    while (read < echoed.length) {
                        int n = client.getInputStream().read(echoed, read, echoed.length - read);
                        if (n < 0) {
                            break;
                        }
                        read += n;
                    }
                    assertArrayEquals(payload, echoed);
                }

                fwd.close();
                assertFalse(fwd.isOpen());

                // 关闭后本地监听应已释放
                int closedPort = fwd.boundPort();
                assertThrows(IOException.class,
                        () -> new Socket("127.0.0.1", closedPort).close(),
                        "Port " + closedPort + " should no longer be listening after close");
            }
        }
    }

    @Test
    void dynamicForwardOpensAndCloses() {
        try (SshSession ssh = connect()) {
            PortForward fwd = ssh.openForward(new PortForwardSpec(
                    "test-socks", PortForwardSpec.Type.DYNAMIC, "127.0.0.1", 0, "", 0));
            assertTrue(fwd.isOpen());
            assertTrue(fwd.boundPort() > 0);
            fwd.close();
            assertFalse(fwd.isOpen());
        }
    }
}
