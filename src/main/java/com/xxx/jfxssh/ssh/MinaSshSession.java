package com.xxx.jfxssh.ssh;

import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.PortForwardingTracker;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
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
    private final String ptyType;

    MinaSshSession(ClientSession session, SshConnectionConfig config) {
        this.session = session;
        this.host = config.getHost();
        this.port = config.getPort();
        this.username = config.getUsername();
        this.ptyType = config.getPtyType();
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
            String type = ptyType == null || ptyType.isBlank() ? "xterm-256color" : ptyType;
            channel.setPtyType(type);
            channel.setPtyColumns(columns);
            channel.setPtyLines(rows);
            channel.open().verify(Duration.ofSeconds(10));
            log.info("Shell opened: {}@{}:{} ({}x{}, {})", username, host, port, columns, rows, type);
            return new MinaSshShell(channel);
        } catch (IOException e) {
            throw new SshConnectException("Failed to open shell on " + host + ":" + port, e);
        }
    }

    @Override
    public SftpSession openSftp() {
        try {
            SftpClient sftp = SftpClientFactory.instance().createSftpClient(session);
            log.info("SFTP opened: {}@{}:{}", username, host, port);
            return new MinaSftpSession(sftp, host, port);
        } catch (IOException e) {
            throw new SshConnectException("Failed to open SFTP on " + host + ":" + port, e);
        }
    }

    @Override
    public PortForward openForward(PortForwardSpec spec) {
        try {
            PortForwardingTracker tracker;
            switch (spec.type()) {
                case LOCAL -> tracker = session.createLocalPortForwardingTracker(
                        new SshdSocketAddress(spec.bindHost(), spec.bindPort()),
                        new SshdSocketAddress(spec.destHost(), spec.destPort()));
                case REMOTE -> tracker = session.createRemotePortForwardingTracker(
                        new SshdSocketAddress(spec.bindHost(), spec.bindPort()),
                        new SshdSocketAddress(spec.destHost(), spec.destPort()));
                case DYNAMIC -> tracker = session.createDynamicPortForwardingTracker(
                        new SshdSocketAddress(spec.bindHost(), spec.bindPort()));
                default -> throw new IllegalArgumentException("Unknown forward type: " + spec.type());
            }
            int boundPort = tracker.getBoundAddress().getPort();
            log.info("Port forward started: {} [{}] bound port {} on {}:{}",
                    spec.name(), spec.type(), boundPort, host, port);
            return new MinaPortForward(spec.name(), spec.type(), boundPort, tracker);
        } catch (IOException e) {
            throw new PortForwardException(
                    "Failed to start forward '" + spec.name() + "' on " + host + ":" + port
                            + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        session.close(false);
        log.info("SSH disconnected: {}@{}:{}", username, host, port);
    }
}
