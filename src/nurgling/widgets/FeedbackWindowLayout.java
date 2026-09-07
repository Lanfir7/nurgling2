package nurgling.widgets;

import haven.Coord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FeedbackWindowLayout {
    private static final int GAP = 8;
    private static final int THUMBNAIL = 96;
    private static final int DESCRIPTION = 180;
    private static final int FIELD = 30;

    private final int typeY;
    private final int subjectY;
    private final int discordY;
    private final int descriptionY;
    private final int attachmentsY;
    private final int statusY;
    private final int sendY;
    private final int thumbnailSize;
    private final List<Coord> thumbnails;

    private FeedbackWindowLayout(int typeY, int subjectY, int discordY, int descriptionY, int attachmentsY,
                                 int statusY, int sendY, int thumbnailSize, List<Coord> thumbnails) {
        this.typeY = typeY;
        this.subjectY = subjectY;
        this.discordY = discordY;
        this.descriptionY = descriptionY;
        this.attachmentsY = attachmentsY;
        this.statusY = statusY;
        this.sendY = sendY;
        this.thumbnailSize = thumbnailSize;
        this.thumbnails = Collections.unmodifiableList(thumbnails);
    }

    public static FeedbackWindowLayout calculate(int width, int thumbnailCount) {
        double scale = width / 560.0;
        int gap = scaled(GAP, scale);
        int field = scaled(FIELD, scale);
        int thumbnail = scaled(THUMBNAIL, scale);
        int typeY = scaled(20, scale);
        int subjectY = typeY + field + scaled(20, scale);
        int discordY = subjectY + field + scaled(20, scale);
        int descriptionY = discordY + field + scaled(20, scale);
        int attachmentsY = descriptionY + scaled(DESCRIPTION, scale) + scaled(28, scale);
        int statusY = attachmentsY + thumbnail + field + gap;
        int sendY = statusY + field;
        int count = Math.max(0, Math.min(3, thumbnailCount));
        int slotWidth = (width - (gap * 2)) / 3;
        List<Coord> thumbnails = new ArrayList<>(count);
        for(int index = 0; index < count; index++)
            thumbnails.add(Coord.of(index * (slotWidth + gap), attachmentsY));
        return new FeedbackWindowLayout(typeY, subjectY, discordY, descriptionY, attachmentsY,
                statusY, sendY, thumbnail, thumbnails);
    }

    private static int scaled(int value, double scale) {
        return Math.max(1, (int)Math.round(value * scale));
    }

    public int typeY() { return typeY; }
    public int subjectY() { return subjectY; }
    public int discordY() { return discordY; }
    public int descriptionY() { return descriptionY; }
    public int attachmentsY() { return attachmentsY; }
    public int statusY() { return statusY; }
    public int sendY() { return sendY; }
    public int thumbnailSize() { return thumbnailSize; }
    public List<Coord> thumbnails() { return thumbnails; }
}
