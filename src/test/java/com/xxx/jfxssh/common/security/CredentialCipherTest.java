package com.xxx.jfxssh.common.security;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CredentialCipherTest {

    private static SecretKey key(String password, byte[] salt) {
        return CredentialCipher.deriveKey(password.toCharArray(), salt, 10_000);
    }

    @Test
    void encryptDecryptRoundTrip() {
        byte[] salt = CredentialCipher.randomSalt();
        SecretKey k = key("master", salt);
        String blob = CredentialCipher.encrypt(k, "s3cret-pw");
        assertNotEquals("s3cret-pw", blob);
        assertEquals("s3cret-pw", CredentialCipher.decrypt(k, blob));
    }

    @Test
    void wrongKeyFailsAuthentication() {
        byte[] salt = CredentialCipher.randomSalt();
        String blob = CredentialCipher.encrypt(key("right", salt), "data");
        SecretKey wrong = key("wrong", salt);
        assertThrows(CryptoException.class, () -> CredentialCipher.decrypt(wrong, blob));
    }

    @Test
    void sameInputProducesDifferentCiphertext() {
        SecretKey k = key("master", CredentialCipher.randomSalt());
        assertNotEquals(CredentialCipher.encrypt(k, "x"), CredentialCipher.encrypt(k, "x"));
    }

    @Test
    void saltLengthIsSixteen() {
        assertEquals(CredentialCipher.SALT_BYTES, CredentialCipher.randomSalt().length);
    }
}
