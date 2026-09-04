package nurgling.widgets.craftatlas;

import haven.Coord;
import haven.GOut;
import haven.UI;
import haven.Widget;
import nurgling.craftatlas.CraftAtlasController;
import nurgling.craftatlas.CraftAtlasEntry;
import nurgling.craftatlas.CraftRecipeGraph;
import nurgling.i18n.L10n;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Recipe card and its deterministic clickable row map. */
public class CraftAtlasDetails extends Widget {
    public enum Target { NONE, INGREDIENT, REQUIREMENT_DESCRIPTION, CYCLE }
    public enum Kind { BONUS, INPUT, REQUIREMENT, STATUS }

    public static final class DetailRow {
        public final Kind kind;
        public final String name, resource;
        public final String value;
        public final int quantity;
        public final Target target;
        public final CraftAtlasEntry.Requirement requirement;
        DetailRow(Kind kind, String name, String resource, String value, int quantity, Target target,
                  CraftAtlasEntry.Requirement requirement) {
            this.kind = kind; this.name = name; this.resource = resource; this.value = value;
            this.quantity = quantity; this.target = target; this.requirement = requirement;
        }
    }

    private final CraftAtlasController controller;
    private CraftAtlasEntry entry;
    private CraftAtlasEntry.Requirement requirementDescription;
    private String cycleResource;
    private List<DetailRow> rows = Collections.emptyList();
    private int scroll;
    private final Map<String, Integer> savedScroll = new HashMap<>();
    private final int rowHeight = UI.scale(28);

    public CraftAtlasDetails(Coord size, CraftAtlasController controller) { super(size); this.controller = controller; }

    public void setEntry(CraftAtlasEntry value) {
        String previous = entry == null ? null : entry.recipeResource;
        String next = value == null ? null : value.recipeResource;
        if(previous != null && !previous.equals(next)) savedScroll.put(previous, scroll);
        entry = value;
        rows = value == null ? Collections.<DetailRow>emptyList() :
                buildRows(value, (resource, name) -> controller.linkState(resource, name));
        if(previous == null || !previous.equals(next)) scroll = next == null ? 0 : savedScroll.getOrDefault(next, 0);
    }

    public void setState(CraftAtlasController.ViewState state) {
        setEntry(state.selected);
        cycleResource = state.cycleResource;
        requirementDescription = state.requirementDescription;
    }

    public static List<DetailRow> buildRows(CraftAtlasEntry entry, Function<String, CraftRecipeGraph.LinkState> links) {
        return buildRows(entry, (resource, name) -> links.apply(resource));
    }

    public static List<DetailRow> buildRows(CraftAtlasEntry entry,
                                             BiFunction<String, String, CraftRecipeGraph.LinkState> links) {
        List<DetailRow> rows = new ArrayList<>();
        for(CraftAtlasEntry.Bonus bonus : entry.bonuses)
            rows.add(new DetailRow(Kind.BONUS, bonus.name, bonus.attributeResource,
                    bonus.value == null ? null : format(bonus.value), 0, Target.NONE, null));
        for(CraftAtlasEntry.InputSlot slot : entry.inputs) for(CraftAtlasEntry.IngredientOption option : slot.options) {
            CraftRecipeGraph.LinkState state = links.apply(option.resource, option.name);
            Target target = state == CraftRecipeGraph.LinkState.NONE ? Target.NONE :
                    state == CraftRecipeGraph.LinkState.CYCLE ? Target.CYCLE : Target.INGREDIENT;
            rows.add(new DetailRow(Kind.INPUT, option.name, option.resource, null, slot.quantity, target, null));
        }
        for(CraftAtlasEntry.Requirement requirement : entry.requirements) {
            Target target;
            if(requirement.kind == CraftAtlasEntry.RequirementKind.SKILL || requirement.kind == CraftAtlasEntry.RequirementKind.DISCOVERY)
                target = Target.REQUIREMENT_DESCRIPTION;
            else {
                CraftRecipeGraph.LinkState state = links.apply(requirement.resource, requirement.name);
                target = state == CraftRecipeGraph.LinkState.NONE ? Target.NONE :
                        state == CraftRecipeGraph.LinkState.CYCLE ? Target.CYCLE : Target.INGREDIENT;
            }
            rows.add(new DetailRow(Kind.REQUIREMENT, requirement.name, requirement.resource, requirement.kind.name(), 0, target, requirement));
        }
        return Collections.unmodifiableList(rows);
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.format("%+.0f", value) : String.format("%+.1f", value);
    }

    @Override public void draw(GOut g) {
        g.chcolor(new Color(24, 29, 33, 235)); g.frect(Coord.z, sz); g.chcolor();
        if(entry == null) { g.text(L10n.get("craft_atlas.no_recipe"), UI.scale(16, 22)); return; }
        g.text(entry.displayName, UI.scale(16, 22));
        g.text(L10n.get(statusKey(entry.availability)), UI.scale(16, 45));
        String notice = requirementDescription != null ? requirementDescription.description :
                cycleResource != null ? L10n.get("craft_atlas.cycle") : null;
        if(notice != null && !notice.isEmpty()) g.text(notice, UI.scale(16, 66));
        else g.text(L10n.get("craft_atlas.inputs") + " / " + L10n.get("craft_atlas.requirements"), UI.scale(16, 66));
        int y0 = UI.scale(82) - scroll;
        for(int i = 0; i < rows.size(); i++) {
            int y = y0 + i * rowHeight;
            if(y + rowHeight < 0 || y > sz.y - UI.scale(42)) continue;
            DetailRow row = rows.get(i);
            if(row.target != Target.NONE) { g.chcolor(new Color(48, 71, 78, 170)); g.frect(Coord.of(UI.scale(8), y), Coord.of(sz.x - UI.scale(16), rowHeight - 1)); g.chcolor(); }
            String prefix = row.kind == Kind.INPUT ? (row.quantity + " \u00d7 ") : row.kind == Kind.REQUIREMENT ?
                    L10n.get("craft_atlas.requirement." + row.value.toLowerCase()) + ": " : "+ ";
            String suffix = row.kind == Kind.REQUIREMENT || row.value == null ? "" : "   " + row.value;
            if(row.target != Target.NONE) suffix += "  \u203a";
            g.text(prefix + row.name + suffix, Coord.of(UI.scale(16), y + UI.scale(19)));
        }
        super.draw(g);
    }

    static String statusKey(CraftAtlasEntry.Availability availability) {
        if(availability == CraftAtlasEntry.Availability.OPEN) return "craft_atlas.status.open";
        if(availability == CraftAtlasEntry.Availability.REFERENCE_ONLY) return "craft_atlas.status.reference";
        return "craft_atlas.status.unavailable";
    }

    @Override public boolean mousedown(MouseDownEvent ev) {
        if(ev.b != 1 || entry == null) return super.mousedown(ev);
        int idx = (ev.c.y - UI.scale(82) + scroll) / rowHeight;
        if(idx < 0 || idx >= rows.size()) return super.mousedown(ev);
        DetailRow row = rows.get(idx);
        if(row.target == Target.INGREDIENT || row.target == Target.CYCLE) {
            if(row.requirement != null) controller.openRequirement(row.requirement);
            else controller.openIngredient(row.resource, row.name);
            return true;
        }
        if(row.target == Target.REQUIREMENT_DESCRIPTION) { controller.openRequirement(row.requirement); return true; }
        return super.mousedown(ev);
    }

    @Override public boolean mousewheel(MouseWheelEvent ev) {
        int max = Math.max(0, UI.scale(82) + rows.size() * rowHeight - Math.max(1, sz.y - UI.scale(42)));
        scroll = Math.max(0, Math.min(max, scroll + ev.a * rowHeight));
        if(entry != null) savedScroll.put(entry.recipeResource, scroll);
        return true;
    }
}
