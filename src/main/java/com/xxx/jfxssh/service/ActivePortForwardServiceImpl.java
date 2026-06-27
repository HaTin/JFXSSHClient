package com.xxx.jfxssh.service;

import com.xxx.jfxssh.ssh.PortForward;
import com.xxx.jfxssh.ssh.PortForwardException;
import com.xxx.jfxssh.ssh.PortForwardSpec;
import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.ssh.SshService;
import com.xxx.jfxssh.ssh.SshSession;
import com.xxx.jfxssh.storage.entity.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ActivePortForwardService} 的默认实现。
 *
 * <p>按连接持有 SSH 会话与转发句柄；窗口关闭不影响此处状态。
 * 对 Mina forwarder 因绑定失败进入关闭状态的情况做重连重试。</p>
 */
public final class ActivePortForwardServiceImpl implements ActivePortForwardService {

    private static final Logger log = LoggerFactory.getLogger(ActivePortForwardServiceImpl.class);

    private final SshService sshService;
    private final Map<Long, SessionContext> sessions = new ConcurrentHashMap<>();

    public ActivePortForwardServiceImpl(SshService sshService) {
        this.sshService = sshService;
    }

    @Override
    public int startForward(Connection connection, SshConnectionConfig config, PortForwardSpec spec) {
        SessionContext ctx = sessions.computeIfAbsent(connection.getId(),
                id -> new SessionContext(connection, config, connect(config)));
        // 如果配置变化（比如重新打开窗口用了新配置），更新配置以便重连使用
        ctx.config = config;

        ActiveForward existing = ctx.forwards.get(spec.name());
        if (existing != null) {
            existing.handle.close();
        }

        PortForward handle = tryOpenForward(ctx, spec, 0);
        ctx.forwards.put(spec.name(), new ActiveForward(spec, handle));
        log.info("Background forward started: {} [{}] on {} bound port {}",
                spec.name(), spec.type(), connection.getHost(), handle.boundPort());
        return handle.boundPort();
    }

    @Override
    public void stopForward(long connectionId, String ruleName) {
        SessionContext ctx = sessions.get(connectionId);
        if (ctx == null) {
            return;
        }
        ActiveForward forward = ctx.forwards.remove(ruleName);
        if (forward != null) {
            forward.handle.close();
            log.info("Background forward stopped: {} (connection {})", ruleName, connectionId);
        }
        if (ctx.forwards.isEmpty()) {
            sessions.remove(connectionId);
            closeSession(ctx);
        }
    }

    @Override
    public void stopAll(long connectionId) {
        SessionContext ctx = sessions.remove(connectionId);
        if (ctx == null) {
            return;
        }
        for (ActiveForward forward : ctx.forwards.values()) {
            forward.handle.close();
        }
        ctx.forwards.clear();
        closeSession(ctx);
        log.info("All background forwards stopped for connection {}", connectionId);
    }

    @Override
    public void stopAll() {
        for (SessionContext ctx : sessions.values()) {
            for (ActiveForward forward : ctx.forwards.values()) {
                forward.handle.close();
            }
            ctx.forwards.clear();
            closeSession(ctx);
        }
        sessions.clear();
        log.info("All background forwards stopped");
    }

    @Override
    public List<ActiveForwardInfo> getActiveForwards(long connectionId) {
        List<ActiveForwardInfo> list = new ArrayList<>();
        SessionContext ctx = sessions.get(connectionId);
        if (ctx == null) {
            return list;
        }
        for (ActiveForward forward : ctx.forwards.values()) {
            list.add(toInfo(ctx.connection, forward));
        }
        return list;
    }

    @Override
    public List<ActiveForwardInfo> getActiveForwards() {
        List<ActiveForwardInfo> list = new ArrayList<>();
        for (SessionContext ctx : sessions.values()) {
            for (ActiveForward forward : ctx.forwards.values()) {
                list.add(toInfo(ctx.connection, forward));
            }
        }
        return list;
    }

    private PortForward tryOpenForward(SessionContext ctx, PortForwardSpec spec, int retryCount) {
        try {
            return ctx.ssh.openForward(spec);
        } catch (RuntimeException ex) {
            if (retryCount == 0 && isForwarderClosed(ex)) {
                log.info("Forwarder closed for {} on {}, reconnecting", spec.name(), ctx.connection.getHost());
                SshSession oldSession = ctx.ssh;
                ctx.ssh = connect(ctx.config);
                closeSession(oldSession);
                return tryOpenForward(ctx, spec, retryCount + 1);
            }
            throw ex;
        }
    }

    private SshSession connect(SshConnectionConfig config) {
        return sshService.connect(config);
    }

    private void closeSession(SessionContext ctx) {
        closeSession(ctx.ssh);
    }

    private void closeSession(SshSession session) {
        try {
            session.close();
        } catch (RuntimeException e) {
            log.warn("Error closing background SSH session: {}", e.getMessage());
        }
    }

    private boolean isForwarderClosed(RuntimeException ex) {
        String msg = ex.getMessage();
        return msg != null && msg.contains("TcpipForwarder is closed or closing");
    }

    private ActiveForwardInfo toInfo(Connection connection, ActiveForward forward) {
        PortForwardSpec spec = forward.spec;
        int port = forward.handle.boundPort();
        return new ActiveForwardInfo(
                connection.getId(),
                connection.getName() != null ? connection.getName() : connection.getHost(),
                spec.name(),
                spec.type(),
                spec.bindHost(),
                port,
                spec.destHost(),
                spec.destPort());
    }

    private static final class SessionContext {
        private final Connection connection;
        private SshConnectionConfig config;
        private SshSession ssh;
        private final Map<String, ActiveForward> forwards = new ConcurrentHashMap<>();

        SessionContext(Connection connection, SshConnectionConfig config, SshSession ssh) {
            this.connection = connection;
            this.config = config;
            this.ssh = ssh;
        }
    }

    private static final class ActiveForward {
        private final PortForwardSpec spec;
        private final PortForward handle;

        ActiveForward(PortForwardSpec spec, PortForward handle) {
            this.spec = spec;
            this.handle = handle;
        }
    }
}
