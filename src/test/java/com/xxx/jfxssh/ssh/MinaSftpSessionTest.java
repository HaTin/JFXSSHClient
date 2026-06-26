package com.xxx.jfxssh.ssh;

import com.xxx.jfxssh.common.AuthType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * SFTP 会话集成测试：基于 {@link EmbeddedSshServer}（虚拟根 + SFTP 子系统），
 * 验证列目录、目录/文件区分、下载字节一致、开启/关闭状态。
 */
class MinaSftpSessionTest {

    private EmbeddedSshServer server;
    private MinaSshService service;

    @AfterEach
    void tearDown() throws IOException {
        if (service != null) {
            service.close();
        }
        if (server != null) {
            server.close();
        }
    }

    /** 以给定目录为 SFTP 虚拟根启动服务器并建立连接。 */
    private SshSession connect(Path root) throws IOException {
        server = new EmbeddedSshServer(root);
        service = new MinaSshService();
        SshConnectionConfig config = SshConnectionConfig.builder("127.0.0.1", EmbeddedSshServer.USER)
                .port(server.port())
                .authType(AuthType.PASSWORD)
                .password(EmbeddedSshServer.PASSWORD)
                .build();
        return service.connect(config);
    }

    @Test
    void listsDirectoriesAndFiles(@TempDir Path remote) throws Exception {
        Files.createDirectory(remote.resolve("docs"));
        Files.writeString(remote.resolve("app.log"), "hello world");

        try (SshSession ssh = connect(remote)) {
            SftpSession sftp = ssh.openSftp();
            String home = sftp.home();
            assertNotNull(home);
            assertFalse(home.isBlank());

            List<SftpEntry> entries = sftp.list(home);
            Map<String, SftpEntry> byName = entries.stream()
                    .collect(Collectors.toMap(SftpEntry::name, Function.identity()));

            assertTrue(byName.containsKey("docs"), "expected docs dir");
            assertTrue(byName.containsKey("app.log"), "expected app.log file");
            assertTrue(byName.get("docs").directory());
            assertFalse(byName.get("app.log").directory());
            assertTrue(byName.get("app.log").regularFile());
            assertEquals("hello world".getBytes(StandardCharsets.UTF_8).length,
                    byName.get("app.log").size());
            // "." 与 ".." 应被过滤
            assertFalse(byName.containsKey("."));
            assertFalse(byName.containsKey(".."));

            sftp.close();
        }
    }

    @Test
    void downloadsFileBytes(@TempDir Path remote, @TempDir Path localDir) throws Exception {
        byte[] payload = "the quick brown fox\n0123456789".getBytes(StandardCharsets.UTF_8);
        Files.write(remote.resolve("data.bin"), payload);

        try (SshSession ssh = connect(remote)) {
            SftpSession sftp = ssh.openSftp();
            assertTrue(sftp.isOpen());

            String remotePath = sftp.canonicalPath("data.bin");
            Path local = localDir.resolve("data.bin");
            sftp.download(remotePath, local.toFile(), null, null);

            assertArrayEquals(payload, Files.readAllBytes(local));

            sftp.close();
            assertFalse(sftp.isOpen());
        }
    }

    @Test
    void uploadsFileWithProgress(@TempDir Path remote, @TempDir Path localDir) throws Exception {
        byte[] payload = new byte[40_000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 97);
        }
        Path localFile = localDir.resolve("upload.bin");
        Files.write(localFile, payload);

        try (SshSession ssh = connect(remote)) {
            SftpSession sftp = ssh.openSftp();
            long[] last = {-1};
            sftp.upload(localFile.toFile(), sftp.canonicalPath(".") + "/uploaded.bin",
                    (transferred, total) -> last[0] = transferred, null);

            assertArrayEquals(payload, Files.readAllBytes(remote.resolve("uploaded.bin")));
            assertEquals(payload.length, last[0], "final progress should equal size");
            sftp.close();
        }
    }

    @Test
    void uploadCancelledRemovesPartial(@TempDir Path remote, @TempDir Path localDir) throws Exception {
        byte[] payload = new byte[200_000];
        Path localFile = localDir.resolve("big.bin");
        Files.write(localFile, payload);

        try (SshSession ssh = connect(remote)) {
            SftpSession sftp = ssh.openSftp();
            String target = sftp.canonicalPath(".") + "/big.bin";
            assertThrows(SftpCancelledException.class, () ->
                    sftp.upload(localFile.toFile(), target, null, () -> true));
            assertFalse(Files.exists(remote.resolve("big.bin")), "partial upload should be removed");
            sftp.close();
        }
    }

    @Test
    void multipleChannelsCoexist(@TempDir Path remote) throws Exception {
        Files.writeString(remote.resolve("a.txt"), "a");
        try (SshSession ssh = connect(remote)) {
            // 浏览通道与传输通道同时存在、互不影响（修复"上传堵塞浏览"的设计依据）
            SftpSession browse = ssh.openSftp();
            SftpSession transfer = ssh.openSftp();
            assertTrue(browse.isOpen());
            assertTrue(transfer.isOpen());
            assertEquals(1, browse.list(browse.home()).size());
            assertEquals(1, transfer.list(transfer.home()).size());

            transfer.close();
            assertFalse(transfer.isOpen());
            assertTrue(browse.isOpen(), "closing one channel must not close the other");
            assertEquals(1, browse.list(browse.home()).size());
            browse.close();
        }
    }

    @Test
    void permissionDeniedReportsStatus(@TempDir Path remote, @TempDir Path localDir) throws Exception {
        Path localFile = Files.writeString(localDir.resolve("src.bin"), "data");
        java.io.File root = remote.toFile();
        try (SshSession ssh = connect(remote)) {
            SftpSession sftp = ssh.openSftp();
            String target = sftp.canonicalPath(".") + "/denied.bin";
            root.setWritable(false);
            boolean threw = false;
            try {
                sftp.upload(localFile.toFile(), target, null, null);
            } catch (SftpOperationException ex) {
                threw = true;
                assertEquals(3, ex.status(), "SSH_FX_PERMISSION_DENIED");
            } finally {
                root.setWritable(true);
                sftp.close();
            }
            // 以 root 运行时只读目录无法生效（root 绕过权限检查），此时跳过断言
            assumeTrue(threw, "environment cannot enforce read-only (running as root?)");
        }
    }

    @Test
    void mkdirRenameDelete(@TempDir Path remote) throws Exception {
        try (SshSession ssh = connect(remote)) {
            SftpSession sftp = ssh.openSftp();
            String home = sftp.home();

            sftp.mkdir(home + "/dir1");
            assertTrue(Files.isDirectory(remote.resolve("dir1")));

            sftp.rename(home + "/dir1", home + "/dir2");
            assertFalse(Files.exists(remote.resolve("dir1")));
            assertTrue(Files.isDirectory(remote.resolve("dir2")));

            // 目录非空时递归删除
            Files.writeString(remote.resolve("dir2").resolve("inner.txt"), "x");
            sftp.delete(home + "/dir2", true);
            assertFalse(Files.exists(remote.resolve("dir2")));

            sftp.close();
        }
    }
}
