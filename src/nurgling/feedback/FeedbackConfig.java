package nurgling.feedback;

import haven.Utils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class FeedbackConfig {
    public static final String TOKEN_KEY = "nurgling.feedback.bot-token";
    public static final String CHAT_ID_KEY = "nurgling.feedback.chat-id";

    private final String botToken;
    private final String chatId;

    FeedbackConfig(String botToken, String chatId) {
        this.botToken = clean(botToken);
        this.chatId = clean(chatId);
    }

    public static FeedbackConfig resolve(Properties system, Properties file) {
        return new FeedbackConfig(first(system, file, TOKEN_KEY), first(system, file, CHAT_ID_KEY));
    }

    public static FeedbackConfig load() {
        return resolve(System.getProperties(), loadNeighboringFile());
    }

    public String botToken() { return botToken; }
    public String chatId() { return chatId; }

    public boolean configured() {
        return !botToken.isEmpty() && chatId.matches("-?[0-9]+");
    }

    @Override
    public String toString() {
        return "FeedbackConfig{configured=" + configured() + "}";
    }

    private static String first(Properties preferred, Properties fallback, String key) {
        String value = preferred == null ? null : preferred.getProperty(key);
        if(value == null || value.trim().isEmpty())
            value = fallback == null ? null : fallback.getProperty(key);
        return clean(value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static Properties loadNeighboringFile() {
        Properties values = new Properties();
        try {
            Path source = Utils.srcpath(FeedbackConfig.class);
            Path file = Files.isDirectory(source)
                    ? Paths.get("feedback.properties")
                    : source.resolveSibling("feedback.properties");
            if(!Files.exists(file))
                return values;
            try(InputStream input = Files.newInputStream(file)) {
                values.load(input);
            }
        } catch(Exception ignored) {
            // Optional configuration failures are represented by configured() == false.
        }
        return values;
    }
}
