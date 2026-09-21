package nurgling.widgets.craftatlas;

import haven.*;
import nurgling.NConfig;
import nurgling.NCore;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.NWindowDeco;
import nurgling.craftatlas.CraftAtlasPreferences;
import nurgling.craftatlas.quality.QualityWorkshopModel;
import nurgling.craftatlas.quality.QualityWorkshopModel.Key;
import nurgling.craftatlas.quality.QualityWorkshopModel.Iteration;
import nurgling.craftatlas.quality.QualityWorkshopModel.MiningIteration;
import nurgling.craftatlas.quality.QualityWorkshopStore;
import nurgling.craftatlas.quality.QualityWorkshopQuantities;
import nurgling.db.service.StorageItemService;
import nurgling.i18n.L10n;

import java.awt.Color;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Player-owned what-if values. Observed inventory/workstations are suggestions only. */
public final class QualityWorkshopWindow extends Window {
    private final CraftAtlasPreferences atlas;
    private final Path file;
    private final QualityWorkshopModel model;
    private final CraftAtlasIconCache icons = new CraftAtlasIconCache();
    private final Scrollport inputPane, outputPane;
    private final Button addResult, refresh, remember;
    private final Label status;
    private final Map<Key, InputRow> inputRows = new EnumMap<>(Key.class);
    private final Map<Key, String> drafts = new EnumMap<>(Key.class);
    private final Set<Key> invalid = EnumSet.noneOf(Key.class);
    private final Map<Key, String> amountDrafts = new EnumMap<>(Key.class);
    private final Set<Key> invalidAmounts = EnumSet.noneOf(Key.class);
    private final Map<Key, ResultRow> resultRows = new EnumMap<>(Key.class);
    private QualityWorkshopQuantities.Plan quantities;
    private boolean quantitiesChanged = true;
    private String potterYieldDraft;
    private boolean invalidPotterYield;
    private Map<Key, QualityWorkshopHints.Hint> storageHints = Collections.emptyMap();
    private final ConcurrentLinkedQueue<HintResult> pending = new ConcurrentLinkedQueue<>();
    private long hintRequest;
    private boolean hintsLoading, closed, rebuildInputs, rebuildOutputs;
    private double saveAfter = -1;
    private Window picker;

    @Override protected Deco makedeco() { return new NWindowDeco(false).freeformResize(UI.scale(720, 440)); }

    public QualityWorkshopWindow(CraftAtlasPreferences atlas) {
        this(atlas, QualityWorkshopStore.profilePath(), null);
    }

    QualityWorkshopWindow(CraftAtlasPreferences atlas, Path file, QualityWorkshopModel initial) {
        super(UI.scale(1040, 690), tr("title"));
        this.atlas = atlas;
        this.file = file;
        this.model = initial == null ? QualityWorkshopStore.load(file) : initial;
        addResult = add(new Button(UI.scale(205), tr("add")).action(this::showPicker));
        remember = add(new Button(UI.scale(205), tr("snapshot")).action(() -> {
            model.snapshot(); changed(false); setStatus(tr("snapshot_done"));
        }));
        refresh = add(new Button(UI.scale(205), tr("refresh")).action(this::refreshHints));
        status = add(new Label(tr("intro")));
        inputPane = add(new Scrollport(UI.scale(400, 550)));
        outputPane = add(new Scrollport(UI.scale(580, 550)));
        layout();
    }

    @Override protected void added() {
        super.added();
        if(parent != null) {
            Coord frame = sz.sub(csz());
            Coord available = parent.sz.sub(frame).sub(UI.scale(20, 20));
            resize(csz().min(available.max(UI.scale(400, 300))));
            c = parent.sz.sub(sz).div(2).max(Coord.z);
        }
        refreshHints();
    }

    private static String tr(String key) { return L10n.get("quality_workshop." + key); }
    static Double parseQuality(String value) {
        try {
            double n = Double.parseDouble(value.trim().replace(',', '.'));
            return Double.isFinite(n) && n >= 1 && n <= 100000 ? n : null;
        } catch(Exception ignored) { return null; }
    }
    static Double parseAmount(Key key, String value) {
        try {
            double n = Double.parseDouble(value.trim().replace(',', '.'));
            return Double.isFinite(n) && n >= 0 && n <= 1000000 && (key == Key.LYE || key == Key.ASH || n == Math.rint(n)) ? n : null;
        } catch(Exception ignored) { return null; }
    }
    private static String amountNumber(double n) {
        return java.math.BigDecimal.valueOf(n).round(new java.math.MathContext(12)).stripTrailingZeros().toPlainString();
    }
    private static String amount(Key key, double n) {
        return amountNumber(n) + " " + unit(key);
    }
    private static String unit(Key key) {
        return tr(key == Key.LYE || key == Key.ASH ? "kilograms" : key == Key.WATER || key == Key.SALT_WATER ? "litres" : "pieces");
    }
    static String number(double n) { return String.format(Locale.ROOT, "%.2f", n).replaceAll("\\.?0+$", ""); }
    private static String quality(double n) { return number(n) + " q"; }
    private static String label(Key key) { return QualityWorkshopCatalog.label(key); }
    private static String join(List<Key> keys) {
        List<String> names = new ArrayList<>();
        for(Key key : keys) names.add(label(key));
        return String.join(" · ", names);
    }
    private static void clear(Widget widget) { for(Widget child : new ArrayList<>(widget.children())) child.destroy(); }

    private void layout() {
        Coord s = csz();
        int gap = UI.scale(10), w = Math.max(1, (s.x - gap * 4) / 3);
        Button[] actions = {addResult, remember, refresh};
        for(int i = 0; i < actions.length; i++) {
            actions[i].move(Coord.of(gap + i * (w + gap), gap));
            actions[i].resize(Coord.of(w, actions[i].sz.y));
        }
        status.move(UI.scale(10, 46));
        status.tooltip = status.text();
        int y = UI.scale(78), left = Math.max(UI.scale(240), (s.x - gap * 3) * 43 / 100);
        left = Math.min(left, s.x / 2);
        inputPane.move(Coord.of(gap, y));
        inputPane.resize(Coord.of(left, Math.max(1, s.y - y - gap)));
        outputPane.move(Coord.of(left + gap * 2, y));
        outputPane.resize(Coord.of(Math.max(1, s.x - left - gap * 3), Math.max(1, s.y - y - gap)));
        rebuildInputs = rebuildOutputs = true;
        quantitiesChanged = true;
    }
    @Override public void resize(Coord size) { super.resize(size); if(inputPane != null) layout(); }

    private void changed(boolean structure) {
        rebuildInputs |= structure;
        rebuildOutputs = true;
        quantitiesChanged |= structure;
        saveAfter = 0.5;
    }

    private void buildInputs() {
        int scroll = inputPane.bar.val;
        clear(inputPane.cont);
        inputRows.clear();
        invalid.retainAll(model.inputs().keySet());
        drafts.keySet().retainAll(model.inputs().keySet());
        int width = inputPane.cont.sz.x - UI.scale(8), y = UI.scale(4);
        y = text(inputPane.cont, tr("inputs"), 0, y, width) + UI.scale(5);
        y = text(inputPane.cont, tr("inputs_hint"), 0, y, width) + UI.scale(10);
        y = buildQuantityPlan(width, y);
        Map<Key, List<Key>> inputs = model.inputs();
        for(Map.Entry<Key, List<Key>> entry : inputs.entrySet()) {
            if(!model.inputHidden(entry.getKey())) y = addInput(entry.getKey(), entry.getValue(), width, y);
        }
        int hidden = (int) inputs.keySet().stream().filter(model::inputHidden).count();
        if(hidden > 0) {
            y = hiddenToggle(inputPane.cont, width, y, hidden, true);
            if(model.hiddenInputsExpanded()) for(Map.Entry<Key, List<Key>> entry : inputs.entrySet())
                if(model.inputHidden(entry.getKey())) y = addInput(entry.getKey(), entry.getValue(), width, y);
        }
        if(inputs.isEmpty()) text(inputPane.cont, tr("empty"), 0, y, width);
        inputPane.cont.update(); inputPane.bar.val = Math.min(scroll, inputPane.bar.max); inputPane.bar.changed();
    }

    private int buildQuantityPlan(int width, int y) {
        boolean requested = model.watched().stream().anyMatch(key -> model.desiredAmount(key) > 0);
        if(!requested) return y;
        if(!quantities.materials.isEmpty()) {
            y = quantityHeading("quantity_materials", width, y + UI.scale(5));
            for(Map.Entry<Key, Double> entry : quantities.materials.entrySet())
                y = quantityLine(entry, width, y);
        }
        if(!quantities.equipment.isEmpty()) {
            y = quantityHeading("quantity_equipment", width, y + UI.scale(5));
            for(Map.Entry<Key, Double> entry : quantities.equipment.entrySet())
                y = quantityLine(entry, width, y);
        }
        return y + UI.scale(12);
    }

    private int quantityHeading(String key, int width, int y) {
        Label heading = inputPane.cont.add(new Label(tr(key), Math.max(1, width)), Coord.of(0, y));
        StringBuilder hint = new StringBuilder(tr("quantity_plan_hint"));
        for(String warning : quantities.warnings)
            hint.append('\n').append(tr("quantity_warning." + warning));
        heading.tooltip = hint.toString();
        return y + heading.sz.y + UI.scale(3);
    }

    private int quantityLine(Map.Entry<Key, Double> entry, int width, int y) {
        inputPane.cont.add(new ItemIcon(entry.getKey()), Coord.of(0, y));
        int end = text(inputPane.cont, label(entry.getKey()) + " × " + amount(entry.getKey(), entry.getValue()),
                UI.scale(36), y + UI.scale(3), width - UI.scale(40));
        return Math.max(end, y + UI.scale(33));
    }

    private int addInput(Key key, List<Key> users, int width, int y) {
        InputRow row = inputPane.cont.add(new InputRow(key, users, width), Coord.of(0, y));
        inputRows.put(key, row);
        return y + row.sz.y + UI.scale(8);
    }

    private int hiddenToggle(Widget parent, int width, int y, int count, boolean inputs) {
        boolean expanded = inputs ? model.hiddenInputsExpanded() : model.hiddenResultsExpanded();
        Button toggle = parent.add(new Button(width, (expanded ? "▼ " : "▶ ")
                + tr(inputs ? "hidden_inputs" : "hidden_results") + " (" + count + ")").action(() -> {
            if(inputs) model.setHiddenInputsExpanded(!model.hiddenInputsExpanded());
            else model.setHiddenResultsExpanded(!model.hiddenResultsExpanded());
            changed(true);
        }), Coord.of(0, y + UI.scale(4)));
        toggle.tooltip = tr("hidden_hint");
        return toggle.c.y + toggle.sz.y + UI.scale(8);
    }

    private final class InputRow extends Widget {
        final Key key;
        final TextEntry field;
        final Button hint;
        final Color tint = new Color(130, 68, 61, 65);
        boolean syncing;
        InputRow(Key key, List<Key> users, int width) {
            super(Coord.of(width, UI.scale(106)));
            this.key = key;
            add(new ItemIcon(key), UI.scale(5, 5));
            int fieldWidth = UI.scale(72), right = width - fieldWidth - UI.scale(5);
            int hintWidth = UI.scale(84), hintX = right - hintWidth - UI.scale(5);
            int titleBottom = text(this, label(key), UI.scale(40), UI.scale(4), hintX - UI.scale(45));
            field = add(new TextEntry(fieldWidth, drafts.getOrDefault(key, Double.toString(model.manual(key)))) {
                @Override protected void changed() {
                    super.changed();
                    if(syncing) return;
                    drafts.put(key, text());
                    Double n = parseQuality(text());
                    if(n == null || !model.setManual(key, n)) {
                        invalid.add(key); setStatus(tr("invalid"));
                    } else {
                        invalid.remove(key); QualityWorkshopWindow.this.changed(false);
                        setStatus(invalid.isEmpty() ? tr("intro") : tr("invalid"));
                    }
                }
            }, Coord.of(right, UI.scale(7)));
            field.autofocus = false;
            field.tooltip = tr(key == Key.STONE_WALL || key == Key.ORE_WALL ? "wall_hint" : "input_quality_hint");
            hint = add(new Button(hintWidth, "").action(this::applyHint), Coord.of(hintX, UI.scale(4)));
            updateHint();
            int rowY = Math.max(UI.scale(37), titleBottom + UI.scale(3));
            int hideWidth = UI.scale(70), actionWidth = model.isRecipe(key) ? UI.scale(100) : 0;
            int actionsX = width - hideWidth - UI.scale(5) - (actionWidth > 0 ? actionWidth + UI.scale(5) : 0);
            Label uses = add(new Label(join(users), Math.max(1, actionsX - UI.scale(10))), Coord.of(UI.scale(5), rowY));
            uses.tooltip = join(users);
            if(model.isRecipe(key)) add(new Button(actionWidth, tr("calculate")).action(() -> {
                model.watch(key); changed(true);
            }), Coord.of(actionsX, rowY));
            Button hide = add(new Button(hideWidth, tr(model.inputHidden(key) ? "restore" : "hide")).action(() -> {
                model.setInputHidden(key, !model.inputHidden(key)); changed(true);
            }), Coord.of(width - hideWidth - UI.scale(5), rowY));
            hide.tooltip = tr("hidden_hint");
            resize(Coord.of(width, rowY + Math.max(uses.sz.y, hide.sz.y) + UI.scale(5)));
        }
        void updateHint() {
            QualityWorkshopHints.Hint value = hintFor(key);
            hint.change(value == null ? "—" : "↑ " + number(value.quality));
            hint.disable(value == null);
            hint.tooltip = value == null ? tr("no_hint") : tr(QualityWorkshopCatalog.station(key) ? "atlas_hint" : QualityWorkshopCatalog.character(key) ? "character_hint" : "stock_hint")
                    + " " + value.name + " · " + quality(value.quality) + "\n" + tr("hint_only");
        }
        void applyHint() {
            QualityWorkshopHints.Hint value = hintFor(key);
            if(value == null || !model.setManual(key, value.quality)) return;
            drafts.remove(key);
            syncing = true; field.settext(Double.toString(value.quality)); syncing = false;
            invalid.remove(key); changed(false); setStatus(tr("hint_only"));
        }
        @Override public void draw(GOut g) {
            g.chcolor(invalid.contains(key) ? new Color(180, 45, 35, 120) : tint); g.frect(Coord.z, sz); g.chcolor();
            super.draw(g);
        }
    }

    private void buildOutputs() {
        int scroll = outputPane.bar.val;
        clear(outputPane.cont);
        resultRows.clear();
        amountDrafts.keySet().retainAll(model.watched());
        invalidAmounts.retainAll(model.watched());
        int width = outputPane.cont.sz.x - UI.scale(8), y = UI.scale(4);
        y = text(outputPane.cont, tr("outputs"), 0, y, width) + UI.scale(5);
        y = text(outputPane.cont, tr("outputs_hint"), 0, y, width) + UI.scale(10);
        for(Key key : model.watched()) {
            if(!model.resultHidden(key)) y = addResult(key, width, y);
        }
        if(model.watched().isEmpty()) y = text(outputPane.cont, tr("empty"), 0, y, width);
        if(model.watched().contains(Key.POTTER_CLAY) && !model.resultHidden(Key.POTTER_CLAY))
            y = buildIterations(width, y + UI.scale(8));
        int hidden = (int) model.watched().stream().filter(model::resultHidden).count();
        if(hidden > 0) {
            y = hiddenToggle(outputPane.cont, width, y, hidden, false);
            if(model.hiddenResultsExpanded()) {
                for(Key key : model.watched()) if(model.resultHidden(key)) y = addResult(key, width, y);
                if(model.watched().contains(Key.POTTER_CLAY) && model.resultHidden(Key.POTTER_CLAY))
                    buildIterations(width, y + UI.scale(8));
            }
        }
        outputPane.cont.update(); outputPane.bar.val = Math.min(scroll, outputPane.bar.max); outputPane.bar.changed();
    }

    private int addResult(Key key, int width, int y) {
        ResultRow row = outputPane.cont.add(new ResultRow(key, width), Coord.of(0, y));
        resultRows.put(key, row);
        y += row.sz.y + UI.scale(10);
        return key == Key.MINED_STONE ? buildMiningIterations(width, y) : y;
    }

    private final class ResultRow extends Widget {
        final Key key;
        final Label total;
        ResultRow(Key key, int width) {
            super(Coord.of(width, UI.scale(130)));
            this.key = key;
            add(new ItemIcon(key), UI.scale(5, 5));
            int y = text(this, label(key) + "  " + quality(model.value(key)), UI.scale(40), UI.scale(5), width - UI.scale(235));
            Button hide = add(new Button(UI.scale(70), tr(model.resultHidden(key) ? "restore" : "hide")).action(() -> {
                model.setResultHidden(key, !model.resultHidden(key)); changed(false);
            }), Coord.of(width - UI.scale(185), UI.scale(4)));
            hide.tooltip = tr("hidden_hint");
            Button remove = add(new Button(UI.scale(105), tr("remove")).action(() -> { model.unwatch(key); changed(true); }), Coord.of(width - UI.scale(110), UI.scale(4)));
            remove.tooltip = tr("remove_hint");
            y = Math.max(y, UI.scale(38));
            int amountWidth = UI.scale(90);
            int amountBottom = text(this, tr("desired_amount") + " (" + unit(key) + ")",
                    UI.scale(5), y, width - amountWidth - UI.scale(20));
            TextEntry desired = add(new TextEntry(amountWidth, amountDrafts.getOrDefault(key, amountNumber(model.desiredAmount(key)))) {
                @Override protected void changed() {
                    super.changed();
                    amountDrafts.put(key, text());
                    Double n = parseAmount(key, text());
                    if(n == null || !model.setDesiredAmount(key, n)) {
                        invalidAmounts.add(key); setStatus(tr("invalid_amount"));
                    } else {
                        invalidAmounts.remove(key);
                        quantitiesChanged = true; rebuildInputs = true; saveAfter = 0.5;
                        setStatus(invalidAmounts.isEmpty() ? tr("quantity_hint") : tr("invalid_amount"));
                    }
                }
            }, Coord.of(width - amountWidth - UI.scale(5), y));
            desired.autofocus = false; desired.tooltip = tr("quantity_hint");
            y = Math.max(amountBottom, y + desired.sz.y) + UI.scale(4);
            total = add(new Label("", width - UI.scale(10)), Coord.of(UI.scale(5), y));
            updateQuantity();
            y += UI.scale(38);
            Double baseline = model.baseline(key);
            if(baseline != null) y = text(this, tr("difference") + " " + String.format(Locale.ROOT, "%+.2f q", model.value(key) - baseline), UI.scale(5), y, width - UI.scale(10));
            y = text(this, tr("depends") + " " + join(model.dependencies(key)), UI.scale(5), y + UI.scale(4), width - UI.scale(10));
            y += UI.scale(6);
            if(key == Key.POTTER_CLAY) {
                int bottom = text(this, tr("potter_yield"), UI.scale(5), y, width - UI.scale(105));
                TextEntry output = add(new TextEntry(UI.scale(85), potterYieldDraft == null
                        ? Integer.toString(model.potterOutputPerCraft()) : potterYieldDraft) {
                    @Override protected void changed() {
                        super.changed();
                        potterYieldDraft = text();
                        Double n = parseAmount(Key.POTTER_CLAY, text());
                        if(n == null || n > 1000 || !model.setPotterOutputPerCraft(n.intValue())) {
                            invalidPotterYield = true; setStatus(tr("invalid_potter_yield"));
                        } else {
                            invalidPotterYield = false;
                            quantitiesChanged = true; rebuildInputs = true; saveAfter = 0.5;
                            setStatus(tr("potter_yield_hint"));
                        }
                    }
                }, Coord.of(width - UI.scale(90), y));
                output.autofocus = false; output.tooltip = tr("potter_yield_hint");
                y = Math.max(bottom, y + output.sz.y) + UI.scale(4);
            }
            if(key == Key.BONE_CLAY || key == Key.BRICK) {
                final List<Key> choices = key == Key.BONE_CLAY ? Arrays.asList(Key.PIT_CLAY, Key.SOAP_CLAY) : Arrays.asList(Key.BALL_CLAY, Key.BONE_CLAY, Key.SOAP_CLAY);
                final Key selected = key == Key.BONE_CLAY ? model.boneClaySource() : model.brickClaySource();
                Button source = add(new Button(width - UI.scale(10), tr("source") + " " + label(selected)).action(() -> {
                    Key next = choices.get((choices.indexOf(selected) + 1) % choices.size());
                    if(key == Key.BONE_CLAY) model.setBoneClaySource(next); else model.setBrickClaySource(next);
                    changed(true);
                }), Coord.of(UI.scale(5), y));
                source.tooltip = tr("source_hint"); y += source.sz.y + UI.scale(4);
            }
            if(key == Key.SOAP_CLAY || key == Key.LYE) {
                CheckBox clay = add(new CheckBox(tr("clay_cauldron")), Coord.of(UI.scale(5), y));
                clay.a = model.clayCauldron(); clay.changed(v -> { model.setClayCauldron(v); changed(true); });
                y += clay.sz.y + UI.scale(4);
            }
            if(key == Key.STACK_FURNACE) {
                List<Key> choices = Arrays.asList(Key.BALL_CLAY, Key.PIT_CLAY, Key.CAVE_CLAY, Key.ACRE_CLAY, Key.GRAY_CLAY,
                        Key.SOAP_CLAY, Key.BONE_CLAY, Key.POTTER_CLAY, Key.COADE_CLAY);
                Button source = add(new Button(width - UI.scale(10), tr("source") + " " + label(model.stackClaySource())).action(() -> {
                    model.setStackClaySource(choices.get((choices.indexOf(model.stackClaySource()) + 1) % choices.size()));
                    changed(true);
                }), Coord.of(UI.scale(5), y));
                source.tooltip = tr("source_hint"); y += source.sz.y + UI.scale(4);
            }
            if(key == Key.KILN) {
                Button clay = add(new Button(width - UI.scale(10), tr("kiln_copy_clay")).action(QualityWorkshopWindow.this::showKilnClayPicker), Coord.of(UI.scale(5), y));
                clay.tooltip = tr("kiln_clay_hint"); y += clay.sz.y + UI.scale(4);
                y = text(this, tr("kiln_clay_hint"), UI.scale(5), y, width - UI.scale(10));
            }
            if(key == Key.ANVIL) {
                List<Key> choices = Arrays.asList(Key.CASTING_MATERIAL, Key.SAND, Key.BAD_SAND,
                        Key.BALL_CLAY, Key.PIT_CLAY, Key.CAVE_CLAY, Key.ACRE_CLAY, Key.GRAY_CLAY,
                        Key.SOAP_CLAY, Key.BONE_CLAY, Key.POTTER_CLAY, Key.COADE_CLAY);
                Button material = add(new Button(width - UI.scale(10), tr("casting_source") + " " + label(model.anvilCastingSource())).action(() -> {
                    model.setAnvilCastingSource(choices.get((choices.indexOf(model.anvilCastingSource()) + 1) % choices.size()));
                    changed(true);
                }), Coord.of(UI.scale(5), y));
                material.tooltip = tr("casting_source_hint"); y += material.sz.y + UI.scale(4);
                Button furnace = add(new Button(width - UI.scale(10), tr("casting_furnace") + " " + label(model.anvilFurnace())).action(() -> {
                    model.setAnvilFurnace(model.anvilFurnace() == Key.ORE_SMELTER ? Key.STACK_FURNACE : Key.ORE_SMELTER);
                    changed(true);
                }), Coord.of(UI.scale(5), y));
                furnace.tooltip = tr("anvil_hint"); y += furnace.sz.y + UI.scale(4);
                y = text(this, tr("anvil_hint"), UI.scale(5), y, width - UI.scale(10));
            }
            if(key == Key.SMELTED_METAL) {
                Button furnace = add(new Button(width - UI.scale(10), tr("furnace") + " " + label(model.smeltingFurnace())).action(() -> {
                    model.setSmeltingFurnace(model.smeltingFurnace() == Key.ORE_SMELTER ? Key.STACK_FURNACE : Key.ORE_SMELTER);
                    changed(true);
                }), Coord.of(UI.scale(5), y));
                furnace.tooltip = tr("furnace_hint"); y += furnace.sz.y + UI.scale(4);
                Button ore = add(new Button(width - UI.scale(10), tr("ore_source") + " " + label(model.smeltingOreSource())).action(() -> {
                    model.setSmeltingOreSource(model.smeltingOreSource() == Key.ORE ? Key.MINED_ORE : Key.ORE);
                    changed(true);
                }), Coord.of(UI.scale(5), y));
                ore.tooltip = tr("ore_source_hint"); y += ore.sz.y + UI.scale(4);
                y = text(this, tr(model.smeltingFurnace() == Key.STACK_FURNACE ? "stack_smelting_hint" : "smelting_hint"), UI.scale(5), y, width - UI.scale(10));
            }
            if(key == Key.MINED_STONE || key == Key.MINED_ORE)
                y = text(this, tr("mining_hint"), UI.scale(5), y, width - UI.scale(10));
            resize(Coord.of(width, y + UI.scale(3)));
        }
        void updateQuantity() {
            double n = quantities == null ? 0 : quantities.production.getOrDefault(key, 0.0);
            double ready = quantities == null ? 0 : quantities.materials.getOrDefault(key, 0.0);
            total.settext(n > 0 ? tr("quantity_total") + " " + amount(key, n)
                    : ready > 0 ? tr("quantity_ready") + " " + amount(key, ready) : tr("quantity_only_quality"));
            total.tooltip = tr("quantity_total_hint");
        }
        @Override public void draw(GOut g) {
            g.chcolor(invalidAmounts.contains(key) || (key == Key.POTTER_CLAY && invalidPotterYield)
                    ? new Color(180, 45, 35, 120) : new Color(65, 121, 139, 60));
            g.frect(Coord.z, sz); g.chcolor(); super.draw(g);
        }
    }

    private int buildIterations(int width, int y) {
        y = text(outputPane.cont, tr("repeat_title"), 0, y, width) + UI.scale(8);
        List<Iteration> rows = model.iterations();
        for(int i = 0; i < rows.size(); i++) {
            Iteration row = rows.get(i);
            y = text(outputPane.cont, tr("craft_number") + " " + row.number, 0, y, width);
            if(i > 0) {
                y = text(outputPane.cont, tr("fire_previous") + " " + quality(rows.get(i - 1).clayQuality), 0, y, width);
                y = text(outputPane.cont, label(Key.KILN) + " " + quality(model.baseValue(Key.KILN)) + " · " + label(Key.FUEL) + " " + quality(model.manual(Key.FUEL)), 0, y, width);
            }
            y = text(outputPane.cont, label(Key.BRICK) + " " + quality(row.brickQuality) + " → " + label(Key.POTTER_CLAY) + " " + quality(row.clayQuality), 0, y, width);
            if(i > 0) y = text(outputPane.cont, tr("previous_difference") + " " + String.format(Locale.ROOT, "%+.2f q", row.clayQuality - rows.get(i - 1).clayQuality), 0, y, width);
            y += UI.scale(12);
        }
        Button next = outputPane.cont.add(new Button(width, tr("repeat")).action(() -> {
            model.setGenerations(model.generations() + 1); changed(true);
        }), Coord.of(0, y));
        next.disable(model.generations() >= 8); y += next.sz.y + UI.scale(6);
        if(model.generations() > 1) {
            Button less = outputPane.cont.add(new Button(width, tr("remove_iteration")).action(() -> {
                model.setGenerations(model.generations() - 1); changed(true);
            }), Coord.of(0, y)); y += less.sz.y + UI.scale(6);
        }
        return text(outputPane.cont, tr("repeat_hint"), 0, y, width) + UI.scale(8);
    }

    private int buildMiningIterations(int width, int y) {
        y = text(outputPane.cont, tr("mining_repeat_title"), 0, y, width) + UI.scale(5);
        List<MiningIteration> rows = model.miningIterations();
        for(int i = 0; i < rows.size(); i++) {
            MiningIteration row = rows.get(i);
            y = text(outputPane.cont, tr("mining_step") + " " + row.number, 0, y, width);
            if(i > 0) y = text(outputPane.cont, tr("mining_previous_stone") + " " + quality(row.inputStoneQuality), 0, y, width);
            y = text(outputPane.cont, label(Key.STONE_AXE) + " " + quality(row.axeQuality)
                    + " → " + label(Key.MINED_STONE) + " " + quality(row.stoneQuality), 0, y, width);
            if(i > 0) y = text(outputPane.cont, tr("previous_difference") + " "
                    + String.format(Locale.ROOT, "%+.2f q", row.stoneQuality - rows.get(i - 1).stoneQuality), 0, y, width);
            y += UI.scale(8);
        }
        Button next = outputPane.cont.add(new Button(width, tr("mining_repeat")).action(() -> {
            model.setMiningGenerations(model.miningGenerations() + 1); changed(true);
        }), Coord.of(0, y));
        next.disable(model.miningGenerations() >= 8); y += next.sz.y + UI.scale(6);
        if(model.miningGenerations() > 1) {
            Button less = outputPane.cont.add(new Button(width, tr("mining_remove_step")).action(() -> {
                model.setMiningGenerations(model.miningGenerations() - 1); changed(true);
            }), Coord.of(0, y)); y += less.sz.y + UI.scale(6);
        }
        return text(outputPane.cont, tr("mining_repeat_hint"), 0, y, width) + UI.scale(10);
    }

    private static int text(Widget parent, String text, int x, int y, int width) {
        Label label = parent.add(new Label(text, Math.max(1, width)), Coord.of(x, y));
        return y + label.sz.y + UI.scale(3);
    }
    private final class ItemIcon extends Widget {
        final Key key;
        ItemIcon(Key key) { super(UI.scale(30, 30)); this.key = key; tooltip = label(key); }
        @Override public void draw(GOut g) {
            String name = key == Key.SMELTED_METAL && model.smeltingFurnace() == Key.STACK_FURNACE
                    ? "Bar of Copper" : QualityWorkshopCatalog.name(key);
            Tex icon = icons.icon(QualityWorkshopCatalog.iconResource(key), name);
            if(icon != null) CraftAtlasIconCache.draw(g, icon, Coord.z, sz.x);
            else { g.chcolor(new Color(140, 145, 120, 70)); g.frect(UI.scale(4, 4), sz.sub(UI.scale(8, 8))); g.chcolor(); }
        }
    }

    private void showPicker() {
        if(picker != null) { picker.raise(); return; }
        picker = new Window(UI.scale(330, 360), tr("add")) {
            @Override public void wdgmsg(Widget sender, String message, Object... args) {
                if(sender == this && "close".equals(message)) { destroy(); return; }
                super.wdgmsg(sender, message, args);
            }
            @Override public void destroy() { super.destroy(); picker = null; }
        };
        Scrollport list = picker.add(new Scrollport(UI.scale(330, 360)), Coord.z);
        int y = 0;
        int group = -1;
        for(Key key : QualityWorkshopModel.recipes()) {
            int nextGroup = QualityWorkshopCatalog.group(key);
            if(nextGroup != group) {
                group = nextGroup;
                y = text(list.cont, tr("group_" + group), UI.scale(5), y + UI.scale(8), UI.scale(300)) + UI.scale(4);
            }
            Button option = list.cont.add(new Button(UI.scale(300), label(key)).action(() -> {
                model.watch(key); changed(true); if(picker != null) picker.destroy();
            }), Coord.of(0, y));
            option.disable(model.watched().contains(key)); y += UI.scale(36);
        }
        list.cont.update();
        if(parent != null) parent.add(picker, c.add(UI.scale(60, 60)));
    }

    private void showKilnClayPicker() {
        if(picker != null) { picker.raise(); return; }
        picker = new Window(UI.scale(360, 260), tr("kiln_copy_clay")) {
            @Override public void wdgmsg(Widget sender, String message, Object... args) {
                if(sender == this && "close".equals(message)) { destroy(); return; }
                super.wdgmsg(sender, message, args);
            }
            @Override public void destroy() { super.destroy(); picker = null; }
        };
        Scrollport list = picker.add(new Scrollport(UI.scale(360, 260)), Coord.z);
        int y = 0;
        for(Key clay : Arrays.asList(Key.BALL_CLAY, Key.PIT_CLAY, Key.CAVE_CLAY, Key.ACRE_CLAY, Key.GRAY_CLAY,
                Key.SOAP_CLAY, Key.BONE_CLAY, Key.POTTER_CLAY, Key.COADE_CLAY)) {
            double q = model.value(clay);
            list.cont.add(new Button(UI.scale(330), label(clay) + " · " + quality(q)).action(() -> {
                if(!model.setManual(Key.KILN_CLAY, q)) { setStatus(tr("invalid")); return; }
                drafts.remove(Key.KILN_CLAY); invalid.remove(Key.KILN_CLAY);
                changed(true); setStatus(tr("kiln_clay_copied"));
                if(picker != null) picker.destroy();
            }), Coord.of(0, y));
            y += UI.scale(36);
        }
        list.cont.update();
        if(parent != null) parent.add(picker, c.add(UI.scale(60, 60)));
    }

    private QualityWorkshopHints.Hint hintFor(Key key) {
        if(QualityWorkshopCatalog.station(key)) {
            Double q = atlas.requirementQualities.get(QualityWorkshopCatalog.stationKey(key, model.clayCauldron()));
            return q != null && Double.isFinite(q) && q >= 1 ? new QualityWorkshopHints.Hint(q, tr("atlas_hint")) : null;
        }
        if(QualityWorkshopCatalog.character(key)) {
            NGameUI gui = NUtils.getGameUI();
            if(gui == null || gui.chrwdg == null) return null;
            String attribute = QualityWorkshopCatalog.attribute(key);
            if(gui.chrwdg.battr != null) for(BAttrWnd.Attr a : gui.chrwdg.battr.attrs)
                if(attribute.equals(a.attr.nm) && a.attr.comp >= 1) return new QualityWorkshopHints.Hint(a.attr.comp, tr("character_hint"));
            if(gui.chrwdg.sattr != null) for(SAttrWnd.SAttr a : gui.chrwdg.sattr.attrs)
                if(attribute.equals(a.attr.nm) && a.attr.comp >= 1) return new QualityWorkshopHints.Hint(a.attr.comp, tr("character_hint"));
            return null;
        }
        return storageHints.get(key);
    }

    private static final class HintResult {
        final long request;
        final Map<Key, QualityWorkshopHints.Hint> values;
        final boolean failed;
        HintResult(long request, Map<Key, QualityWorkshopHints.Hint> values, boolean failed) {
            this.request = request; this.values = values; this.failed = failed;
        }
    }
    private void refreshHints() {
        for(InputRow row : inputRows.values()) row.updateHint();
        if(hintsLoading) return;
        if(!Boolean.TRUE.equals(NConfig.get(NConfig.Key.ndbenable)) || NCore.databaseManager == null || !NCore.databaseManager.isReady()) {
            storageHints = Collections.emptyMap();
            for(InputRow row : inputRows.values()) row.updateHint();
            setStatus(tr("no_database")); return;
        }
        final StorageItemService service = new StorageItemService(NCore.databaseManager);
        final Map<Key, Set<String>> names = new EnumMap<>(Key.class);
        final Set<String> all = new LinkedHashSet<>();
        for(Key key : Key.values()) { Set<String> values = QualityWorkshopCatalog.storageNames(key); names.put(key, values); all.addAll(values); }
        final long request = ++hintRequest;
        hintsLoading = true; refresh.disable(true); setStatus(tr("loading"));
        Thread thread = new Thread(() -> {
            try { pending.add(new HintResult(request, QualityWorkshopHints.best(names, service.loadStorageItemsByNames(all)), false)); }
            catch(Exception error) { pending.add(new HintResult(request, Collections.emptyMap(), true)); }
        }, "QualityWorkshopHints");
        thread.setDaemon(true); thread.start();
    }

    private void setStatus(String message) {
        status.settext(message); status.tooltip = message;
        status.resize(Coord.of(Math.max(1, csz().x - UI.scale(20)), status.sz.y));
    }
    @Override public void tick(double dt) {
        super.tick(dt);
        if(closed) return;
        HintResult result;
        while((result = pending.poll()) != null) if(result.request == hintRequest) {
            hintsLoading = false; refresh.disable(false); storageHints = result.values;
            for(InputRow row : inputRows.values()) row.updateHint();
            setStatus(result.failed ? tr("no_database") : tr("hint_only"));
        }
        if(quantitiesChanged) {
            quantitiesChanged = false;
            quantities = QualityWorkshopQuantities.calculate(model);
            for(ResultRow row : resultRows.values()) row.updateQuantity();
        }
        if(rebuildInputs) { rebuildInputs = false; buildInputs(); }
        if(rebuildOutputs) { rebuildOutputs = false; buildOutputs(); }
        if(saveAfter >= 0 && (saveAfter -= dt) < 0) save();
    }
    private void save() {
        try { QualityWorkshopStore.save(file, model); }
        catch(Exception error) { setStatus(tr("save_failed")); System.err.println("Unable to save quality workshop: " + error.getMessage()); }
    }
    @Override public void wdgmsg(Widget sender, String message, Object... args) {
        if(sender == this && "close".equals(message)) { destroy(); return; }
        super.wdgmsg(sender, message, args);
    }
    @Override public void destroy() {
        closed = true; ++hintRequest; save();
        if(picker != null) picker.destroy();
        super.destroy();
    }
    @Override public void dispose() { icons.dispose(); super.dispose(); }
}
