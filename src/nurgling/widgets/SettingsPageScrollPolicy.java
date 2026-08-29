package nurgling.widgets;

final class SettingsPageScrollPolicy {
    private SettingsPageScrollPolicy() {
    }

    static boolean needsScroll(int contentHeight, int viewportHeight) {
        return contentHeight > viewportHeight;
    }
}
