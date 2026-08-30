package nurgling.areas;

public enum PileFillDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP;

    public static PileFillDirection fromStored(Object value) {
        if (value == null) return LEFT_TO_RIGHT;
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) return LEFT_TO_RIGHT;
        try {
            return valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return LEFT_TO_RIGHT;
        }
    }
}
