package nurgling.widgets;

import haven.Coord;

final class NSettingsLayout {
    final Coord sidebarSize;
    final Coord panelPosition;
    final Coord panelSize;
    final Coord saveButton;
    final Coord cancelButton;
    final Coord backButton;

    private NSettingsLayout(Coord sidebarSize, Coord panelPosition, Coord panelSize,
                            Coord saveButton, Coord cancelButton, Coord backButton) {
        this.sidebarSize = sidebarSize;
        this.panelPosition = panelPosition;
        this.panelSize = panelSize;
        this.saveButton = saveButton;
        this.cancelButton = cancelButton;
        this.backButton = backButton;
    }

    static NSettingsLayout calculate(Coord size, int sidebarX, int contentTop,
                                     Coord buttonSize, boolean hasBack, int margin) {
        int footerY = Math.max(contentTop + margin, size.y - margin - buttonSize.y);
        int saveX = Math.max(margin, size.x - margin - buttonSize.x);
        int cancelX = Math.max(margin, saveX - margin - buttonSize.x);
        Coord back = hasBack
                ? Coord.of(Math.max(margin, cancelX - margin - buttonSize.x), footerY)
                : null;
        Coord sidebar = Coord.of(
                Math.max(1, sidebarX - margin),
                Math.max(1, footerY - (margin * 2)));
        Coord panel = Coord.of(
                Math.max(1, size.x - sidebarX - margin),
                Math.max(1, footerY - contentTop - margin));
        return new NSettingsLayout(
                sidebar,
                Coord.of(sidebarX, contentTop),
                panel,
                Coord.of(saveX, footerY),
                Coord.of(cancelX, footerY),
                back);
    }
}
