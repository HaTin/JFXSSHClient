package com.xxx.jfxssh.common.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 凭据加密原语（见 docs/ARCHITECTURE.md 加密方案）。
 *
 * <p>主密码经 PBKDF2-HMAC-SHA256 派生 256-bit 密钥，使用 AES-256-GCM 加密，
 * 落库格式为 Base64( iv ‖ ciphertext ‖ tag )。全部基于 JDK 原生 javax.crypto，
 * 无 native 依赖。</p>
 */
public final class CredentialCipher {

    /** KDF 算法。 */
    public static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";

    /** KDF 迭代次数（参考 OWASP）。 */
    public static final int KDF_ITERATIONS = 600_000;

    /** 盐长度（字节）。 */
    public static final int SALT_BYTES = 16;

    private static final int KEY_BITS = 256;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CredentialCipher() {
    }

    /**
     * @return 新的随机盐
     */
    public static byte[] randomSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * 由主密码派生 AES 密钥。
     *
     * @param password   主密码
     * @param salt       盐
     * @param iterations 迭代次数
     * @return AES 密钥
     */
    public static SecretKey deriveKey(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Key derivation failed", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * 加密。
     *
     * @param key       AES 密钥
     * @param plaintext 明文
     * @return Base64( iv ‖ ciphertext ‖ tag )
     */
    public static String encrypt(SecretKey key, String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Encryption failed", e);
        }
    }

    /**
     * 解密。GCM 校验失败（密钥错误或数据被篡改）会抛出 {@link CryptoException}。
     *
     * @param key  AES 密钥
     * @param blob Base64( iv ‖ ciphertext ‖ tag )
     * @return 明文
     */
    public static String decrypt(SecretKey key, String blob) {
        try {
            byte[] in = Base64.getDecoder().decode(blob);
            byte[] iv = Arrays.copyOfRange(in, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(in, IV_BYTES, in.length);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Decryption failed", e);
        }
    }
}
