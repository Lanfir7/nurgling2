package haven;

public final class WindowLayering {
    interface Target {
        void drawContent(GOut g);
        void drawForeground(GOut g);
    }

    public interface Overlay {
        void drawOverlay(GOut g, boolean strict);
    }

    static void paint(Target target, GOut g) {
        target.drawContent(g);
        target.drawForeground(g);
    }

    private WindowLayering() {
    }
}
