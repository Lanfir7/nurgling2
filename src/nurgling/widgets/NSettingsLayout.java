package nurgling.widgets;

import haven.Coord;
import haven.UI;

final class NSettingsLayout {
    final Coord sidebarPosition;
    final Coord sidebarSize;
    final Coord panelPosition;
    final Coord panelSize;
    final int footerTop;
    final Coord saveButton;
    final Coord cancelButton;
    final Coord backButton;
    final Coord footerMaskPosition;
    final Coord footerMaskSize;
    final boolean compact;
    final int columns;

    private NSettingsLayout(Coord sidebarPosition, Coord sidebarSize,
                            Coord panelPosition, Coord panelSize, int footerTop,
                            Coord saveButton, Coord cancelButton, Coord backButton,
                            Coord footerMaskPosition, Coord footerMaskSize,
                            boolean compact, int columns) {
        this.sidebarPosition = sidebarPosition;
        this.sidebarSize = sidebarSize;
        this.panelPosition = panelPosition;
        this.panelSize = panelSize;
        this.footerTop = footerTop;
        this.saveButton = saveButton;
        this.cancelButton = cancelButton;
        this.backButton = backButton;
        this.footerMaskPosition = footerMaskPosition;
        this.footerMaskSize = footerMaskSize;
        this.compact = compact;
        this.columns = columns;
    }

    static NSettingsLayout calculate(Coord size, int preferredSidebarWidth,
                                     Coord buttonSize, boolean hasBack, int margin) {
        int footerTop = Math.max(margin, size.y - margin - buttonSize.y);
        boolean compact = size.x < UI.scale(680);
        int sidebarWidth = compact
                ? Math.max(UI.scale(150), Math.min(preferredSidebarWidth, size.x / 3))
                : preferredSidebarWidth;
        int panelX = margin + sidebarWidth + margin;
        Coord panelSize = Coord.of(
                Math.max(1, size.x - panelX - margin),
                Math.max(1, footerTop - (margin * 2)));
        int saveX = Math.max(margin, size.x - margin - buttonSize.x);
        int cancelX = Math.max(margin, saveX - margin - buttonSize.x);
        Coord back = hasBack
                ? Coord.of(Math.max(margin, cancelX - margin - buttonSize.x), footerTop)
                : null;
        int footerMaskTop = Math.max(0, footerTop - margin);
        return new NSettingsLayout(
                Coord.of(margin, margin),
                Coord.of(sidebarWidth, panelSize.y),
                Coord.of(panelX, margin),
                panelSize,
                footerTop,
                Coord.of(saveX, footerTop),
                Coord.of(cancelX, footerTop),
                back,
                Coord.of(0, footerMaskTop),
                Coord.of(size.x, Math.max(0, size.y - footerMaskTop)),
                compact,
                compact ? 1 : 2);
    }

    static NSettingsLayout calculate(Coord size, int sidebarX, int contentTop,
                                     Coord buttonSize, boolean hasBack, int margin) {
        int footerTop = Math.max(contentTop + margin, size.y - margin - buttonSize.y);
        int saveX = Math.max(margin, size.x - margin - buttonSize.x);
        int cancelX = Math.max(margin, saveX - margin - buttonSize.x);
        Coord back = hasBack
                ? Coord.of(Math.max(margin, cancelX - margin - buttonSize.x), footerTop)
                : null;
        boolean compact = size.x < UI.scale(680);
        int footerMaskTop = Math.max(0, footerTop - margin);
        return new NSettingsLayout(
                Coord.of(margin, margin),
                Coord.of(Math.max(1, sidebarX - margin),
                        Math.max(1, footerTop - (margin * 2))),
                Coord.of(sidebarX, contentTop),
                Coord.of(Math.max(1, size.x - sidebarX - margin),
                        Math.max(1, footerTop - contentTop - margin)),
                footerTop,
                Coord.of(saveX, footerTop),
                Coord.of(cancelX, footerTop),
                back,
                Coord.of(0, footerMaskTop),
                Coord.of(size.x, Math.max(0, size.y - footerMaskTop)),
                compact,
                compact ? 1 : 2);
    }
}
