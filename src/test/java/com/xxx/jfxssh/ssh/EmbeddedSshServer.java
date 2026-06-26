package com.xxx.jfxssh.ssh;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.AcceptAllPublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 测试用进程内 SSH 服务器：密码 tester/secret、接受全部公钥、shell 为 /bin/sh、含 SFTP 子系统。
 *
 * <p>无参构造沿用默认文件系统；{@link #EmbeddedSshServer(Path)} 将 SFTP 根限定到指定目录
 * （{@link VirtualFileSystemFactory}），便于隔离的 SFTP 测试。</p>
 */
public final class EmbeddedSshServer implements AutoCloseable {

    public static final String USER = "tester";
    public static final String PASSWORD = "secret";

    private final SshServer sshd;

    public EmbeddedSshServer() throws IOException {
        this(null);
    }

    /**
     * @param sftpRoot SFTP 虚拟根目录（非空时所有 SFTP 操作限定其下）；null 则用默认文件系统
     * @throws IOException 启动失败时抛出
     */
    public EmbeddedSshServer(Path sftpRoot) throws IOException {
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(0);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        sshd.setPasswordAuthenticator((u, p, s) -> USER.equals(u) && PASSWORD.equals(p));
        sshd.setPublickeyAuthenticator(AcceptAllPublickeyAuthenticator.INSTANCE);
        sshd.setShellFactory(new ProcessShellFactory("sh", "/bin/sh"));
        sshd.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
        if (sftpRoot != null) {
            sshd.setFileSystemFactory(new VirtualFileSystemFactory(sftpRoot));
        }
        sshd.start();
    }

    public int port() {
        return sshd.getPort();
    }

    @Override
    public void close() throws IOException {
        sshd.stop();
    }
}
