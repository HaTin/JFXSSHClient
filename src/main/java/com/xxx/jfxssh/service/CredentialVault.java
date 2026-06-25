package com.xxx.jfxssh.service;

import com.xxx.jfxssh.common.security.CredentialCipher;
import com.xxx.jfxssh.common.security.CryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * 凭据保险库：用主密码保护连接密码等敏感信息（见 docs/ARCHITECTURE.md 加密方案）。
 *
 * <p>主密码不入库；首次设置时生成盐并存校验块，解锁时派生密钥并用 GCM 校验。
 * 派生密钥仅驻内存（解锁期间），锁定 / 退出即清除。KDF 参数、盐、校验块存于
 * settings 表（{@link SettingsService}）。</p>
 */
public final class CredentialVault {

    private static final Logger log = LoggerFactory.getLogger(CredentialVault.class);

    private static final String SENTINEL = "JFXSSH-VAULT-OK";
    private static final String KEY_KDF = "security.kdf";
    private static final String KEY_ITERATIONS = "security.kdf.iterations";
    private static final String KEY_SALT = "security.kdf.salt";
    private static final String KEY_VERIFIER = "security.verifier";

    private final SettingsService settings;
    private SecretKey key;

    /**
     * @param settings 设置服务（构造器注入）
     */
    public CredentialVault(SettingsService settings) {
        this.settings = settings;
    }

    /**
     * @return 是否已设置主密码
     */
    public boolean isInitialized() {
        return settings.get(KEY_VERIFIER).isPresent();
    }

    /**
     * @return 当前是否已解锁（内存中持有密钥）
     */
    public boolean isUnlocked() {
        return key != null;
    }

    /**
     * 首次设置主密码：生成盐、派生密钥、写入校验块，并进入解锁态。
     *
     * @param master 主密码
     */
    public void initialize(char[] master) {
        byte[] salt = CredentialCipher.randomSalt();
        SecretKey derived = CredentialCipher.deriveKey(master, salt, CredentialCipher.KDF_ITERATIONS);
        String verifier = CredentialCipher.encrypt(derived, SENTINEL);
        settings.set(KEY_KDF, CredentialCipher.KDF_ALGORITHM);
        settings.set(KEY_ITERATIONS, Integer.toString(CredentialCipher.KDF_ITERATIONS));
        settings.set(KEY_SALT, Base64.getEncoder().encodeToString(salt));
        settings.set(KEY_VERIFIER, verifier);
        this.key = derived;
        log.info("Credential vault initialized");
    }

    /**
     * 用主密码解锁。
     *
     * @param master 主密码
     * @return 成功返回 true，主密码错误返回 false
     */
    public boolean unlock(char[] master) {
        Optional<String> saltB64 = settings.get(KEY_SALT);
        Optional<String> verifier = settings.get(KEY_VERIFIER);
        if (saltB64.isEmpty() || verifier.isEmpty()) {
            return false;
        }
        int iterations = settings.get(KEY_ITERATIONS)
                .map(Integer::parseInt)
                .orElse(CredentialCipher.KDF_ITERATIONS);
        byte[] salt = Base64.getDecoder().decode(saltB64.get());
        SecretKey derived = CredentialCipher.deriveKey(master, salt, iterations);
        try {
            if (SENTINEL.equals(CredentialCipher.decrypt(derived, verifier.get()))) {
                this.key = derived;
                log.info("Credential vault unlocked");
                return true;
            }
            return false;
        } catch (CryptoException e) {
            // GCM 校验失败 = 主密码错误
            return false;
        }
    }

    /**
     * 锁定：清除内存中的密钥。
     */
    public void lock() {
        key = null;
    }

    /**
     * 加密（需已解锁）。
     *
     * @param plaintext 明文
     * @return 密文（Base64）
     */
    public String encrypt(String plaintext) {
        requireUnlocked();
        return CredentialCipher.encrypt(key, plaintext);
    }

    /**
     * 解密（需已解锁）。
     *
     * @param blob 密文（Base64）
     * @return 明文
     */
    public String decrypt(String blob) {
        requireUnlocked();
        return CredentialCipher.decrypt(key, blob);
    }

    private void requireUnlocked() {
        if (key == null) {
            throw new IllegalStateException("Credential vault is locked");
        }
    }

    /**
     * 清除给定密码字符数组（便于调用方用后即清）。
     *
     * @param password 密码字符数组
     */
    public static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
