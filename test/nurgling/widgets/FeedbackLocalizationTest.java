package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackLocalizationTest {
    private static final List<String> REQUIRED = Arrays.asList(
            "feedback.entry", "feedback.title", "feedback.type", "feedback.type.bug",
            "feedback.type.suggestion", "feedback.subject", "feedback.description",
            "feedback.attachments", "feedback.snip", "feedback.snip.instruction",
            "feedback.send", "feedback.cancel", "feedback.retry", "feedback.remove",
            "feedback.preview", "feedback.discard.title", "feedback.discard.question",
            "feedback.discard", "feedback.keep_editing", "feedback.error.subject_required",
            "feedback.error.subject_too_long", "feedback.error.description_required",
            "feedback.error.description_too_long", "feedback.error.configuration",
            "feedback.error.capture", "feedback.error.delivery", "feedback.status.sending",
            "feedback.status.sent");

    @Test
    void englishAndRussianBundlesContainEveryFeedbackKey() throws Exception {
        assertComplete(load("src/lang/messages.properties"));
        assertComplete(load("src/lang/messages_ru.properties"));
    }

    private static void assertComplete(Properties properties) {
        List<String> missing = new ArrayList<>();
        for(String key : REQUIRED)
            if(properties.getProperty(key) == null || properties.getProperty(key).trim().isEmpty())
                missing.add(key);
        assertTrue(missing.isEmpty(), "Missing feedback translations: " + missing);
    }

    private static Properties load(String path) throws Exception {
        Properties properties = new Properties();
        try(InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(Paths.get(path)), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
