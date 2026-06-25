package com.xxx.jfxssh.ssh;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.AcceptAllPublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ProcessShellFactory;

import java.io.IOException;

/**
 * 测试用进程内 SSH 服务器：密码 tester/secret、接受全部公钥、shell 为 /bin/sh。
 */
public final class EmbeddedSshServer implements AutoCloseable {

    public static final String USER = "tester";
    public static final String PASSWORD = "secret";

    private final SshServer sshd;

    public EmbeddedSshServer() throws IOException {
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(0);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        sshd.setPasswordAuthenticator((u, p, s) -> USER.equals(u) && PASSWORD.equals(p));
        sshd.setPublickeyAuthenticator(AcceptAllPublickeyAuthenticator.INSTANCE);
        sshd.setShellFactory(new ProcessShellFactory("sh", "/bin/sh"));
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
