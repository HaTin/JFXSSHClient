package com.xxx.jfxssh.ssh;

import org.apache.sshd.sftp.client.SftpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    public void download(String remotePath, File localFile, SftpProgress progress) {
        long total = statSize(remotePath);
        try (InputStream in = client.read(remotePath);
             FileOutputStream out = new FileOutputStream(localFile)) {
            copy(in, out, total, progress);
            log.info("Downloaded {} -> {} from {}", remotePath, localFile.getAbsolutePath(), target());
        } catch (IOException e) {
            throw new SshConnectException("Failed to download '" + remotePath + "' from " + target(), e);
        }
    }

    @Override
    public void upload(File localFile, String remotePath, SftpProgress progress) {
        long total = localFile.length();
        try (FileInputStream in = new FileInputStream(localFile);
             OutputStream out = client.write(remotePath)) {
            copy(in, out, total, progress);
            log.info("Uploaded {} -> {} on {}", localFile.getAbsolutePath(), remotePath, target());
        } catch (IOException e) {
            throw new SshConnectException("Failed to upload '" + remotePath + "' to " + target(), e);
        }
    }

    @Override
    public void mkdir(String path) {
        try {
            client.mkdir(path);
        } catch (IOException e) {
            throw new SshConnectException("Failed to create '" + path + "' on " + target(), e);
        }
    }

    @Override
    public void rename(String oldPath, String newPath) {
        try {
            client.rename(oldPath, newPath);
        } catch (IOException e) {
            throw new SshConnectException("Failed to rename '" + oldPath + "' on " + target(), e);
        }
    }

    @Override
    public void delete(String path, boolean directory) {
        try {
            if (directory) {
                deleteDirRecursive(path);
            } else {
                client.remove(path);
            }
        } catch (IOException e) {
            throw new SshConnectException("Failed to delete '" + path + "' on " + target(), e);
        }
    }

    private void deleteDirRecursive(String path) throws IOException {
        for (SftpClient.DirEntry entry : client.readDir(path)) {
            String name = entry.getFilename();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String child = path.endsWith("/") ? path + name : path + "/" + name;
            if (entry.getAttributes().isDirectory()) {
                deleteDirRecursive(child);
            } else {
                client.remove(child);
            }
        }
        client.rmdir(path);
    }

    private long statSize(String path) {
        try {
            return client.stat(path).getSize();
        } catch (IOException e) {
            return -1L;
        }
    }

    /** 分块拷贝并上报进度。 */
    private void copy(InputStream in, OutputStream out, long total, SftpProgress progress) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long done = 0;
        int read;
        if (progress != null) {
            progress.update(0, total);
        }
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            done += read;
            if (progress != null) {
                progress.update(done, total);
            }
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
