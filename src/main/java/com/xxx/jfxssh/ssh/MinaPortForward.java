package com.xxx.jfxssh.ssh;

import org.apache.sshd.client.session.forward.PortForwardingTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * {@link PortForward} 的 Apache Mina 实现：包装一个 {@link PortForwardingTracker}。
 *
 * <p>{@link #close()} 幂等；底层 SSH 会话关闭会自动断开转发通道，故二次关闭被容忍。</p>
 */
final class MinaPortForward implements PortForward {

    private static final Logger log = LoggerFactory.getLogger(MinaPortForward.class);

    private final String name;
    private final PortForwardSpec.Type type;
    private final int boundPort;
    private final PortForwardingTracker tracker;
    private volatile boolean open = true;

    MinaPortForward(String name, PortForwardSpec.Type type, int boundPort, PortForwardingTracker tracker) {
        this.name = name;
        this.type = type;
        this.boundPort = boundPort;
        this.tracker = tracker;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public PortForwardSpec.Type type() {
        return type;
    }

    @Override
    public int boundPort() {
        return boundPort;
    }

    @Override
    public boolean isOpen() {
        return open && tracker.isOpen();
    }

    @Override
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        try {
            tracker.close();
            log.info("Port forward stopped: {} (port {})", name, boundPort);
        } catch (IOException e) {
            log.warn("Error stopping port forward {}: {}", name, e.getMessage());
        }
    }
}
