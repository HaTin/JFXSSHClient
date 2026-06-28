package com.xxx.jfxssh.ssh;

import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
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
import java.util.Optional;
import java.util.function.BooleanSupplier;

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
            throw wrap(e, "Failed to resolve path '" + path + "' on " + target());
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
            throw wrap(e, "Failed to list '" + path + "' on " + target());
        }
    }

    @Override
    public Optional<SftpEntry> statEntry(String path) {
        try {
            SftpClient.Attributes attrs = client.stat(path);
            String name = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            int slash = name.lastIndexOf('/');
            if (slash >= 0) {
                name = name.substring(slash + 1);
            }
            return Optional.of(toEntry(name, attrs));
        } catch (IOException e) {
            if (e instanceof SftpException se && se.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE) {
                return Optional.empty();
            }
            throw wrap(e, "Failed to stat '" + path + "' on " + target());
        }
    }

    @Override
    public void download(String remotePath, File localFile, SftpProgress progress, BooleanSupplier cancelled) {
        long total = statSize(remotePath);
        try (InputStream in = client.read(remotePath);
             FileOutputStream out = new FileOutputStream(localFile)) {
            copy(in, out, total, progress, cancelled);
            log.info("Downloaded {} -> {} from {}", remotePath, localFile.getAbsolutePath(), target());
        } catch (SftpCancelledException ce) {
            deleteLocalQuietly(localFile);
            throw ce;
        } catch (IOException e) {
            throw wrap(e, "Failed to download '" + remotePath + "' from " + target());
        }
    }

    @Override
    public void upload(File localFile, String remotePath, SftpProgress progress, BooleanSupplier cancelled) {
        long total = localFile.length();
        try (FileInputStream in = new FileInputStream(localFile);
             OutputStream out = client.write(remotePath)) {
            copy(in, out, total, progress, cancelled);
            log.info("Uploaded {} -> {} on {}", localFile.getAbsolutePath(), remotePath, target());
        } catch (SftpCancelledException ce) {
            deleteRemoteQuietly(remotePath);
            throw ce;
        } catch (IOException e) {
            throw wrap(e, "Failed to upload '" + remotePath + "' to " + target());
        }
    }

    @Override
    public void mkdir(String path) {
        try {
            client.mkdir(path);
        } catch (IOException e) {
            throw wrap(e, "Failed to create '" + path + "' on " + target());
        }
    }

    @Override
    public void rename(String oldPath, String newPath) {
        try {
            client.rename(oldPath, newPath);
        } catch (IOException e) {
            throw wrap(e, "Failed to rename '" + oldPath + "' on " + target());
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
            throw wrap(e, "Failed to delete '" + path + "' on " + target());
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

    /** 分块拷贝并上报进度；取消标志置位时中止并抛出 {@link SftpCancelledException}。 */
    private void copy(InputStream in, OutputStream out, long total,
                      SftpProgress progress, BooleanSupplier cancelled) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long done = 0;
        int read;
        if (progress != null) {
            progress.update(0, total);
        }
        while ((read = in.read(buffer)) != -1) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                throw new SftpCancelledException("Transfer cancelled");
            }
            out.write(buffer, 0, read);
            done += read;
            if (progress != null) {
                progress.update(done, total);
            }
        }
    }

    private void deleteLocalQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            log.warn("Could not delete partial local file {}", file.getAbsolutePath());
        }
    }

    private void deleteRemoteQuietly(String remotePath) {
        try {
            client.remove(remotePath);
        } catch (IOException e) {
            log.warn("Could not delete partial remote file {}: {}", remotePath, e.getMessage());
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

    /**
     * 把底层 IOException 归一化：SFTP 协议错误转 {@link SftpOperationException}（带状态码），
     * 其余转 {@link SshConnectException}。
     */
    private RuntimeException wrap(IOException e, String context) {
        if (e instanceof SftpException se) {
            log.warn("{} (sftp status {}): {}", context, se.getStatus(), se.getMessage());
            return new SftpOperationException(se.getStatus(), context + ": " + se.getMessage());
        }
        return new SshConnectException(context, e);
    }
}
