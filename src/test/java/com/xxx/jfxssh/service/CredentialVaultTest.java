package com.xxx.jfxssh.service;

import com.xxx.jfxssh.common.AppPaths;
import com.xxx.jfxssh.storage.Database;
import com.xxx.jfxssh.storage.repository.SettingsRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialVaultTest {

    private SettingsService settings;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        Database db = new Database(new AppPaths(dir));
        db.init();
        settings = new SettingsServiceImpl(new SettingsRepositoryImpl(db));
    }

    @Test
    void initializeThenEncryptDecrypt() {
        CredentialVault vault = new CredentialVault(settings);
        assertFalse(vault.isInitialized());
        assertFalse(vault.isUnlocked());

        vault.initialize("master".toCharArray());
        assertTrue(vault.isInitialized());
        assertTrue(vault.isUnlocked());

        String blob = vault.encrypt("ssh-pw");
        assertEquals("ssh-pw", vault.decrypt(blob));
    }

    @Test
    void unlockPersistsAcrossInstances() {
        CredentialVault first = new CredentialVault(settings);
        first.initialize("master".toCharArray());
        String blob = first.encrypt("ssh-pw");

        CredentialVault second = new CredentialVault(settings);
        assertTrue(second.isInitialized());
        assertFalse(second.isUnlocked());
        assertFalse(second.unlock("wrong".toCharArray()));
        assertTrue(second.unlock("master".toCharArray()));
        assertEquals("ssh-pw", second.decrypt(blob));
    }

    @Test
    void lockedVaultRejectsEncryption() {
        CredentialVault vault = new CredentialVault(settings);
        vault.initialize("master".toCharArray());
        vault.lock();
        assertFalse(vault.isUnlocked());
        assertThrows(IllegalStateException.class, () -> vault.encrypt("x"));
    }
}
