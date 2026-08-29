package nurgling;

import java.util.function.BiConsumer;
import java.util.function.Function;

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
        return new Snapshot(0, NInventory.Grouping.NONE, NInventory.DisplayType.Name, "");
    }

    public static Snapshot load() {
        return snapshotForInstall(NConfig::get);
    }

    /** Snapshot a new extra-panel container applies. Never forced closed. */
    public static Snapshot snapshotForInstall() {
        return load();
    }

    static Snapshot snapshotForInstall(Function<NConfig.Key, Object> get) {
        return read(get);
    }

    static Snapshot read(Function<NConfig.Key, Object> get) {
        return new Snapshot(
                parsePanelState(get.apply(NConfig.Key.extraInvPanelState)),
                parseGrouping(get.apply(NConfig.Key.extraInvGrouping)),
                parseDisplayType(get.apply(NConfig.Key.extraInvDisplayType)),
                parseMinQualityText(get.apply(NConfig.Key.extraInvMinQuality)));
    }

    public static void save(Snapshot snap) {
        write(snap, NConfig::set);
    }

    static void write(Snapshot snap, BiConsumer<NConfig.Key, Object> set) {
        if (snap == null) {
            snap = defaults();
        }
        set.accept(NConfig.Key.extraInvPanelState, clampState(snap.panelState));
        NInventory.Grouping grouping = snap.grouping != null ? snap.grouping : NInventory.Grouping.NONE;
        NInventory.DisplayType displayType = snap.displayType != null ? snap.displayType : NInventory.DisplayType.Name;
        set.accept(NConfig.Key.extraInvGrouping, grouping.name());
        set.accept(NConfig.Key.extraInvDisplayType, displayType.name());
        set.accept(NConfig.Key.extraInvMinQuality, snap.minQualityText != null ? snap.minQualityText : "");
    }

    /** Main inv and container extra panels use separate keys so they do not fight. */
    public static void onPanelStateChanged(boolean mainInvInstalled, boolean extraPanelInstalled, int state) {
        onPanelStateChanged(mainInvInstalled, extraPanelInstalled, state, NConfig::set);
    }

    static void onPanelStateChanged(boolean mainInvInstalled, boolean extraPanelInstalled, int state,
                                    BiConsumer<NConfig.Key, Object> set) {
        if (mainInvInstalled) {
            set.accept(NConfig.Key.inventoryRightPanelShow, state);
        }
        if (extraPanelInstalled) {
            set.accept(NConfig.Key.extraInvPanelState, clampState(state));
        }
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
            return ((Boolean) raw) ? 2 : 0;
        }
        return 0;
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
        if (state < 0 || state > 2) {
            return 0;
        }
        return state;
    }
}
