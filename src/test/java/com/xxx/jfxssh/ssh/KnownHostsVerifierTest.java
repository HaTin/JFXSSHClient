package com.xxx.jfxssh.ssh;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnownHostsVerifierTest {

    private Map<String, String> backing;
    private HostKeyStore store;
    private PublicKey keyA;
    private PublicKey keyB;
    private final InetSocketAddress addr = new InetSocketAddress("example.com", 22);

    private static PublicKey rsaKey() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        return kp.getPublic();
    }

    @BeforeEach
    void setUp() throws Exception {
        backing = new HashMap<>();
        store = new HostKeyStore() {
            @Override
            public Optional<String> find(String host, int port) {
                return Optional.ofNullable(backing.get(host + ":" + port));
            }

            @Override
            public void save(String host, int port, String fingerprint) {
                backing.put(host + ":" + port, fingerprint);
            }
        };
        keyA = rsaKey();
        keyB = rsaKey();
    }

    private KnownHostsVerifier verifier(HostKeyPrompt prompt, boolean enabled) {
        return new KnownHostsVerifier(store, prompt, () -> enabled);
    }

    @Test
    void firstUseRecordsAndAccepts() {
        KnownHostsVerifier v = verifier((h, p, s, r) -> false, true);
        assertTrue(v.verifyServerKey(null, addr, keyA));
        assertEquals(KnownHostsVerifier.fingerprint(keyA), backing.get("example.com:22"));
    }

    @Test
    void sameKeyAcceptedWithoutPrompt() {
        boolean[] prompted = {false};
        KnownHostsVerifier v = verifier((h, p, s, r) -> {
            prompted[0] = true;
            return false;
        }, true);
        assertTrue(v.verifyServerKey(null, addr, keyA));
        assertTrue(v.verifyServerKey(null, addr, keyA));
        assertFalse(prompted[0]);
    }

    @Test
    void changedKeyRejectedWhenUserCancels() {
        verifier((h, p, s, r) -> false, true).verifyServerKey(null, addr, keyA); // record A
        KnownHostsVerifier v = verifier((h, p, s, r) -> false, true);
        assertFalse(v.verifyServerKey(null, addr, keyB));
        // store unchanged
        assertEquals(KnownHostsVerifier.fingerprint(keyA), backing.get("example.com:22"));
    }

    @Test
    void changedKeyAcceptedAndUpdatedWhenUserContinues() {
        verifier((h, p, s, r) -> false, true).verifyServerKey(null, addr, keyA);
        KnownHostsVerifier v = verifier((h, p, s, r) -> true, true);
        assertTrue(v.verifyServerKey(null, addr, keyB));
        assertEquals(KnownHostsVerifier.fingerprint(keyB), backing.get("example.com:22"));
    }

    @Test
    void disabledAcceptsAllWithoutRecording() {
        KnownHostsVerifier v = verifier((h, p, s, r) -> false, false);
        assertTrue(v.verifyServerKey(null, addr, keyA));
        assertTrue(backing.isEmpty());
    }
}
