package org.lifstools.jgoslin.webapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.lifstools.jgoslin.domain.LipidFaBondType;
import org.lifstools.jgoslin.domain.LipidLevel;
import org.lifstools.jgoslin.webapp.domain.FailureExplanation.Hint;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * The result and input pages look up enum names as message keys: levels and bond types ({@code #{${r.level()}}}),
 * and the failure hints ({@code fix.hint.<hint>}). Every value needs a key in the English bundle, which German
 * falls back to (the German level and bond type names are deferred: "German is partial"), and every hint needs a
 * German key too.
 */
class MessageKeysTest {

    private static List<String> enumKeys() {
        List<String> keys = new ArrayList<>();
        for (LipidLevel level : LipidLevel.values()) {
            keys.add(level.name());
        }
        for (LipidFaBondType type : LipidFaBondType.values()) {
            keys.add(type.name());
        }
        keys.addAll(hintKeys());
        return keys;
    }

    private static List<String> hintKeys() {
        List<String> keys = new ArrayList<>();
        for (Hint hint : Hint.values()) {
            keys.add("fix.hint." + hint.name().toLowerCase(Locale.ROOT));
        }
        return keys;
    }

    private static Properties bundle(String file) throws Exception {
        Properties p = new Properties();
        try (var in = new InputStreamReader(MessageKeysTest.class.getResourceAsStream("/i18n/tool/" + file),
                StandardCharsets.UTF_8)) {
            p.load(in);
        }
        return p;
    }

    @Test
    void everyEnumValueTheTemplatesLookUpHasAnEnglishKey() throws Exception {
        assertThat(bundle("messages.properties").keySet()).containsAll(enumKeys());
    }

    @Test
    void everyHintHasAGermanKey() throws Exception {
        assertThat(bundle("messages_de.properties").keySet()).containsAll(hintKeys());
    }

    @Test
    void everyKeyResolvesInBothLanguages() {
        // as configured in application.properties: spring.messages.*
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasenames("i18n.messages", "i18n.tool.messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        for (Locale locale : new Locale[]{Locale.US, Locale.GERMAN}) {
            for (String key : enumKeys()) {
                assertThatCode(() -> messages.getMessage(key, new Object[]{1, "x", 20}, locale))
                        .as(locale + " " + key).doesNotThrowAnyException();
            }
        }
    }
}
