package nurgling.feedback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FeedbackSubmission {
    private final String reportId;
    private final FeedbackType type;
    private final String subject;
    private final String description;
    private final String discordContact;
    private final List<FeedbackAttachment> attachments;

    public FeedbackSubmission(String reportId, FeedbackType type, String subject, String description,
                              List<FeedbackAttachment> attachments) {
        this(reportId, type, subject, description, "", attachments);
    }

    public FeedbackSubmission(String reportId, FeedbackType type, String subject, String description,
                              String discordContact, List<FeedbackAttachment> attachments) {
        if(reportId == null || reportId.trim().isEmpty())
            throw new IllegalArgumentException("reportId");
        this.reportId = reportId;
        this.type = type == null ? FeedbackType.BUG : type;
        this.subject = subject == null ? "" : subject;
        this.description = description == null ? "" : description;
        this.discordContact = discordContact == null ? "" : discordContact;
        this.attachments = Collections.unmodifiableList(new ArrayList<>(attachments));
    }

    public String reportId() { return reportId; }
    public FeedbackType type() { return type; }
    public String subject() { return subject; }
    public String description() { return description; }
    public String discordContact() { return discordContact; }
    public List<FeedbackAttachment> attachments() { return attachments; }
}
