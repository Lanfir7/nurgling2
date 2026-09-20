package nurgling.widgets;

final class SpecialisationButtonLayout {
    private SpecialisationButtonLayout() {
    }

    static int[] rightAlignedPositions(int rowWidth, int rightMargin, int gap, int... buttonWidths) {
        int[] positions = new int[buttonWidths.length];
        int right = Math.max(0, rowWidth - rightMargin);
        for (int index = buttonWidths.length - 1; index >= 0; index--) {
            int width = Math.max(0, buttonWidths[index]);
            positions[index] = Math.max(0, right - width);
            right = positions[index] - gap;
        }
        return positions;
    }
}
