package com.xxx.jfxssh.ssh;

import com.xxx.jfxssh.common.AuthType;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;

/**
 * {@link SshService} 的 Apache Mina 实现。
 *
 * <p>内部维护单个 {@link SshClient}（首次使用时启动），每次 connect 建立一个
 * {@link ClientSession} 并完成密码 / 公钥认证。实现 {@link AutoCloseable}，应用
 * 退出时调用 {@link #close()} 停止客户端。</p>
 *
 * <p>服务器主机密钥校验由注入的 {@link ServerKeyVerifier} 决定（known_hosts
 * 校验见 {@link KnownHostsVerifier}）。</p>
 */
public final class MinaSshService implements SshService, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MinaSshService.class);

    private final SshClient client;
    private volatile boolean started;

    /**
     * 创建服务（接受全部主机密钥，便于测试）。
     */
    public MinaSshService() {
        this(AcceptAllServerKeyVerifier.INSTANCE);
    }

    /**
     * @param serverKeyVerifier 主机密钥校验器
     */
    public MinaSshService(ServerKeyVerifier serverKeyVerifier) {
        this.client = SshClient.setUpDefaultClient();
        this.client.setServerKeyVerifier(serverKeyVerifier);
        this.client.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
    }

    @Override
    public SshSession connect(SshConnectionConfig config) {
        ensureStarted();
        applyKeepAlive(config);
        try {
            ClientSession session = client
                    .connect(config.getUsername(), config.getHost(), config.getPort())
                    .verify(config.getConnectTimeout())
                    .getSession();

            applyAuth(session, config);
            session.auth().verify(config.getAuthTimeout());

            log.info("SSH connected: {}@{}:{}", config.getUsername(), config.getHost(), config.getPort());
            return new MinaSshSession(session, config);
        } catch (IOException e) {
            throw new SshConnectException(
                    "Failed to connect " + config.getHost() + ":" + config.getPort(), e);
        }
    }

    @Override
    public boolean test(SshConnectionConfig config) {
        try (SshSession session = connect(config)) {
            return session.isOpen();
        } catch (SshConnectException e) {
            log.warn("SSH test failed for {}:{} - {}", config.getHost(), config.getPort(), e.getMessage());
            return false;
        }
    }

    private synchronized void ensureStarted() {
        if (!started) {
            client.start();
            started = true;
            log.info("SSH client started");
        }
    }

    private void applyAuth(ClientSession session, SshConnectionConfig config) throws IOException {
        if (config.getAuthType() == AuthType.PRIVATE_KEY) {
            String content = config.getPrivateKeyContent();
            if (content != null && !content.isBlank()) {
                session.setKeyIdentityProvider(KeyIdentityProvider.wrapKeyPairs(loadKeyPairs(content, config)));
            } else {
                FileKeyPairProvider provider = new FileKeyPairProvider(Path.of(config.getPrivateKeyPath()));
                String passphrase = config.getPassphrase();
                if (passphrase != null && !passphrase.isBlank()) {
                    provider.setPasswordFinder((ctx, resource, retryIndex) -> passphrase);
                }
                session.setKeyIdentityProvider(provider);
            }
        } else {
            session.addPasswordIdentity(config.getPassword());
        }
    }

    /** 从内存中的私钥内容（PEM/OpenSSH 文本）解析 KeyPair，带口令则解密。 */
    private Iterable<KeyPair> loadKeyPairs(String content, SshConnectionConfig config) throws IOException {
        String passphrase = config.getPassphrase();
        FilePasswordProvider passwordProvider = (passphrase != null && !passphrase.isBlank())
                ? FilePasswordProvider.of(passphrase)
                : FilePasswordProvider.EMPTY;
        try (ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            return SecurityUtils.loadKeyPairIdentities(
                    null, NamedResource.ofName("connection"), in, passwordProvider);
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to parse private key: " + e.getMessage(), e);
        }
    }

    private void applyKeepAlive(SshConnectionConfig config) {
        if (config.getKeepAliveInterval() == null || config.getKeepAliveInterval().isZero()) {
            CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, Duration.ZERO);
            return;
        }
        // 在 connect() 之前设置到 client，新的 ClientSession 才会在构造 ClientConnectionService 时读到
        CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, config.getKeepAliveInterval());
        // 对 OpenSSH 服务器使用更兼容的 keepalive 请求名
        CoreModuleProperties.HEARTBEAT_REQUEST.set(client, "keepalive@openssh.com");
        // 同时开启 TCP keepalive，帮助检测死连接
        CoreModuleProperties.SOCKET_KEEPALIVE.set(client, Boolean.TRUE);
        log.debug("SSH keepalive configured: interval={}", config.getKeepAliveInterval());
    }

    @Override
    public void close() {
        if (started) {
            client.stop();
            started = false;
            log.info("SSH client stopped");
        }
    }
}
