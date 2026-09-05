package nurgling.widgets.craftatlas;

import nurgling.craftatlas.CraftAtlasEntry;
import nurgling.tools.VSpec;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasMaterialPickerTest {
    @Test
    void resolvesVSpecEntriesForEveryMaterialIcon() {
        String group = "CraftAtlasMaterialPickerStrings";
        ArrayList<JSONObject> members = new ArrayList<>();
        members.add(new JSONObject().put("name", "Taproot").put("static", "gfx/invobjs/taproot"));
        members.add(new JSONObject().put("name", "Yarn").put("static", "gfx/invobjs/yarn"));
        VSpec.categories.put(group, members);
        try {
            CraftAtlasEntry.InputSlot slot = new CraftAtlasEntry.InputSlot(1, false, List.of(
                    new CraftAtlasEntry.IngredientOption("gfx/invobjs/string", group)));

            List<CraftAtlasMaterialPicker.Option> options = CraftAtlasMaterialPicker.optionsFor(
                    slot, List.of("Taproot", "Yarn"));

            assertEquals(List.of("Taproot", "Yarn"),
                    options.stream().map(option -> option.name).toList());
            assertEquals("gfx/invobjs/taproot", options.get(0).spec.getString("static"));
            assertEquals("gfx/invobjs/yarn", options.get(1).spec.getString("static"));
        } finally {
            VSpec.categories.remove(group);
        }
    }
}
