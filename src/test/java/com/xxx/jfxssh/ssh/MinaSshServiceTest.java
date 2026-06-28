package com.xxx.jfxssh.ssh;

import com.xxx.jfxssh.common.AuthType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinaSshServiceTest {

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

    private SshConnectionConfig passwordConfig(String password) {
        return SshConnectionConfig.builder("127.0.0.1", EmbeddedSshServer.USER)
                .port(server.port())
                .authType(AuthType.PASSWORD)
                .password(password)
                .build();
    }

    @Test
    void passwordAuthConnects() {
        try (SshSession session = service.connect(passwordConfig(EmbeddedSshServer.PASSWORD))) {
            assertTrue(session.isOpen());
        }
    }

    @Test
    void wrongPasswordFailsTest() {
        assertFalse(service.test(passwordConfig("wrong")));
    }

    @Test
    void publicKeyAuthConnects(@TempDir Path dir) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        Path keyFile = dir.resolve("id_rsa");
        String body = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(pair.getPrivate().getEncoded());
        Files.writeString(keyFile, "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n");

        SshConnectionConfig config = SshConnectionConfig.builder("127.0.0.1", EmbeddedSshServer.USER)
                .port(server.port())
                .authType(AuthType.PRIVATE_KEY)
                .privateKeyPath(keyFile.toString())
                .build();
        try (SshSession session = service.connect(config)) {
            assertTrue(session.isOpen());
        }
    }

    @Test
    void publicKeyAuthFromInMemoryContentConnects() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String body = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(pair.getPrivate().getEncoded());
        String keyContent = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";

        // 不写文件，直接把私钥内容交给配置（对应「输入私钥内容」加密存库后的连接路径）
        SshConnectionConfig config = SshConnectionConfig.builder("127.0.0.1", EmbeddedSshServer.USER)
                .port(server.port())
                .authType(AuthType.PRIVATE_KEY)
                .privateKeyContent(keyContent)
                .build();
        try (SshSession session = service.connect(config)) {
            assertTrue(session.isOpen());
        }
    }

    @Test
    void openShellEchoesCommand() throws Exception {
        try (SshSession session = service.connect(passwordConfig(EmbeddedSshServer.PASSWORD))) {
            SshShell shell = session.openShell(80, 24);
            shell.getOutputStream().write("echo MARKER_42\n".getBytes());
            shell.getOutputStream().write("exit\n".getBytes());
            shell.getOutputStream().flush();

            StringBuilder out = new StringBuilder();
            byte[] buf = new byte[1024];
            int n;
            while ((n = shell.getInputStream().read(buf)) >= 0) {
                out.append(new String(buf, 0, n));
                if (out.indexOf("MARKER_42") >= 0) {
                    break;
                }
            }
            assertTrue(out.indexOf("MARKER_42") >= 0, "shell output should contain the echoed marker");
            shell.close();
        }
    }

    @Test
    void knownHostsTofuRecordsThenMatchesWithoutPrompt() {
        java.util.Map<String, String> store = new java.util.HashMap<>();
        boolean[] prompted = {false};
        KnownHostsVerifier verifier = new KnownHostsVerifier(
                new HostKeyStore() {
                    public java.util.Optional<String> find(String host, int port) {
                        return java.util.Optional.ofNullable(store.get(host + ":" + port));
                    }

                    public void save(String host, int port, String fp) {
                        store.put(host + ":" + port, fp);
                    }
                },
                (h, p, s, r) -> {
                    prompted[0] = true;
                    return false;
                },
                () -> true);
        try (MinaSshService verified = new MinaSshService(verifier)) {
            try (SshSession s1 = verified.connect(passwordConfig(EmbeddedSshServer.PASSWORD))) {
                assertTrue(s1.isOpen());
            }
            try (SshSession s2 = verified.connect(passwordConfig(EmbeddedSshServer.PASSWORD))) {
                assertTrue(s2.isOpen());
            }
        }
        assertEquals(1, store.size());
        assertFalse(prompted[0]);
    }
}
