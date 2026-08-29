package nurgling.widgets;

public interface AdaptiveSettingsPanel {
    void fitToWidth(int width, int columns);

    default boolean ownsVerticalScroll() {
        return false;
    }
}
