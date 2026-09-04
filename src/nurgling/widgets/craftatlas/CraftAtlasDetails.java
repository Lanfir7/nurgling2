package nurgling.widgets.craftatlas;

import haven.Coord;
import haven.GOut;
import haven.UI;
import haven.Widget;
import nurgling.craftatlas.CraftAtlasController;
import nurgling.craftatlas.CraftAtlasEntry;
import nurgling.craftatlas.CraftRecipeGraph;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private List<DetailRow> rows = Collections.emptyList();
    private int scroll;
    private final int rowHeight = UI.scale(28);

    public CraftAtlasDetails(Coord size, CraftAtlasController controller) { super(size); this.controller = controller; }

    public void setEntry(CraftAtlasEntry value) {
        entry = value;
        rows = value == null ? Collections.<DetailRow>emptyList() : buildRows(value, controller::linkState);
        scroll = 0;
    }

    public static List<DetailRow> buildRows(CraftAtlasEntry entry, Function<String, CraftRecipeGraph.LinkState> links) {
        List<DetailRow> rows = new ArrayList<>();
        for(CraftAtlasEntry.Bonus bonus : entry.bonuses)
            rows.add(new DetailRow(Kind.BONUS, bonus.name, bonus.attributeResource,
                    bonus.value == null ? "?" : format(bonus.value), 0, Target.NONE, null));
        for(CraftAtlasEntry.InputSlot slot : entry.inputs) for(CraftAtlasEntry.IngredientOption option : slot.options) {
            CraftRecipeGraph.LinkState state = links.apply(option.resource);
            Target target = state == CraftRecipeGraph.LinkState.NONE ? Target.NONE :
                    state == CraftRecipeGraph.LinkState.CYCLE ? Target.CYCLE : Target.INGREDIENT;
            rows.add(new DetailRow(Kind.INPUT, option.name, option.resource, null, slot.quantity, target, null));
        }
        for(CraftAtlasEntry.Requirement requirement : entry.requirements) {
            Target target;
            if(requirement.kind == CraftAtlasEntry.RequirementKind.SKILL || requirement.kind == CraftAtlasEntry.RequirementKind.DISCOVERY)
                target = Target.REQUIREMENT_DESCRIPTION;
            else {
                CraftRecipeGraph.LinkState state = links.apply(requirement.resource);
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
        if(entry == null) { g.text("Select a recipe", UI.scale(16, 22)); return; }
        g.text(entry.displayName, UI.scale(16, 22));
        g.text(entry.availability.name(), UI.scale(16, 45));
        int y0 = UI.scale(70) - scroll;
        for(int i = 0; i < rows.size(); i++) {
            int y = y0 + i * rowHeight;
            if(y + rowHeight < 0 || y > sz.y - UI.scale(42)) continue;
            DetailRow row = rows.get(i);
            if(row.target != Target.NONE) { g.chcolor(new Color(48, 71, 78, 170)); g.frect(Coord.of(UI.scale(8), y), Coord.of(sz.x - UI.scale(16), rowHeight - 1)); g.chcolor(); }
            String prefix = row.kind == Kind.INPUT ? (row.quantity + " × ") : row.kind == Kind.REQUIREMENT ? "• " : "+ ";
            String suffix = row.value == null ? "" : "   " + row.value;
            if(row.target != Target.NONE) suffix += "  ›";
            g.text(prefix + row.name + suffix, Coord.of(UI.scale(16), y + UI.scale(19)));
        }
        super.draw(g);
    }

    @Override public boolean mousedown(MouseDownEvent ev) {
        if(ev.b != 1 || entry == null) return super.mousedown(ev);
        int idx = (ev.c.y - UI.scale(70) + scroll) / rowHeight;
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
        int max = Math.max(0, UI.scale(70) + rows.size() * rowHeight - Math.max(1, sz.y - UI.scale(42)));
        scroll = Math.max(0, Math.min(max, scroll + ev.a * rowHeight));
        return true;
    }
}
