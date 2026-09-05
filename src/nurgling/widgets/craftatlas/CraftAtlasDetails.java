package nurgling.widgets.craftatlas;

import haven.Coord;
import haven.CheckBox;
import haven.GOut;
import haven.Tex;
import haven.Text;
import haven.TextEntry;
import haven.UI;
import haven.Widget;
import monitoring.NGlobalSearchItems;
import nurgling.craftatlas.CraftAtlasController;
import nurgling.craftatlas.CraftAtlasEntry;
import nurgling.craftatlas.CraftAtlasQuality;
import nurgling.craftatlas.CraftAtlasMaterialPlanner;
import nurgling.craftatlas.CraftAtlasMaterialSource;
import nurgling.craftatlas.CraftRecipeGraph;
import nurgling.i18n.L10n;
import nurgling.tools.VSpec;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recipe card and its deterministic clickable row map. */
public class CraftAtlasDetails extends Widget {
    private static final Pattern EQUIPMENT_SLOT = Pattern.compile(
            "(?<![A-Za-z0-9])((?:1[01]|[1-9])[LR])(?:\\s*\\((optional)\\))?",
            Pattern.CASE_INSENSITIVE);
    private static final long MATERIAL_REFRESH_INTERVAL_NS = 3_000_000_000L;
    public enum Target { NONE, INGREDIENT, REQUIREMENT_DESCRIPTION, CYCLE }
    public enum Kind { GILDING, BONUS, CURIOSITY, SLOT, QUALITY, INPUT, REQUIREMENT, STATUS }

    public static final class DetailRow {
        public final Kind kind;
        public final String name, resource;
        public final String value;
        public final int quantity;
        public final Target target;
        public final CraftAtlasEntry.Requirement requirement;
        public final List<CraftAtlasEntry.AttributeRef> attributes;
        public final int slotIndex;
        DetailRow(Kind kind, String name, String resource, String value, int quantity, Target target,
                  CraftAtlasEntry.Requirement requirement, List<CraftAtlasEntry.AttributeRef> attributes) {
            this(kind, name, resource, value, quantity, target, requirement, attributes, -1);
        }
        DetailRow(Kind kind, String name, String resource, String value, int quantity, Target target,
                  CraftAtlasEntry.Requirement requirement, List<CraftAtlasEntry.AttributeRef> attributes,
                  int slotIndex) {
            this.kind = kind; this.name = name; this.resource = resource; this.value = value;
            this.quantity = quantity; this.target = target; this.requirement = requirement;
            this.attributes = attributes == null ? Collections.<CraftAtlasEntry.AttributeRef>emptyList() : attributes;
            this.slotIndex = slotIndex;
        }
    }

    private static final class IconHit {
        final Coord at, size;
        final String name;
        IconHit(Coord at, Coord size, String name) { this.at = at; this.size = size; this.name = name; }
    }

    private static final class PendingMaterials {
        final long generation, requestToken;
        final long storageRevision;
        final String recipeResource;
        final String inventorySignature;
        final CraftAtlasMaterialSource.Snapshot snapshot;
        final Consumer<Boolean> callback;

        PendingMaterials(long generation, long requestToken, long storageRevision, String recipeResource,
                         String inventorySignature, CraftAtlasMaterialSource.Snapshot snapshot,
                         Consumer<Boolean> callback) {
            this.generation = generation;
            this.requestToken = requestToken;
            this.storageRevision = storageRevision;
            this.recipeResource = recipeResource;
            this.inventorySignature = inventorySignature;
            this.snapshot = snapshot;
            this.callback = callback;
        }
    }

    private final CraftAtlasController controller;
    private CraftAtlasEntry entry;
    private CraftAtlasEntry.Requirement requirementDescription;
    private String cycleResource;
    private List<DetailRow> rows = Collections.emptyList();
    private int scroll;
    private final Map<String, Integer> savedScroll = new HashMap<>();
    private final CraftAtlasIconCache icons = new CraftAtlasIconCache();
    private final List<IconHit> iconHits = new ArrayList<>();
    private final TextEntry qualityEntry;
    private final CheckBox autoQualityBox;
    private final CraftAtlasMaterialSource materialSource = new CraftAtlasMaterialSource();
    private CraftAtlasMaterialSource.Snapshot materials;
    private CraftAtlasMaterialPlanner.Plan materialPlan;
    private final Map<Integer, CraftAtlasIngredientSelector> selectors = new HashMap<>();
    private CraftAtlasMaterialPicker materialPicker;
    private final Map<String, Map<Integer, CraftAtlasMaterialPlanner.Selection>> savedSelections = new HashMap<>();
    private Map<Integer, CraftAtlasMaterialPlanner.Selection> selections = new HashMap<>();
    private Runnable planListener;
    private int craftCount = 1;
    private boolean autoQuality;
    private boolean syncingQuality;
    private long nextMaterialRefreshAt;
    private long observedStorageRevision = Long.MIN_VALUE;
    private long materialGeneration;
    private long materialRequestToken;
    private final ConcurrentLinkedQueue<PendingMaterials> completedMaterials = new ConcurrentLinkedQueue<>();
    private final AtomicInteger materialLoadsInFlight = new AtomicInteger();
    private String materialSignature;
    private double quality = 10;
    private final int headerHeight = UI.scale(104);
    private final int sectionHeight = UI.scale(30);
    private final int itemRowHeight = UI.scale(50);
    private final int bonusRowHeight = UI.scale(34);

    public CraftAtlasDetails(Coord size, CraftAtlasController controller) {
        super(size);
        this.controller = controller;
        qualityEntry = add(new TextEntry(UI.scale(54), "10") {
            @Override protected void changed() {
                super.changed();
                if(!syncingQuality && !autoQuality) qualityChanged();
            }
            @Override public boolean mousedown(MouseDownEvent ev) {
                return autoQuality || super.mousedown(ev);
            }
            @Override public boolean keydown(KeyDownEvent ev) {
                return autoQuality || super.keydown(ev);
            }
        });
        qualityEntry.tooltip = L10n.get("craft_atlas.quality_hint");
        qualityEntry.hide();
        autoQualityBox = add(new CheckBox(L10n.get("craft_atlas.auto")));
        autoQualityBox.a = false;
        autoQualityBox.changed(value -> setAutoQuality(value));
        autoQualityBox.hide();
        qualityEntry.setcanfocus(true);
        positionQualityEntry();
    }

    public void setEntry(CraftAtlasEntry value) {
        closeMaterialPicker();
        CraftAtlasEntry oldEntry = entry;
        String previous = entry == null ? null : entry.recipeResource;
        String next = value == null ? null : value.recipeResource;
        if(previous != null && !previous.equals(next)) savedScroll.put(previous, scroll);
        entry = value;
        boolean changed = previous == null ? next != null : !previous.equals(next);
        boolean materialsChanged = changed || (oldEntry != value && value != null &&
                (oldEntry == null || oldEntry.inputs != value.inputs || oldEntry.inputsObserved != value.inputsObserved));
        if(materialsChanged) loadMaterials();
        rebuildRows();
        if(materialsChanged) rebuildSelectors();
        boolean qualityVisible = value != null &&
                (value.categories.contains("gildings") || value.categories.contains("foods") ||
                        value.categories.contains("equipment") || value.inputsObserved);
        qualityEntry.visible = qualityVisible;
        autoQualityBox.visible = qualityVisible && qualityControlsFit();
        if(previous == null || !previous.equals(next)) scroll = next == null ? 0 : savedScroll.getOrDefault(next, 0);
    }

    public void setCraftCount(int value) {
        if(!supportsCraftCount(value)) return;
        craftCount = Math.max(1, value);
        replan();
    }

    public boolean supportsCraftCount(int value) {
        return materials == null || CraftAtlasMaterialPlanner.supportsCraftCount(materials.slots, selections, value);
    }

    public void refreshMaterialsAsync(long storageRevision, Consumer<Boolean> callback) {
        startMaterialRefresh(storageRevision, callback);
    }

    public void refreshMaterialsIfDue(long now, long storageRevision) {
        PendingMaterials completed;
        while((completed = completedMaterials.poll()) != null) {
            boolean requestCurrent = completed.snapshot != null && entry != null &&
                    completed.generation == materialGeneration &&
                    completed.requestToken == materialRequestToken &&
                    entry.recipeResource.equals(completed.recipeResource);
            long currentStorageRevision = NGlobalSearchItems.storageRevision();
            boolean sourceCurrent = requestCurrent && completed.storageRevision == currentStorageRevision &&
                    completed.inventorySignature.equals(inventorySignature(
                            CraftAtlasMaterialSource.inventorySamples()));
            if(sourceCurrent) {
                String signature = materialSignature(completed.snapshot);
                if(!signature.equals(materialSignature)) {
                    installMaterials(completed.snapshot, signature);
                    rebuildRows();
                    rebuildSelectors();
                }
            }
            if(requestCurrent && !sourceCurrent) {
                startMaterialRefresh(currentStorageRevision, completed.callback);
            } else if(completed.callback != null) {
                completed.callback.accept(sourceCurrent);
            }
        }
        if(entry == null || (now < nextMaterialRefreshAt && storageRevision == observedStorageRevision)
                || materialLoadsInFlight.get() > 0) return;
        startMaterialRefresh(storageRevision, null);
    }

    private void startMaterialRefresh(long storageRevision, Consumer<Boolean> callback) {
        if(entry == null) {
            if(callback != null) callback.accept(false);
            return;
        }
        nextMaterialRefreshAt = System.nanoTime() + MATERIAL_REFRESH_INTERVAL_NS;
        observedStorageRevision = storageRevision;
        final CraftAtlasEntry requestedEntry = entry;
        final long requestedGeneration = materialGeneration;
        final long requestToken = ++materialRequestToken;
        final List<CraftAtlasMaterialSource.InventorySample> inventory =
                CraftAtlasMaterialSource.inventorySamples();
        final String requestedInventorySignature = inventorySignature(inventory);
        materialLoadsInFlight.incrementAndGet();
        Thread worker = new Thread(() -> {
            CraftAtlasMaterialSource.Snapshot snapshot = null;
            try {
                snapshot = materialSource.load(requestedEntry, inventory);
            } catch(RuntimeException error) {
                error.printStackTrace();
            } finally {
                completedMaterials.add(new PendingMaterials(requestedGeneration, requestToken, storageRevision,
                        requestedEntry.recipeResource, requestedInventorySignature, snapshot, callback));
                materialLoadsInFlight.decrementAndGet();
            }
        }, "CraftAtlasStockRefresh");
        worker.setDaemon(true);
        worker.start();
    }

    public CraftAtlasMaterialSource.Snapshot materialSnapshot() { return materials; }
    public CraftAtlasMaterialPlanner.Plan materialPlan() { return materialPlan; }
    public void setPlanListener(Runnable listener) { planListener = listener; }

    public void setState(CraftAtlasController.ViewState state) {
        setEntry(state.selected);
        cycleResource = state.cycleResource;
        requirementDescription = state.requirementDescription;
    }

    public static List<DetailRow> buildRows(CraftAtlasEntry entry, Function<String, CraftRecipeGraph.LinkState> links) {
        return buildRows(entry, (resource, name) -> links.apply(resource), 10);
    }

    public static List<DetailRow> buildRows(CraftAtlasEntry entry,
                                             BiFunction<String, String, CraftRecipeGraph.LinkState> links) {
        return buildRows(entry, links, 10);
    }

    static List<DetailRow> buildRows(CraftAtlasEntry entry,
                                      BiFunction<String, String, CraftRecipeGraph.LinkState> links, double quality) {
        List<DetailRow> rows = new ArrayList<>();
        if(entry.gilding != null)
            rows.add(new DetailRow(Kind.GILDING, formatChance(entry.gilding.pmin, entry.gilding.pmax), null,
                    null, 0, Target.NONE, null, entry.gilding.attributes));
        for(CraftAtlasEntry.Bonus bonus : entry.bonuses) {
            if(entry.categories.contains("foods") &&
                    ("food:energy".equals(bonus.attributeResource) || "food:hunger".equals(bonus.attributeResource)))
                continue;
            rows.add(new DetailRow(Kind.BONUS, bonus.name, bonus.attributeResource,
                    bonus.value == null ? null : format(CraftAtlasQuality.project(entry, bonus, quality)),
                    0, Target.NONE, null, null));
        }
        if(entry.curiosity != null) {
            CraftAtlasEntry.Curiosity curiosity = entry.curiosity;
            rows.add(new DetailRow(Kind.CURIOSITY, L10n.get("craft_atlas.curiosity.lp"), null,
                    String.format(Locale.ROOT, "%,d", curiosity.learningPoints(quality)), 0, Target.NONE, null, null));
            rows.add(new DetailRow(Kind.CURIOSITY, L10n.get("craft_atlas.curiosity.time"), null,
                    formatStudyTime(curiosity.studyMinutes), 0, Target.NONE, null, null));
            rows.add(new DetailRow(Kind.CURIOSITY, L10n.get("craft_atlas.curiosity.weight"), null,
                    Integer.toString(curiosity.mentalWeight), 0, Target.NONE, null, null));
            rows.add(new DetailRow(Kind.CURIOSITY, L10n.get("craft_atlas.curiosity.lp_hour"), null,
                    formatUnsigned(curiosity.lpPerHour(quality)), 0, Target.NONE, null, null));
            rows.add(new DetailRow(Kind.CURIOSITY, L10n.get("craft_atlas.curiosity.lp_hour_weight"), null,
                    formatUnsigned(curiosity.lpPerHourPerWeight(quality)), 0, Target.NONE, null, null));
        }
        for(String slot : entry.equipmentSlots)
            rows.add(new DetailRow(Kind.SLOT, formatEquipmentSlots(slot), null, null, 0, Target.NONE, null, null));
        for(int slotIndex = 0; slotIndex < entry.inputs.size(); slotIndex++) {
            CraftAtlasEntry.InputSlot slot = entry.inputs.get(slotIndex);
            CraftAtlasEntry.IngredientOption linked = slot.options.get(0);
            CraftRecipeGraph.LinkState state = CraftRecipeGraph.LinkState.NONE;
            List<String> names = new ArrayList<>();
            for(CraftAtlasEntry.IngredientOption option : slot.options) {
                names.add(option.name);
                CraftRecipeGraph.LinkState candidate = links.apply(option.resource, option.name);
                if(state == CraftRecipeGraph.LinkState.NONE && candidate != CraftRecipeGraph.LinkState.NONE) {
                    linked = option;
                    state = candidate;
                }
            }
            Target target = state == CraftRecipeGraph.LinkState.NONE ? Target.NONE :
                    state == CraftRecipeGraph.LinkState.CYCLE ? Target.CYCLE : Target.INGREDIENT;
            rows.add(new DetailRow(Kind.INPUT, String.join(" / ", names), linked.resource, null,
                    slot.quantity, target, null, null, slotIndex));
        }
        for(CraftAtlasEntry.Requirement requirement : entry.requirements) {
            if(requirement.kind != CraftAtlasEntry.RequirementKind.STATION) continue;
            CraftRecipeGraph.LinkState state = links.apply(requirement.resource, requirement.name);
            Target target = state == CraftRecipeGraph.LinkState.NONE ? Target.NONE :
                    state == CraftRecipeGraph.LinkState.CYCLE ? Target.CYCLE : Target.INGREDIENT;
            rows.add(new DetailRow(Kind.REQUIREMENT, requirement.name, requirement.resource,
                    requirement.kind.name(), 0, target, requirement, null));
        }
        return Collections.unmodifiableList(rows);
    }

    static String formatEquipmentSlots(String value) {
        if(value == null || value.trim().isEmpty()) return value;
        Matcher matcher = EQUIPMENT_SLOT.matcher(value);
        List<String> slots = new ArrayList<>();
        while(matcher.find()) {
            String code = matcher.group(1).toUpperCase(Locale.ROOT);
            String slot = L10n.get("craft_atlas.equipment_slot." + code.toLowerCase(Locale.ROOT)) + " (" + code + ")";
            if(matcher.group(2) != null) slot += " — " + L10n.get("craft_atlas.equipment_slot.optional");
            slots.add(slot);
        }
        return slots.isEmpty() ? value : String.join("; ", slots);
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.format("%+.0f", value) : String.format("%+.1f", value);
    }

    private static String formatUnsigned(double value) {
        if(!Double.isFinite(value)) return "—";
        return value == Math.rint(value) ? String.format(Locale.ROOT, "%.0f", value) :
                String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatStudyTime(int minutes) {
        int positive = Math.max(0, minutes);
        return positive / 60 + L10n.get("craft_atlas.curiosity.hours") + " " +
                positive % 60 + L10n.get("craft_atlas.curiosity.minutes");
    }

    @Override public void draw(GOut g) {
        iconHits.clear();
        g.chcolor(new Color(24, 29, 33, 235)); g.frect(Coord.z, sz); g.chcolor();
        if(entry == null) { g.text(L10n.get("craft_atlas.no_recipe"), UI.scale(18, 28)); return; }

        int productBox = UI.scale(68);
        Coord productAt = UI.scale(16, 14);
        g.chcolor(new Color(9, 13, 16, 215)); g.frect(productAt, Coord.of(productBox, productBox)); g.chcolor();
        Tex productIcon = icons.recipe(entry.outputResource, entry.recipeResource, entry.displayName);
        CraftAtlasIconCache.draw(g, productIcon, productAt.add(UI.scale(3), UI.scale(3)), productBox - UI.scale(6));
        drawCentered(g, entry.displayName, UI.scale(98), UI.scale(14), UI.scale(24), null);
        Color availability = entry.availability == CraftAtlasEntry.Availability.OPEN ? new Color(103, 201, 129) :
                entry.availability == CraftAtlasEntry.Availability.REFERENCE_ONLY ? new Color(210, 171, 91) : new Color(153, 160, 162);
        drawCentered(g, "\u25cf  " + L10n.get(statusKey(entry.availability)), UI.scale(98), UI.scale(40), UI.scale(22), availability);
        String notice = requirementDescription != null ? requirementDescription.description :
                cycleResource != null ? L10n.get("craft_atlas.cycle") : null;
        if(notice != null && !notice.isEmpty()) {
            drawCentered(g, notice, UI.scale(98), UI.scale(66), UI.scale(22), new Color(204, 187, 137, 230));
        } else {
            String category = entry.categories.contains("equipment") ? L10n.get("craft_atlas.section.equipment") :
                    entry.categories.contains("gildings") ? L10n.get("craft_atlas.section.gildings") :
                            entry.categories.contains("foods") ? L10n.get("craft_atlas.section.foods") :
                                    entry.categories.contains("curiosities") ? L10n.get("craft_atlas.section.curiosities") : "";
            if(!category.isEmpty()) drawCentered(g, category, UI.scale(98), UI.scale(66), UI.scale(22), new Color(169, 179, 181, 220));
        }
        if(qualityEntry.visible)
            drawCentered(g, L10n.get("craft_atlas.quality"), Math.max(UI.scale(98), qualityEntry.c.x - UI.scale(78)),
                    UI.scale(14), UI.scale(24), new Color(169, 179, 181, 220));
        g.chcolor(new Color(75, 83, 86, 175)); g.frect(Coord.of(0, headerHeight - 1), Coord.of(sz.x, 1)); g.chcolor();

        int y = headerHeight - scroll;
        Kind previous = null;
        int ordinal = 0;
        for(DetailRow row : rows) {
            if(row.kind != previous) {
                drawSectionHeader(g, row.kind, y);
                y += sectionHeight;
                previous = row.kind;
                ordinal = 0;
            }
            int height = rowHeight(row);
            if(y + height >= headerHeight && y <= sz.y) drawRow(g, row, y, height, ordinal);
            y += height;
            ordinal++;
        }
        positionSelectors();
        super.draw(g);
    }

    private static String formatChance(double pmin, double pmax) {
        return Math.round(pmin * 100) + "%–" + Math.round(pmax * 100) + "%";
    }

    private void drawSectionHeader(GOut g, Kind kind, int y) {
        if(y + sectionHeight < headerHeight || y > sz.y) return;
        String key = kind == Kind.GILDING ? "craft_atlas.gilding" :
                kind == Kind.BONUS ? "craft_atlas.bonuses" :
                kind == Kind.CURIOSITY ? "craft_atlas.curiosity" :
                kind == Kind.SLOT ? "craft_atlas.equipment_slots" :
                kind == Kind.QUALITY ? "craft_atlas.quality_modifiers" :
                kind == Kind.INPUT ? "craft_atlas.inputs" : "craft_atlas.requirements";
        String label = L10n.get(key);
        Color color = new Color(208, 171, 91, 235);
        drawCentered(g, label, UI.scale(16), y, sectionHeight, color);
        int labelWidth = Text.render(label).sz().x;
        g.chcolor(new Color(93, 77, 48, 190));
        int lineX = UI.scale(16) + labelWidth + UI.scale(10);
        g.frect(Coord.of(lineX, y + sectionHeight / 2), Coord.of(Math.max(0, sz.x - lineX - UI.scale(16)), 1));
        g.chcolor();
    }

    private void drawRow(GOut g, DetailRow row, int y, int height, int ordinal) {
        if((ordinal & 1) == 1) { g.chcolor(new Color(17, 22, 25, 135)); g.frect(Coord.of(UI.scale(8), y), Coord.of(sz.x - UI.scale(16), height - 1)); g.chcolor(); }
        if(row.target != Target.NONE) { g.chcolor(new Color(48, 71, 78, 120)); g.frect(Coord.of(UI.scale(8), y), Coord.of(sz.x - UI.scale(16), height - 1)); g.chcolor(); }
        if(row.kind == Kind.GILDING) {
            drawCentered(g, row.name, UI.scale(18), y, height, null);
            int iconBox = UI.scale(28);
            int x = UI.scale(18) + Text.render(row.name).sz().x + UI.scale(12);
            for(CraftAtlasEntry.AttributeRef attribute : row.attributes) {
                Coord iconAt = Coord.of(x, y + (height - iconBox) / 2);
                drawIcon(g, iconAt, iconBox, attribute.resource, attribute.name);
                x += iconBox + UI.scale(5);
            }
            return;
        }
        if(row.kind == Kind.BONUS) {
            int iconBox = UI.scale(30);
            Coord iconAt = Coord.of(UI.scale(16), y + (height - iconBox) / 2);
            drawIcon(g, iconAt, iconBox, row.resource, row.name);
            drawCentered(g, row.name, UI.scale(56), y, height, null);
            if(row.value != null) {
                drawCentered(g, row.value, Math.max(UI.scale(180), sz.x - UI.scale(72)), y, height,
                        new Color(117, 211, 147));
            }
            return;
        }
        if(row.kind == Kind.CURIOSITY) {
            drawCentered(g, row.name, UI.scale(18), y, height, null);
            drawCentered(g, row.value, Math.max(UI.scale(180), sz.x - UI.scale(92)), y, height,
                    new Color(132, 220, 159));
            return;
        }
        if(row.kind == Kind.SLOT) {
            drawCentered(g, row.name, UI.scale(16), y, height, null);
            return;
        }
        if(row.kind == Kind.QUALITY) {
            int iconBox = UI.scale(30);
            Coord iconAt = Coord.of(UI.scale(16), y + (height - iconBox) / 2);
            drawIcon(g, iconAt, iconBox, row.resource, row.name);
            drawCentered(g, row.name, UI.scale(56), y, height, null);
            return;
        }
        int iconBox = UI.scale(36);
        Coord iconAt = Coord.of(UI.scale(16), y + (height - iconBox) / 2);
        String displayName = row.kind == Kind.INPUT ? selectedMaterial(row.slotIndex) : null;
        if(displayName == null) displayName = row.name;
        String displayResource = row.kind == Kind.INPUT && !displayName.equals(row.name)
                ? VSpec.getIconPath(displayName) : row.resource;
        drawIcon(g, iconAt, iconBox, displayResource, displayName);
        String title = row.kind == Kind.INPUT ? row.quantity + " × " + displayName : row.name;
        drawCentered(g, title, UI.scale(62), y + UI.scale(3), UI.scale(23), null);
        String meta = row.kind == Kind.REQUIREMENT ? L10n.get("craft_atlas.requirement." + row.value.toLowerCase()) :
                (row.target == Target.NONE ? "" : L10n.get("craft_atlas.open_recipe"));
        if(!meta.isEmpty()) drawCentered(g, meta, UI.scale(62), y + UI.scale(25), UI.scale(20), new Color(163, 172, 174, 220));
        if(row.target != Target.NONE) drawCentered(g, "\u203a", sz.x - UI.scale(24), y, height, new Color(220, 177, 89));
    }

    private static void drawCentered(GOut g, String text, int x, int y, int height, Color color) {
        if(color != null) g.chcolor(color);
        g.atext(text, Coord.of(x, y + height / 2), 0, 0.5);
        if(color != null) g.chcolor();
    }

    private void drawIcon(GOut g, Coord at, int box, String resource, String name) {
        g.chcolor(new Color(9, 13, 16, 205)); g.frect(at, Coord.of(box, box)); g.chcolor();
        CraftAtlasIconCache.draw(g, icons.icon(resource, name), at.add(UI.scale(2), UI.scale(2)), box - UI.scale(4));
        iconHits.add(new IconHit(at, Coord.of(box, box), name));
    }

    private int rowHeight(DetailRow row) {
        return row.kind == Kind.BONUS || row.kind == Kind.CURIOSITY || row.kind == Kind.SLOT ? bonusRowHeight :
                row.kind == Kind.GILDING || row.kind == Kind.QUALITY ? UI.scale(44) : itemRowHeight;
    }

    private int contentHeight() {
        int height = 0;
        Kind previous = null;
        for(DetailRow row : rows) {
            if(row.kind != previous) { height += sectionHeight; previous = row.kind; }
            height += rowHeight(row);
        }
        return height;
    }

    private DetailRow rowAt(int contentY) {
        int y = 0;
        Kind previous = null;
        for(DetailRow row : rows) {
            if(row.kind != previous) { y += sectionHeight; previous = row.kind; }
            int height = rowHeight(row);
            if(contentY >= y && contentY < y + height) return row;
            y += height;
        }
        return null;
    }

    static String statusKey(CraftAtlasEntry.Availability availability) {
        if(availability == CraftAtlasEntry.Availability.OPEN) return "craft_atlas.status.open";
        if(availability == CraftAtlasEntry.Availability.REFERENCE_ONLY) return "craft_atlas.status.reference";
        return "craft_atlas.status.unavailable";
    }

    @Override public boolean mousedown(MouseDownEvent ev) {
        if(super.mousedown(ev)) return true;
        if(ev.b != 1 || entry == null || ev.c.y < headerHeight) return false;
        DetailRow row = rowAt(ev.c.y - headerHeight + scroll);
        if(row == null) return false;
        if(row.kind == Kind.INPUT && isGroupedSlot(row.slotIndex) &&
                ev.c.x >= UI.scale(16) && ev.c.x < UI.scale(52)) {
            openMaterialPicker(row.slotIndex, ev.c);
            return true;
        }
        if(row.target == Target.INGREDIENT || row.target == Target.CYCLE) {
            if(row.requirement != null) controller.openRequirement(row.requirement);
            else {
                String material = row.kind == Kind.INPUT ? selectedMaterial(row.slotIndex) : null;
                controller.openIngredient(material == null ? row.resource : null,
                        material == null ? row.name : material);
            }
            return true;
        }
        if(row.target == Target.REQUIREMENT_DESCRIPTION) { controller.openRequirement(row.requirement); return true; }
        return false;
    }

    @Override public boolean mousewheel(MouseWheelEvent ev) {
        int max = Math.max(0, contentHeight() - Math.max(1, sz.y - headerHeight));
        scroll = Math.max(0, Math.min(max, scroll + ev.a * itemRowHeight));
        if(entry != null) savedScroll.put(entry.recipeResource, scroll);
        return true;
    }

    @Override public void resize(Coord size) {
        super.resize(size);
        positionQualityEntry();
        if(!selectors.isEmpty() && selectors.values().iterator().next().sz.x != selectorWidth())
            rebuildSelectors();
        positionSelectors();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight() - Math.max(1, sz.y - headerHeight))));
    }

    private void positionQualityEntry() {
        if(qualityEntry != null) {
            int margin = UI.scale(12);
            int availableWidth = Math.max(1, sz.x - (2 * margin));
            qualityEntry.resize(Coord.of(Math.min(UI.scale(54), availableWidth), qualityEntry.sz.y));
            int[] x = qualityControlPositions(sz.x, qualityEntry.sz.x, autoQualityBox.sz.x,
                    UI.scale(8), margin);
            qualityEntry.move(Coord.of(x[0], UI.scale(14)));
            autoQualityBox.move(Coord.of(x[1], UI.scale(16)));
            autoQualityBox.visible = qualityEntry.visible && x[2] != 0;
        }
    }

    static int[] qualityControlPositions(int width, int entryWidth, int autoWidth, int gap, int margin) {
        width = Math.max(0, width);
        entryWidth = Math.max(0, Math.min(entryWidth, width));
        autoWidth = Math.max(0, autoWidth);
        gap = Math.max(0, gap);
        margin = Math.max(0, margin);
        boolean showAuto = width >= entryWidth + gap + autoWidth + (2 * margin);
        int autoX = showAuto ? width - margin - autoWidth : width;
        int entryX = showAuto ? autoX - gap - entryWidth : Math.max(0, width - margin - entryWidth);
        return new int[] { Math.max(0, entryX), Math.max(0, autoX), showAuto ? 1 : 0 };
    }

    private boolean qualityControlsFit() {
        return qualityControlPositions(sz.x, qualityEntry.sz.x, autoQualityBox.sz.x,
                UI.scale(8), UI.scale(12))[2] != 0;
    }

    private void qualityChanged() {
        try { quality = Math.max(1, Double.parseDouble(qualityEntry.text().replace(',', '.'))); }
        catch(NumberFormatException ignored) { quality = 10; }
        rebuildRows();
    }

    private void rebuildRows() {
        rows = entry == null ? Collections.<DetailRow>emptyList() :
                buildRows(entry, (resource, name) -> controller == null ? CraftRecipeGraph.LinkState.NONE :
                        controller.linkState(resource, name), quality);
        if(autoQuality && (materialPlan == null || materialPlan.quality == null)) {
            List<DetailRow> withoutProjection = new ArrayList<>();
            for(DetailRow row : rows)
                withoutProjection.add(row.kind == Kind.BONUS
                        ? new DetailRow(row.kind, row.name, row.resource, null, row.quantity, row.target,
                                row.requirement, row.attributes, row.slotIndex) : row);
            rows = Collections.unmodifiableList(withoutProjection);
        }
    }

    private void loadMaterials() {
        materialGeneration++;
        materialRequestToken++;
        if(entry == null) {
            clearSelectors();
            materials = null;
            materialSignature = null;
            materialPlan = null;
            selections = new HashMap<>();
            notifyPlanChanged();
            return;
        }
        CraftAtlasMaterialSource.Snapshot fresh = materialSource.loadInventoryOnly(
                entry, CraftAtlasMaterialSource.inventorySamples());
        installMaterials(fresh, materialSignature(fresh));
        startMaterialRefresh(NGlobalSearchItems.storageRevision(), null);
    }

    private void installMaterials(CraftAtlasMaterialSource.Snapshot fresh, String signature) {
        clearSelectors();
        materials = fresh;
        materialSignature = signature;
        Map<Integer, CraftAtlasMaterialPlanner.Selection> explicit = savedSelections.get(entry.recipeResource);
        selections = CraftAtlasMaterialPlanner.resolveSelections(
                materials.slots, materials.candidatesBySlot, explicit);
        replan();
    }

    private static String inventorySignature(List<CraftAtlasMaterialSource.InventorySample> inventory) {
        List<String> values = new ArrayList<>();
        if(inventory != null) for(CraftAtlasMaterialSource.InventorySample sample : inventory) {
            if(sample == null) continue;
            values.add(String.valueOf(sample.name) + ':' + Double.doubleToLongBits(sample.quality) + ':' + sample.count);
        }
        Collections.sort(values);
        return String.join("|", values);
    }

    private static String materialSignature(CraftAtlasMaterialSource.Snapshot snapshot) {
        StringBuilder value = new StringBuilder().append(snapshot.collectible);
        for(CraftAtlasMaterialPlanner.SlotRequest slot : snapshot.slots) {
            value.append('|').append(slot.slotIndex).append(':').append(slot.unitsPerCraft)
                    .append(':').append(slot.optional).append(':').append(slot.allowedMaterials);
            for(CraftAtlasMaterialPlanner.Candidate candidate : snapshot.candidatesBySlot.getOrDefault(
                    slot.slotIndex, Collections.emptyList()))
                value.append(';').append(candidate.id).append(':')
                        .append(Double.doubleToLongBits(candidate.quality)).append(':')
                        .append(candidate.count).append(':').append(candidate.location);
        }
        for(Map.Entry<String, nurgling.widgets.NStorageItemsWidget.GroupedItem> row :
                snapshot.storageByCandidateId.entrySet()) {
            value.append('|').append(row.getKey());
            for(nurgling.db.dao.StorageItemDao.StorageItemData item : row.getValue().items)
                value.append(';').append(item.getItemHash()).append(':').append(item.getContainer());
        }
        return value.toString();
    }

    private void replan() {
        if(materials == null) {
            materialPlan = null;
        } else if(!CraftAtlasMaterialPlanner.supportsCraftCount(materials.slots, selections, craftCount)) {
            materialPlan = null;
        } else {
            materialPlan = CraftAtlasMaterialPlanner.plan(materials.slots, materials.candidatesBySlot,
                    selections, craftCount);
        }
        if(autoQuality) {
            Double plannedQuality = materialPlan == null ? null : materialPlan.quality;
            if(plannedQuality != null) quality = plannedQuality;
            syncingQuality = true;
            qualityEntry.settext(autoQualityText(plannedQuality));
            syncingQuality = false;
            rebuildRows();
        }
        notifyPlanChanged();
    }

    private void setAutoQuality(boolean value) {
        autoQuality = value;
        autoQualityBox.a = value;
        qualityEntry.setcanfocus(!value);
        if(value) replan();
        else qualityChanged();
    }

    private void rebuildSelectors() {
        clearSelectors();
        if(entry == null || materials == null) return;
        for(CraftAtlasMaterialPlanner.SlotRequest slot : materials.slots) {
            List<CraftAtlasMaterialPlanner.Candidate> candidates = materials.candidatesBySlot.getOrDefault(
                    slot.slotIndex, Collections.emptyList());
            boolean grouped = slot.allowedMaterials.size() > 1;
            CraftAtlasIngredientSelector selector = add(new CraftAtlasIngredientSelector(selectorWidth(), candidates,
                    slot.optional, grouped, selections.get(slot.slotIndex), selected -> {
                applySelection(slot.slotIndex, selected, false);
            }));
            selectors.put(slot.slotIndex, selector);
        }
        positionSelectors();
    }

    private boolean isGroupedSlot(int slotIndex) {
        if(materials == null || slotIndex < 0) return false;
        for(CraftAtlasMaterialPlanner.SlotRequest slot : materials.slots)
            if(slot.slotIndex == slotIndex) return slot.allowedMaterials.size() > 1;
        return false;
    }

    private String selectedMaterial(int slotIndex) {
        CraftAtlasMaterialPlanner.Selection selected = selections.get(slotIndex);
        return selected == null || selected.isAll() || selected.isIgnored() ? null : selected.material;
    }

    private void openMaterialPicker(int slotIndex, Coord click) {
        if(ui == null || entry == null || materials == null ||
                slotIndex < 0 || slotIndex >= entry.inputs.size()) return;
        CraftAtlasMaterialPlanner.SlotRequest slot = materialSlot(slotIndex);
        if(slot == null || slot.allowedMaterials.size() < 2) return;
        closeMaterialPicker();
        List<CraftAtlasMaterialPicker.Option> options = CraftAtlasMaterialPicker.optionsFor(
                entry.inputs.get(slotIndex), slot.allowedMaterials);
        materialPicker = new CraftAtlasMaterialPicker(options, selectedMaterial(slotIndex),
                material -> selectMaterial(slotIndex, material), () -> materialPicker = null);
        Coord at = rootpos().add(UI.scale(16), click.y + UI.scale(8));
        if(at.y + materialPicker.sz.y > ui.root.sz.y)
            at = Coord.of(at.x, rootpos().y + click.y - materialPicker.sz.y - UI.scale(8));
        Coord maximum = ui.root.sz.sub(materialPicker.sz).max(Coord.z);
        at = Coord.of(Math.max(0, Math.min(at.x, maximum.x)), Math.max(0, Math.min(at.y, maximum.y)));
        ui.root.add(materialPicker, at);
        materialPicker.raise();
    }

    private void selectMaterial(int slotIndex, String material) {
        CraftAtlasMaterialPlanner.SlotRequest slot = materialSlot(slotIndex);
        if(slot == null || material == null || !slot.allowedMaterials.contains(material)) return;
        List<CraftAtlasMaterialPlanner.Candidate> candidates = materials.candidatesBySlot.getOrDefault(
                slotIndex, Collections.emptyList());
        List<CraftAtlasMaterialPlanner.Candidate> matching = new ArrayList<>();
        for(CraftAtlasMaterialPlanner.Candidate candidate : candidates)
            if(material.equals(candidate.material)) matching.add(candidate);
        CraftAtlasMaterialPlanner.Selection selected = matching.isEmpty()
                ? CraftAtlasMaterialPlanner.Selection.material(material)
                : CraftAtlasMaterialPlanner.defaultSelection(matching);
        applySelection(slotIndex, selected, true);
    }

    private void applySelection(int slotIndex, CraftAtlasMaterialPlanner.Selection selected,
                                boolean refreshSelector) {
        selections.put(slotIndex, selected);
        savedSelections.computeIfAbsent(entry.recipeResource, key -> new HashMap<>())
                .put(slotIndex, selected);
        if(refreshSelector) {
            CraftAtlasMaterialPlanner.SlotRequest slot = materialSlot(slotIndex);
            CraftAtlasIngredientSelector selector = selectors.get(slotIndex);
            if(slot != null && selector != null)
                selector.setChoices(materials.candidatesBySlot.getOrDefault(slotIndex, Collections.emptyList()),
                        slot.optional, true, selected);
        }
        replan();
    }

    private CraftAtlasMaterialPlanner.SlotRequest materialSlot(int slotIndex) {
        if(materials != null) for(CraftAtlasMaterialPlanner.SlotRequest slot : materials.slots)
            if(slot.slotIndex == slotIndex) return slot;
        return null;
    }

    private void closeMaterialPicker() {
        CraftAtlasMaterialPicker picker = materialPicker;
        materialPicker = null;
        if(picker != null && picker.parent != null) picker.destroy();
    }

    private void clearSelectors() {
        for(CraftAtlasIngredientSelector selector : selectors.values()) selector.reqdestroy();
        selectors.clear();
    }

    private void positionSelectors() {
        for(CraftAtlasIngredientSelector selector : selectors.values()) selector.hide();
        int y = headerHeight - scroll;
        Kind previous = null;
        for(DetailRow row : rows) {
            if(row.kind != previous) { y += sectionHeight; previous = row.kind; }
            int height = rowHeight(row);
            if(row.kind == Kind.INPUT && row.slotIndex >= 0) {
                CraftAtlasIngredientSelector selector = selectors.get(row.slotIndex);
                if(selector != null) {
                    selector.move(Coord.of(Math.max(0, sz.x - selector.sz.x - UI.scale(18)),
                            y + (height - selector.sz.y) / 2));
                    selector.visible = y >= headerHeight && y + height <= sz.y;
                }
            }
            y += height;
        }
    }

    private void notifyPlanChanged() {
        if(planListener != null) planListener.run();
    }

    private int selectorWidth() {
        return Math.max(UI.scale(90), Math.min(UI.scale(300), sz.x - UI.scale(208)));
    }

    private static String formatQuality(double value) {
        return value == Math.rint(value) ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.1f", value);
    }

    static String autoQualityText(Double value) {
        return value == null ? L10n.get("craft_atlas.quality_unavailable") : formatQuality(value);
    }

    @Override public Object tooltip(Coord c, Widget prev) {
        for(IconHit hit : iconHits) if(c.isect(hit.at, hit.size)) return hit.name;
        return super.tooltip(c, prev);
    }

    @Override public void dispose() {
        closeMaterialPicker();
        icons.dispose();
        super.dispose();
    }
}
