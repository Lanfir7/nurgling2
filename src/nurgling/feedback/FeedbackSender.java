package nurgling.feedback;

import java.io.IOException;

public interface FeedbackSender {
    void sendMessage(String text) throws IOException;
    void sendPhoto(byte[] png, String filename, String caption) throws IOException;
}
