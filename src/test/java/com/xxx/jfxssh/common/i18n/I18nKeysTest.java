package com.xxx.jfxssh.common.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class I18nKeysTest {

    private Map<String, String> load(String resource) throws Exception {
        try (InputStream in = I18nKeysTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing resource " + resource);
            return new ObjectMapper().readValue(in, new TypeReference<LinkedHashMap<String, String>>() {
            });
        }
    }

    @Test
    void englishAndChineseHaveSameKeys() throws Exception {
        Set<String> en = new TreeSet<>(load("/i18n/messages_en.json").keySet());
        Set<String> zh = new TreeSet<>(load("/i18n/messages_zh_CN.json").keySet());
        assertEquals(en, zh, "en/zh language files must define the same keys");
    }

    @Test
    void essentialKeysResolve() {
        I18n.init(I18n.EN);
        for (String key : new String[]{"button.ok", "button.cancel", "dialog.connection.host",
                "app.title", "menu.file", "common.error"}) {
            assertNotEquals(key, I18n.t(key), "key not translated: " + key);
        }
    }
}
