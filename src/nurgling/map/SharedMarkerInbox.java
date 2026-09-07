package nurgling.map;

/** Remembers the clipboard value already handled by one game session. */
public final class SharedMarkerInbox {
    private String lastClipboard;

    public void ignore(String clipboard) {
        lastClipboard = clipboard;
    }

    public SharedMarkerCode.Marker offer(String clipboard, String world) {
        if (clipboard == null || clipboard.equals(lastClipboard))
            return null;
        lastClipboard = clipboard;
        try {
            SharedMarkerCode.Marker marker = SharedMarkerCode.decode(clipboard);
            return marker.belongsTo(world) ? marker : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
