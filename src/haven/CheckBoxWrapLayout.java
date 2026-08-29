package haven;

final class CheckBoxWrapLayout {
    private CheckBoxWrapLayout() {
    }

    static int labelWidth(int totalWidth, int boxWidth, int gap) {
        return Math.max(1, totalWidth - boxWidth - gap);
    }

    static Coord size(Coord box, Coord label, int gap) {
        return Coord.of(box.x + gap + label.x, Math.max(box.y, label.y));
    }
}
