package nurgling.widgets.nsettings;

/**
 * Unscaled two-column layout for Item Q / Stack Q quality-threshold tabs.
 */
final class ItemOverlayQualityTabLayout {
    final int tabWidth;
    final int leftColumnWidth;
    final int rightColumnX;
    final int listWidth;
    final int addButtonInset;

    private ItemOverlayQualityTabLayout(int tabWidth, int leftColumnWidth,
                                        int rightColumnX, int listWidth,
                                        int addButtonInset) {
        this.tabWidth = tabWidth;
        this.leftColumnWidth = leftColumnWidth;
        this.rightColumnX = rightColumnX;
        this.listWidth = listWidth;
        this.addButtonInset = addButtonInset;
    }

    static ItemOverlayQualityTabLayout forTab() {
        int tabWidth = 560;
        int leftColumnWidth = tabWidth / 2;
        return new ItemOverlayQualityTabLayout(
                tabWidth,
                leftColumnWidth,
                leftColumnWidth + 10,
                210,
                18);
    }

    int addButtonX() {
        return rightColumnX + listWidth - addButtonInset;
    }

    int addButtonRight(int buttonWidth) {
        return addButtonX() + buttonWidth;
    }
}
