package nurgling.feedback;

public final class FeedbackFormatter {
    private FeedbackFormatter() {
    }

    public static String message(FeedbackSubmission submission) {
        return "Report: " + submission.reportId() + "\n"
                + "Type: " + submission.type().name() + "\n"
                + "Subject: " + submission.subject() + "\n\n"
                + submission.description();
    }

    public static String photoCaption(FeedbackSubmission submission, int index) {
        return submission.reportId() + " — screenshot " + (index + 1) + "/" + submission.attachments().size();
    }
}
