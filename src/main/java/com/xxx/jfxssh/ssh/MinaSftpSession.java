package com.xxx.jfxssh.ssh;

import org.apache.sshd.sftp.client.SftpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link SftpSession} 的 Apache Mina 实现。
 *
 * <p>包装 Mina 的 {@link SftpClient}，仅暴露浏览与下载。{@link #close()} 只关闭 SFTP
 * 客户端，其底层 {@code ClientSession} 由 {@link MinaSshSession} 持有、单独关闭。</p>
 *
 * <p><b>非线程安全：</b>{@link SftpClient} 不支持并发调用，调用方须串行化。</p>
 */
final class MinaSftpSession implements SftpSession {

    private static final Logger log = LoggerFactory.getLogger(MinaSftpSession.class);

    private static final int BUFFER_SIZE = 8192;

    private final SftpClient client;
    private final String host;
    private final int port;

    MinaSftpSession(SftpClient client, String host, int port) {
        this.client = client;
        this.host = host;
        this.port = port;
    }

    @Override
    public String home() {
        return canonicalPath(".");
    }

    @Override
    public String canonicalPath(String path) {
        try {
            return client.canonicalPath(path);
        } catch (IOException e) {
            throw new SshConnectException("Failed to resolve path '" + path + "' on " + target(), e);
        }
    }

    @Override
    public List<SftpEntry> list(String path) {
        List<SftpEntry> entries = new ArrayList<>();
        try {
            for (SftpClient.DirEntry entry : client.readDir(path)) {
                String name = entry.getFilename();
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }
                entries.add(toEntry(name, entry.getAttributes()));
            }
            return entries;
        } catch (IOException e) {
            throw new SshConnectException("Failed to list '" + path + "' on " + target(), e);
        }
    }

    @Override
    public void download(String remotePath, File localFile) {
        try (InputStream in = client.read(remotePath);
             FileOutputStream out = new FileOutputStream(localFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            log.info("Downloaded {} -> {} from {}", remotePath, localFile.getAbsolutePath(), target());
        } catch (IOException e) {
            throw new SshConnectException("Failed to download '" + remotePath + "' from " + target(), e);
        }
    }

    @Override
    public boolean isOpen() {
        return client.isOpen();
    }

    @Override
    public void close() {
        try {
            client.close();
            log.info("SFTP closed: {}", target());
        } catch (IOException e) {
            log.warn("Error closing SFTP on {}: {}", target(), e.getMessage());
        }
    }

    private SftpEntry toEntry(String name, SftpClient.Attributes attrs) {
        FileTime mtime = attrs.getModifyTime();
        long modified = mtime == null ? 0L : mtime.toMillis();
        String perms = String.format("%04o", attrs.getPermissions() & 0x0FFF);
        return new SftpEntry(name, attrs.isDirectory(), attrs.isSymbolicLink(),
                attrs.isRegularFile(), attrs.getSize(), modified, perms);
    }

    private String target() {
        return host + ":" + port;
    }
}
