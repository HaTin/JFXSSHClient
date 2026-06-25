package com.xxx.jfxssh.service;

import com.xxx.jfxssh.common.AppPaths;
import com.xxx.jfxssh.storage.Database;
import com.xxx.jfxssh.storage.repository.SettingsRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsServiceTest {

    private SettingsService service(Path dir) {
        Database db = new Database(new AppPaths(dir));
        db.init();
        return new SettingsServiceImpl(new SettingsRepositoryImpl(db));
    }

    @Test
    void getMissingReturnsEmpty(@TempDir Path dir) {
        assertTrue(service(dir).get("no.such.key").isEmpty());
    }

    @Test
    void setThenGet(@TempDir Path dir) {
        SettingsService s = service(dir);
        s.set("theme", "dark");
        assertEquals("dark", s.get("theme").orElseThrow());
    }

    @Test
    void setOverwrites(@TempDir Path dir) {
        SettingsService s = service(dir);
        s.set("theme", "dark");
        s.set("theme", "light");
        assertEquals("light", s.get("theme").orElseThrow());
    }
}
