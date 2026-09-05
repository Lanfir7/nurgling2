package nurgling.widgets.craftatlas;

import haven.Coord;
import haven.Defer;
import haven.GOut;
import haven.Inventory;
import haven.Loading;
import haven.Tex;
import haven.TexI;
import haven.UI;
import haven.Widget;
import haven.res.lib.itemtex.ItemTex;
import nurgling.craftatlas.CraftAtlasEntry;
import nurgling.tools.VSpec;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Icon grid used to select one concrete member of a VSpec ingredient group. */
final class CraftAtlasMaterialPicker extends Widget {
    static final class Option {
        final String name;
        final String resource;
        final JSONObject spec;

        Option(String name, String resource, JSONObject spec) {
            this.name = name;
            this.resource = resource;
            this.spec = spec;
        }
    }

    private static final int COLUMNS = 8;
    private static final int GAP = UI.scale(2);
    private static final int MARGIN = UI.scale(8);
    private static final int CELL = Inventory.sqsz.x;

    private final List<Option> options;
    private final String selected;
    private final Consumer<String> listener;
    private final Runnable closeListener;
    private final CraftAtlasIconCache fallbackIcons = new CraftAtlasIconCache();
    private final Map<Option, Defer.Future<Tex>> pending = new IdentityHashMap<>();
    private final Map<Option, Tex> loaded = new IdentityHashMap<>();
    private UI.Grab mouseGrab;
    private boolean closed;

    CraftAtlasMaterialPicker(List<Option> options, String selected,
                             Consumer<String> listener, Runnable closeListener) {
        super(sizeFor(options == null ? 0 : options.size()));
        this.options = options == null ? Collections.emptyList() : options;
        this.selected = selected;
        this.listener = listener;
        this.closeListener = closeListener;
        for(Option option : this.options) {
            if(option.spec != null)
                pending.put(option, Defer.later(() -> texture(option.spec)));
        }
    }

    static List<Option> optionsFor(CraftAtlasEntry.InputSlot slot, List<String> allowedMaterials) {
        if(slot == null || allowedMaterials == null || allowedMaterials.isEmpty())
            return Collections.emptyList();
        Map<String, JSONObject> specs = new LinkedHashMap<>();
        Map<String, String> resources = new HashMap<>();
        for(CraftAtlasEntry.IngredientOption ingredient : slot.options) {
            if(ingredient == null || ingredient.name == null) continue;
            List<JSONObject> category = VSpec.categories.get(ingredient.name);
            if(category != null) for(JSONObject value : category) {
                if(value == null) continue;
                String name = value.optString("name", null);
                if(name != null) specs.putIfAbsent(name, value);
            } else {
                resources.putIfAbsent(ingredient.name, ingredient.resource);
            }
        }
        List<Option> result = new ArrayList<>();
        for(String material : allowedMaterials) {
            JSONObject spec = specs.get(material);
            String resource = spec == null ? resources.get(material) : spec.optString("static", null);
            if(resource == null) resource = VSpec.getIconPath(material);
            result.add(new Option(material, resource, spec));
        }
        return Collections.unmodifiableList(result);
    }

    private static Coord sizeFor(int count) {
        int columns = Math.max(1, Math.min(COLUMNS, count));
        int rows = Math.max(1, (count + COLUMNS - 1) / COLUMNS);
        return Coord.of(MARGIN * 2 + columns * CELL + Math.max(0, columns - 1) * GAP,
                MARGIN * 2 + rows * CELL + Math.max(0, rows - 1) * GAP);
    }

    private static Tex texture(JSONObject spec) {
        BufferedImage image = ItemTex.create(spec);
        return image == null ? null : new TexI(image);
    }

    @Override protected void added() {
        mouseGrab = ui.grabmouse(this);
    }

    @Override public void draw(GOut g) {
        g.chcolor(new Color(13, 19, 22, 245));
        g.frect(Coord.z, sz);
        g.chcolor(new Color(193, 145, 55, 230));
        g.rect(Coord.z, sz.sub(1, 1));
        g.chcolor();
        for(int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            Coord at = cellAt(i);
            g.chcolor(new Color(5, 10, 13, 230));
            g.frect(at, Coord.of(CELL, CELL));
            g.chcolor();
            CraftAtlasIconCache.draw(g, icon(option), at.add(UI.scale(2), UI.scale(2)), CELL - UI.scale(4));
            if(option.name.equals(selected)) {
                g.chcolor(new Color(224, 177, 72));
                g.rect(at, Coord.of(CELL - 1, CELL - 1));
                g.chcolor();
            }
        }
        super.draw(g);
    }

    private Tex icon(Option option) {
        Tex ready = loaded.get(option);
        if(ready != null) return ready;
        Defer.Future<Tex> future = pending.get(option);
        if(future != null) {
            try {
                ready = future.get();
                pending.remove(option);
                if(ready != null) loaded.put(option, ready);
                if(ready != null) return ready;
            } catch(Loading waiting) {
                return fallbackIcons.icon(option.resource, option.name);
            } catch(Defer.DeferredException failed) {
                pending.remove(option);
            }
        }
        return fallbackIcons.icon(option.resource, option.name);
    }

    @Override public boolean mousedown(MouseDownEvent ev) {
        if(ev.b != 1 || !ev.c.isect(Coord.z, sz)) {
            destroy();
            return true;
        }
        for(int i = 0; i < options.size(); i++) {
            if(ev.c.isect(cellAt(i), Coord.of(CELL, CELL))) {
                if(listener != null) listener.accept(options.get(i).name);
                destroy();
                return true;
            }
        }
        return true;
    }

    @Override public Object tooltip(Coord c, Widget prev) {
        for(int i = 0; i < options.size(); i++)
            if(c.isect(cellAt(i), Coord.of(CELL, CELL))) return options.get(i).name;
        return null;
    }

    private Coord cellAt(int index) {
        int column = index % COLUMNS;
        int row = index / COLUMNS;
        return Coord.of(MARGIN + column * (CELL + GAP), MARGIN + row * (CELL + GAP));
    }

    @Override public void dispose() {
        if(mouseGrab != null) {
            mouseGrab.remove();
            mouseGrab = null;
        }
        for(Defer.Future<Tex> future : pending.values()) future.cancel();
        pending.clear();
        for(Tex texture : loaded.values()) texture.dispose();
        loaded.clear();
        fallbackIcons.dispose();
        if(!closed) {
            closed = true;
            if(closeListener != null) closeListener.run();
        }
        super.dispose();
    }
}
