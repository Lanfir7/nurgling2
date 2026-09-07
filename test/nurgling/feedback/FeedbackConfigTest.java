package nurgling.feedback;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackConfigTest {
    @Test
    void systemPropertiesOverrideNeighboringFile() {
        Properties file = values("file-secret", "100");
        Properties system = values("system-secret", "200");

        FeedbackConfig config = FeedbackConfig.resolve(system, file);

        assertEquals("system-secret", config.botToken());
        assertEquals("200", config.chatId());
        assertTrue(config.configured());
    }

    @Test
    void blankOrMalformedValuesAreNotConfigured() {
        assertFalse(FeedbackConfig.resolve(new Properties(), values(" ", "")).configured());
        assertFalse(FeedbackConfig.resolve(new Properties(), values("secret", "not-a-chat")).configured());
    }

    @Test
    void diagnosticStringDoesNotExposeSecret() {
        FeedbackConfig config = FeedbackConfig.resolve(new Properties(), values("very-private", "100"));

        assertFalse(config.toString().contains("very-private"));
        assertTrue(config.toString().contains("configured=true"));
    }

    private static Properties values(String token, String chat) {
        Properties values = new Properties();
        values.setProperty(FeedbackConfig.TOKEN_KEY, token);
        values.setProperty(FeedbackConfig.CHAT_ID_KEY, chat);
        return values;
    }
}
