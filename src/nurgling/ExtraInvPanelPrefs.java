package nurgling;

public final class ExtraInvPanelPrefs {
    private ExtraInvPanelPrefs() {}

    public static final class Snapshot {
        public final int panelState;
        public final NInventory.Grouping grouping;
        public final NInventory.DisplayType displayType;
        public final String minQualityText;

        public Snapshot(int panelState, NInventory.Grouping grouping,
                        NInventory.DisplayType displayType, String minQualityText) {
            this.panelState = clampState(panelState);
            this.grouping = grouping != null ? grouping : NInventory.Grouping.NONE;
            this.displayType = displayType != null ? displayType : NInventory.DisplayType.Name;
            this.minQualityText = minQualityText != null ? minQualityText : "";
        }
    }

    public static Snapshot defaults() {
        return new Snapshot(NInventory.PANEL_CLOSED, NInventory.Grouping.NONE,
                NInventory.DisplayType.Name, "");
    }

    public static Snapshot load() {
        return new Snapshot(
                parsePanelState(NConfig.get(NConfig.Key.extraInvPanelState)),
                parseGrouping(NConfig.get(NConfig.Key.extraInvGrouping)),
                parseDisplayType(NConfig.get(NConfig.Key.extraInvDisplayType)),
                parseMinQualityText(NConfig.get(NConfig.Key.extraInvMinQuality)));
    }

    public static void save(Snapshot snap) {
        if (snap == null) {
            snap = defaults();
        }
        savePanelState(snap.panelState);
        saveGrouping(snap.grouping);
        saveDisplayType(snap.displayType);
        saveMinQualityText(snap.minQualityText);
    }

    public static void savePanelState(int state) {
        NConfig.set(NConfig.Key.extraInvPanelState, clampState(state));
    }

    public static void saveGrouping(NInventory.Grouping grouping) {
        NInventory.Grouping val = grouping != null ? grouping : NInventory.Grouping.NONE;
        NConfig.set(NConfig.Key.extraInvGrouping, val.name());
    }

    public static void saveDisplayType(NInventory.DisplayType displayType) {
        NInventory.DisplayType val = displayType != null ? displayType : NInventory.DisplayType.Name;
        NConfig.set(NConfig.Key.extraInvDisplayType, val.name());
    }

    public static void saveMinQualityText(String text) {
        NConfig.set(NConfig.Key.extraInvMinQuality, text != null ? text : "");
    }

    static int parsePanelState(Object raw) {
        if (raw instanceof Number) {
            return clampState(((Number) raw).intValue());
        }
        if (raw instanceof Boolean) {
            return ((Boolean) raw) ? NInventory.PANEL_EXPANDED : NInventory.PANEL_CLOSED;
        }
        return NInventory.PANEL_CLOSED;
    }

    static NInventory.Grouping parseGrouping(Object raw) {
        if (raw instanceof String) {
            try {
                return NInventory.Grouping.valueOf((String) raw);
            } catch (IllegalArgumentException ignored) {
                return NInventory.Grouping.NONE;
            }
        }
        return NInventory.Grouping.NONE;
    }

    static NInventory.DisplayType parseDisplayType(Object raw) {
        if (raw instanceof String) {
            try {
                return NInventory.DisplayType.valueOf((String) raw);
            } catch (IllegalArgumentException ignored) {
                return NInventory.DisplayType.Name;
            }
        }
        return NInventory.DisplayType.Name;
    }

    static String parseMinQualityText(Object raw) {
        return raw instanceof String ? (String) raw : "";
    }

    static int clampState(int state) {
        if (state < NInventory.PANEL_CLOSED || state > NInventory.PANEL_EXPANDED) {
            return NInventory.PANEL_CLOSED;
        }
        return state;
    }
}
