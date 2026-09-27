package nurgling;

import haven.GItem;
import haven.Coord;
import haven.GOut;
import haven.Loading;
import haven.Resource;
import haven.Widget;
import haven.res.ui.stackinv.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Polls only the player's main inventory and cursor, then feeds the transient overlay. */
final class LootNoticeController {
    private static final double INITIAL_SYNC = 3.0;
    private static final double NAME_WAIT = 3.0;

    private static final class Pending {
        final InventoryGainTracker.Gain<GItem> gain;
        double age;

        Pending(InventoryGainTracker.Gain<GItem> gain) {
            this.gain = gain;
        }
    }

    private final NGameUI gui;
    final LootNoticeOverlay overlay = new LootNoticeOverlay();
    private final InventoryGainTracker<GItem> tracker = new InventoryGainTracker<>();
    private final List<Pending> pending = new ArrayList<>();
    private Object observedInventory;
    private double readyAge;
    private double previewRemaining;
    private LootNoticeLayout previewLayout;
    private LootNoticeLayout savedLayout = new LootNoticeLayout(true, LootNoticeLayout.DEFAULT_X,
            LootNoticeLayout.DEFAULT_Y, LootNoticeLayout.DEFAULT_BACKGROUND_OPACITY);

    LootNoticeController(NGameUI gui) {
        this.gui = gui;
    }

    void tick(double dt) {
        overlay.tick(dt);
        previewRemaining = Math.max(0, previewRemaining - Math.max(0, dt));
        savedLayout = readLayout();
        if (previewRemaining == 0)
            previewLayout = null;
        if (gui.maininv != observedInventory) {
            observedInventory = gui.maininv;
            readyAge = 0;
            pending.clear();
            tracker.reset();
            if (previewRemaining == 0)
                overlay.clear();
        }
        if (gui.maininv == null)
            return;
        List<InventoryGainTracker.Item<GItem>> seen = new ArrayList<>();
        for (Widget child : gui.maininv.children()) {
            if (child instanceof GItem)
                seen.add(snapshot((GItem)child));
        }
        if (gui.vhand != null && gui.vhand.item != null)
            seen.add(snapshot(gui.vhand.item));
        boolean enabled = Boolean.TRUE.equals(NConfig.get(NConfig.Key.lootNotices));
        if (!enabled) {
            tracker.reset();
            tracker.observe(seen, dt);
            pending.clear();
            if (previewRemaining == 0)
                overlay.clear();
            return;
        }
        if (readyAge < INITIAL_SYNC) {
            readyAge += dt;
            tracker.reset();
            tracker.observe(seen, dt);
            return;
        }
        for (InventoryGainTracker.Gain<GItem> gain : tracker.observe(seen, dt))
            pending.add(new Pending(gain));
        for (Iterator<Pending> it = pending.iterator(); it.hasNext();) {
            Pending event = it.next();
            event.age += dt;
            if (event.age >= NAME_WAIT) {
                it.remove();
                continue;
            }
            try {
                GItem item = event.gain.example;
                Resource icon = item.res.get();
                String name = (item instanceof NGItem) ? ((NGItem)item).name() : null;
                if (name == null) {
                    Resource.Tooltip tooltip = icon.layer(Resource.tooltip);
                    if (tooltip != null)
                        name = tooltip.text();
                }
                if (name == null || name.isBlank())
                    continue;
                overlay.add(event.gain.resource, name, icon, event.gain.amount);
                it.remove();
            } catch (Loading ignored) {
                // Tooltip and icon data can arrive after the item widget.
            }
        }
    }

    void preview(LootNoticeLayout layout) {
        overlay.clear();
        overlay.add("preview/grain", nurgling.i18n.L10n.get("loot_notice.sample_grain"), null, 3);
        overlay.add("preview/ore", nurgling.i18n.L10n.get("loot_notice.sample_ore"), null, 2);
        previewRemaining = 4.2;
        previewLayout = layout;
    }

    void updatePreviewLayout(LootNoticeLayout layout) {
        if (previewRemaining > 0)
            previewLayout = layout;
    }

    void draw(GOut g, Coord screen) {
        Integer compassBottom = null;
        if (gui.compassWidget != null && gui.compassWidget.parent != null
                && gui.compassWidget.visible && gui.compassWidget.sz.y > 0)
            compassBottom = gui.compassWidget.c.y + gui.compassWidget.sz.y;
        overlay.draw(g, screen, previewLayout == null ? savedLayout : previewLayout, compassBottom);
    }

    private static LootNoticeLayout readLayout() {
        return new LootNoticeLayout(
                !Boolean.FALSE.equals(NConfig.get(NConfig.Key.lootNoticeBelowCompass)),
                LootNoticeLayout.percent(NConfig.get(NConfig.Key.lootNoticeX), LootNoticeLayout.DEFAULT_X),
                LootNoticeLayout.percent(NConfig.get(NConfig.Key.lootNoticeY), LootNoticeLayout.DEFAULT_Y),
                LootNoticeLayout.percent(NConfig.get(NConfig.Key.lootNoticeBackgroundOpacity),
                        LootNoticeLayout.DEFAULT_BACKGROUND_OPACITY));
    }

    private static InventoryGainTracker.Item<GItem> snapshot(GItem item) {
        String resource;
        try {
            resource = item.res.get().name;
        } catch (Loading loading) {
            resource = null;
        }
        int amount = 1;
        if (item.contents instanceof ItemStack)
            amount = Math.max(1, ((ItemStack)item.contents).order.size());
        return new InventoryGainTracker.Item<>(item, resource, amount);
    }

    void dispose() {
        overlay.dispose();
        pending.clear();
        tracker.reset();
    }
}
