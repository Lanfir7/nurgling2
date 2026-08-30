package nurgling;

final class QuickMapMarkerGesture {
    private QuickMapMarkerGesture() {
    }

    static boolean matches(int button, boolean alt, boolean ctrl, boolean shift) {
        return button == 2 && alt && !ctrl && !shift;
    }
}
