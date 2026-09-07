package nurgling.feedback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FeedbackDraft {
    public static final int MAX_SUBJECT = 120;
    public static final int MAX_DESCRIPTION = 3800;
    public static final int MAX_ATTACHMENTS = 3;

    public enum Validation {
        OK,
        SUBJECT_REQUIRED,
        SUBJECT_TOO_LONG,
        DESCRIPTION_REQUIRED,
        DESCRIPTION_TOO_LONG
    }

    private FeedbackType type = FeedbackType.BUG;
    private String subject = "";
    private String description = "";
    private final List<FeedbackAttachment> attachments = new ArrayList<>();

    public FeedbackType type() { return type; }
    public void setType(FeedbackType type) { this.type = type == null ? FeedbackType.BUG : type; }
    public String subject() { return subject; }
    public void setSubject(String subject) { this.subject = subject == null ? "" : subject; }
    public String description() { return description; }
    public void setDescription(String description) { this.description = description == null ? "" : description; }
    public List<FeedbackAttachment> attachments() { return Collections.unmodifiableList(attachments); }

    public Validation validate() {
        String cleanSubject = subject.trim();
        String cleanDescription = description.trim();
        if(cleanSubject.isEmpty()) return Validation.SUBJECT_REQUIRED;
        if(cleanSubject.length() > MAX_SUBJECT) return Validation.SUBJECT_TOO_LONG;
        if(cleanDescription.isEmpty()) return Validation.DESCRIPTION_REQUIRED;
        if(cleanDescription.length() > MAX_DESCRIPTION) return Validation.DESCRIPTION_TOO_LONG;
        return Validation.OK;
    }

    public boolean addAttachment(FeedbackAttachment attachment) {
        if(attachment == null || attachments.size() >= MAX_ATTACHMENTS)
            return false;
        attachments.add(attachment);
        return true;
    }

    public FeedbackAttachment removeAttachment(int index) {
        return attachments.remove(index);
    }

    public boolean dirty() {
        return type != FeedbackType.BUG || !subject.trim().isEmpty() ||
                !description.trim().isEmpty() || !attachments.isEmpty();
    }

    public void clear() {
        type = FeedbackType.BUG;
        subject = "";
        description = "";
        attachments.clear();
    }

    public FeedbackSubmission submission(String reportId) {
        if(validate() != Validation.OK)
            throw new IllegalStateException("Feedback draft is invalid");
        return new FeedbackSubmission(reportId, type, subject.trim(), description.trim(), attachments);
    }
}
