package com.xxx.jfxssh.common.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 国际化工具（见 docs/I18N.md）。
 *
 * <p>从 {@code /i18n/messages_<locale>.json} 加载文案，按资源 ID 取值。
 * 通过可观察的当前语言属性配合 {@link #tp(String)} 实现 JavaFX 文案的
 * 运行时实时切换，无需重启。</p>
 *
 * <p>取值回退顺序：当前语言 → 默认语言（en）→ 资源 ID 本身（并记 WARN）。</p>
 */
public final class I18n {

    private static final Logger log = LoggerFactory.getLogger(I18n.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RESOURCE_PATTERN = "/i18n/messages_%s.json";

    /** 默认 / 回退语言。 */
    public static final Locale FALLBACK_LOCALE = Locale.ENGLISH;

    /** 英文。 */
    public static final Locale EN = Locale.ENGLISH;

    /** 简体中文（zh_CN）。 */
    public static final Locale ZH_CN = Locale.SIMPLIFIED_CHINESE;

    private static final ObjectProperty<Locale> CURRENT_LOCALE =
            new SimpleObjectProperty<>(FALLBACK_LOCALE);

    private static Map<String, String> messages = Collections.emptyMap();
    private static Map<String, String> fallback = Collections.emptyMap();

    private I18n() {
    }

    /**
     * 初始化并加载初始语言。
     *
     * @param locale 初始语言
     */
    public static void init(Locale locale) {
        fallback = load(FALLBACK_LOCALE);
        setLocale(locale);
    }

    /**
     * 切换当前语言并刷新所有绑定。
     *
     * @param locale 目标语言
     */
    public static void setLocale(Locale locale) {
        Locale target = locale == null ? FALLBACK_LOCALE : locale;
        messages = load(target);
        if (fallback.isEmpty()) {
            fallback = load(FALLBACK_LOCALE);
        }
        CURRENT_LOCALE.set(target);
        log.info("Locale applied: {}", code(target));
    }

    /**
     * @return 当前语言
     */
    public static Locale currentLocale() {
        return CURRENT_LOCALE.get();
    }

    /**
     * @return 当前语言属性（用于监听语言变化）
     */
    public static ObjectProperty<Locale> currentLocaleProperty() {
        return CURRENT_LOCALE;
    }

    /**
     * 取文案。
     *
     * @param key 资源 ID
     * @return 当前语言文案，按回退顺序取值
     */
    public static String t(String key) {
        String value = messages.get(key);
        if (value != null) {
            return value;
        }
        value = fallback.get(key);
        if (value != null) {
            return value;
        }
        log.warn("Missing i18n key: {}", key);
        return key;
    }

    /**
     * 取带占位符的文案（MessageFormat 风格 {@code {0}}）。
     *
     * @param key  资源 ID
     * @param args 占位符参数
     * @return 替换后的文案
     */
    public static String t(String key, Object... args) {
        return MessageFormat.format(t(key), args);
    }

    /**
     * 返回与当前语言绑定的文案，语言切换时自动更新。
     *
     * @param key 资源 ID
     * @return StringBinding
     */
    public static StringBinding tp(String key) {
        return Bindings.createStringBinding(() -> t(key), CURRENT_LOCALE);
    }

    /**
     * 返回与当前语言绑定的带参数文案。
     *
     * @param key  资源 ID
     * @param args 占位符参数
     * @return StringBinding
     */
    public static StringBinding tp(String key, Object... args) {
        return Bindings.createStringBinding(() -> t(key, args), CURRENT_LOCALE);
    }

    /**
     * 语言代码（{@code en} 或 {@code zh_CN}）。
     *
     * @param locale 语言
     * @return 代码字符串
     */
    public static String code(Locale locale) {
        String country = locale.getCountry();
        return country.isEmpty() ? locale.getLanguage() : locale.getLanguage() + "_" + country;
    }

    /**
     * 将语言代码解析为支持的 Locale；不支持时回退默认语言。
     *
     * @param value 语言代码
     * @return Locale
     */
    public static Locale parse(String value) {
        if (value == null) {
            return FALLBACK_LOCALE;
        }
        if (value.startsWith("zh")) {
            return ZH_CN;
        }
        if (value.startsWith("en")) {
            return EN;
        }
        return FALLBACK_LOCALE;
    }

    private static Map<String, String> load(Locale locale) {
        String resource = String.format(RESOURCE_PATTERN, code(locale));
        try (InputStream in = I18n.class.getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("Language file not found: {}", resource);
                return Collections.emptyMap();
            }
            return MAPPER.readValue(in, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (IOException e) {
            log.warn("Failed to load language file: {}", resource, e);
            return Collections.emptyMap();
        }
    }
}
