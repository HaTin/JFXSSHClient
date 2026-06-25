package com.xxx.jfxssh.ssh;

import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;

/**
 * {@link SshShell} 的 Apache Mina 实现，封装 {@link ChannelShell}。
 */
final class MinaSshShell implements SshShell {

    private static final Logger log = LoggerFactory.getLogger(MinaSshShell.class);

    private final ChannelShell channel;

    MinaSshShell(ChannelShell channel) {
        this.channel = channel;
    }

    @Override
    public InputStream getInputStream() {
        return channel.getInvertedOut();
    }

    @Override
    public OutputStream getOutputStream() {
        return channel.getInvertedIn();
    }

    @Override
    public void resize(int columns, int rows) {
        try {
            channel.sendWindowChange(columns, rows);
        } catch (IOException e) {
            log.warn("Failed to resize shell to {}x{}", columns, rows, e);
        }
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public int waitForClose() {
        channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0L);
        Integer exit = channel.getExitStatus();
        return exit == null ? 0 : exit;
    }

    @Override
    public void close() {
        channel.close(false);
    }
}
