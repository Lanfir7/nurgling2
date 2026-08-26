package nurgling.tools;

import haven.Coord;

public final class FloorOverlayMarkerLogic {
    private FloorOverlayMarkerLogic() {}

    public static Coord srcTile(Coord destTc, Coord tileOffset) {
        return destTc.add(tileOffset);
    }

    public static Coord destToScreen(Coord destTc, Coord tileOffset, Coord dlocTc, float scalef,
                                    float currentScale, Coord hsz, float uiScale) {
        Coord src = srcTile(destTc, tileOffset);
        Coord dlocDiv = dlocTc.div((double) scalef);
        double x = src.x * uiScale * currentScale - dlocDiv.x + hsz.x;
        double y = src.y * uiScale * currentScale - dlocDiv.y + hsz.y;
        return new Coord((int) Math.round(x), (int) Math.round(y));
    }

    public static boolean onScreen(Coord screen, Coord mapSz) {
        return screen.x >= 0 && screen.y >= 0 && screen.x <= mapSz.x && screen.y <= mapSz.y;
    }

    public static boolean matchesSearch(String name, String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return true;
        }
        if (name == null) {
            return false;
        }
        return name.toLowerCase().contains(pattern.trim().toLowerCase());
    }

    public static boolean shouldShowProspecting(boolean showIcons, boolean hideAll) {
        return showIcons && !hideAll;
    }

    public static boolean overlayActive(boolean enabled, long destSegId, long currentSegId) {
        return enabled && destSegId != currentSegId;
    }

    public static boolean hoverHit(Coord mouse, Coord markScreen, int thresholdPx) {
        return mouse.dist(markScreen) < thresholdPx;
    }

    public static int overlayTooltipKind(boolean prospectHit, boolean vanillaHit) {
        if (prospectHit) {
            return 1;
        }
        if (vanillaHit) {
            return 2;
        }
        return 0;
    }
}
