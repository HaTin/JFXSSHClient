package com.xxx.jfxssh.ssh;

import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * known_hosts 主机密钥校验器（TOFU，见 docs/ARCHITECTURE.md 安全节）。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>校验关闭时：接受全部。</li>
 *   <li>首次连接：记录指纹并放行（信任首次使用）。</li>
 *   <li>指纹一致：放行。</li>
 *   <li>指纹不一致：交由 {@link HostKeyPrompt} 让用户确认；继续则更新记录并放行，
 *       取消则拒绝连接。</li>
 * </ul>
 */
public final class KnownHostsVerifier implements ServerKeyVerifier {

    private static final Logger log = LoggerFactory.getLogger(KnownHostsVerifier.class);

    private final HostKeyStore store;
    private final HostKeyPrompt prompt;
    private final BooleanSupplier enabled;

    /**
     * @param store   主机密钥存储
     * @param prompt  变更确认回调
     * @param enabled 是否启用校验（动态读取，便于设置项即时生效）
     */
    public KnownHostsVerifier(HostKeyStore store, HostKeyPrompt prompt, BooleanSupplier enabled) {
        this.store = store;
        this.prompt = prompt;
        this.enabled = enabled;
    }

    @Override
    public boolean verifyServerKey(ClientSession session, SocketAddress remoteAddress, PublicKey serverKey) {
        if (!enabled.getAsBoolean()) {
            return true;
        }
        String host;
        int port;
        if (remoteAddress instanceof InetSocketAddress isa) {
            host = isa.getHostString();
            port = isa.getPort();
        } else {
            host = String.valueOf(remoteAddress);
            port = 0;
        }

        String fingerprint = fingerprint(serverKey);
        Optional<String> stored = store.find(host, port);

        if (stored.isEmpty()) {
            store.save(host, port, fingerprint);
            log.info("Recorded host key for {}:{} ({})", host, port, fingerprint);
            return true;
        }
        if (stored.get().equals(fingerprint)) {
            return true;
        }

        log.warn("Host key mismatch for {}:{} (stored={}, received={})",
                host, port, stored.get(), fingerprint);
        boolean accept = prompt.onMismatch(host, port, stored.get(), fingerprint);
        if (accept) {
            store.save(host, port, fingerprint);
            log.info("User accepted changed host key for {}:{}", host, port);
            return true;
        }
        return false;
    }

    /**
     * 计算公钥的 SHA-256 指纹（形如 {@code SHA256:base64}）。
     *
     * @param key 公钥
     * @return 指纹
     */
    public static String fingerprint(PublicKey key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
