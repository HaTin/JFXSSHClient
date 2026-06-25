package com.xxx.jfxssh.ssh;

import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

/**
 * {@link SshSession} 的 Apache Mina 实现。
 *
 * <p>包装 Mina 的 {@link ClientSession}，仅暴露连接存活与关闭，不暴露底层会话。</p>
 */
final class MinaSshSession implements SshSession {

    private static final Logger log = LoggerFactory.getLogger(MinaSshSession.class);

    private final ClientSession session;
    private final String host;
    private final int port;
    private final String username;

    MinaSshSession(ClientSession session, SshConnectionConfig config) {
        this.session = session;
        this.host = config.getHost();
        this.port = config.getPort();
        this.username = config.getUsername();
    }

    @Override
    public String host() {
        return host;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public String username() {
        return username;
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public SshShell openShell(int columns, int rows) {
        try {
            ChannelShell channel = session.createShellChannel();
            channel.setPtyType("xterm-256color");
            channel.setPtyColumns(columns);
            channel.setPtyLines(rows);
            channel.open().verify(Duration.ofSeconds(10));
            log.info("Shell opened: {}@{}:{} ({}x{})", username, host, port, columns, rows);
            return new MinaSshShell(channel);
        } catch (IOException e) {
            throw new SshConnectException("Failed to open shell on " + host + ":" + port, e);
        }
    }

    @Override
    public void close() {
        session.close(false);
        log.info("SSH disconnected: {}@{}:{}", username, host, port);
    }
}
