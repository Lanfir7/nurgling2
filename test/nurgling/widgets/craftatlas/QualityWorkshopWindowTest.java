package nurgling.widgets.craftatlas;

import haven.*;
import nurgling.craftatlas.CraftAtlasPreferences;
import nurgling.craftatlas.quality.QualityWorkshopModel;
import nurgling.craftatlas.quality.QualityWorkshopModel.Key;
import nurgling.i18n.L10n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QualityWorkshopWindowTest {
    @TempDir Path directory;
    @Test void acceptsDecimalCommaAndRejectsInvalidValuesWithoutCoercion() {
        assertEquals(120.25, QualityWorkshopWindow.parseQuality("120,25"));
        assertEquals(120.25, QualityWorkshopWindow.parseQuality("120.25"));
        for(String s : new String[]{"", "-1", "0", "NaN", "Infinity", "1e309", "100001", "abc"})
            assertNull(QualityWorkshopWindow.parseQuality(s), s);
        assertEquals("100", QualityWorkshopWindow.number(100));
    }
    @Test void widgetsKeepInputWhileResultsRecalculateAndSurviveResize() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        QualityWorkshopWindow window = new QualityWorkshopWindow(new CraftAtlasPreferences(), directory.resolve("ui.json"), model);
        try {
            window.tick(0);
            List<TextEntry> fields = descendants(window, TextEntry.class);
            assertFalse(fields.isEmpty());
            TextEntry field = fields.get(0);
            field.settext("123,5"); window.tick(0);
            assertTrue(descendants(window, TextEntry.class).contains(field), "result updates must not destroy focused input");
            field.settext("invalid"); window.resize(UI.scale(800, 500)); window.tick(0);
            assertTrue(descendants(window, TextEntry.class).stream().anyMatch(f -> f.text().equals("invalid")), "invalid draft survives structural rebuild");
            assertTrue(descendants(window, Scrollport.class).size() >= 2);
        } finally { window.destroy(); }
    }
    @Test void desiredCountsRecalculateWithoutDestroyingFocusedFieldAndPersistWithHiddenResults() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        for(Key key : new ArrayList<>(model.watched())) model.unwatch(key);
        model.watch(Key.ANVIL);
        Path saved = directory.resolve("amounts.json");
        QualityWorkshopWindow window = new QualityWorkshopWindow(new CraftAtlasPreferences(), saved, model);
        try {
            window.tick(0);
            Widget output = descendants(window, Scrollport.class).get(1).cont;
            TextEntry field = descendants(output, TextEntry.class).get(0);
            field.settext("3"); window.tick(0);
            assertEquals(3, model.desiredAmount(Key.ANVIL));
            assertTrue(descendants(output, TextEntry.class).contains(field), "count edit must retain focus");
            Widget input = descendants(window, Scrollport.class).get(0).cont;
            assertTrue(descendants(input, Label.class).stream().anyMatch(l -> l.text().contains("30")));
            assertTrue(descendants(input, Label.class).stream().anyMatch(l -> l.text().contains("15")));
            field.settext("3.5"); window.tick(0);
            assertEquals(3, model.desiredAmount(Key.ANVIL));
            window.resize(UI.scale(800, 500)); window.tick(0);
            assertEquals("3.5", descendants(output, TextEntry.class).get(0).text());
            button(output, "hide").click(); window.tick(0);
            assertEquals(3, model.desiredAmount(Key.ANVIL));
            assertTrue(descendants(input, Label.class).stream().anyMatch(l -> l.text().contains("30")));
            toggle(output, "hidden_results").click(); window.tick(0);
            TextEntry hidden = descendants(output, TextEntry.class).get(0);
            assertEquals("3.5", hidden.text());
            hidden.settext("2"); window.tick(0);
            assertEquals(2, model.desiredAmount(Key.ANVIL));
        } finally { window.destroy(); }
        QualityWorkshopModel restored = nurgling.craftatlas.quality.QualityWorkshopStore.load(saved);
        assertEquals(2, restored.desiredAmount(Key.ANVIL));
        assertTrue(restored.resultHidden(Key.ANVIL));
    }

    @Test void quantityFieldsAcceptPowderMassButRequireWholePieces() {
        assertEquals(0, QualityWorkshopWindow.parseAmount(Key.ANVIL, "0"));
        assertEquals(1000000, QualityWorkshopWindow.parseAmount(Key.ANVIL, "1000000"));
        assertEquals(0.5, QualityWorkshopWindow.parseAmount(Key.LYE, "0,5"));
        assertEquals(0.2, QualityWorkshopWindow.parseAmount(Key.ASH, "0,2"));
        for(String value : new String[]{"", "1.5", "-1", "NaN", "Infinity", "1000001"})
            assertNull(QualityWorkshopWindow.parseAmount(Key.ANVIL, value));
    }

    @Test void potterYieldCanBeEnteredWithoutLosingFocusOrItsInvalidDraft() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        for(Key key : new ArrayList<>(model.watched())) model.unwatch(key);
        model.watch(Key.POTTER_CLAY);
        model.setDesiredAmount(Key.POTTER_CLAY, 10);
        QualityWorkshopWindow window = new QualityWorkshopWindow(new CraftAtlasPreferences(), directory.resolve("yield.json"), model);
        try {
            window.tick(0);
            Widget output = descendants(window, Scrollport.class).get(1).cont;
            TextEntry yield = descendants(output, TextEntry.class).get(1);
            yield.settext("4"); window.tick(0);
            assertEquals(4, model.potterOutputPerCraft());
            assertTrue(descendants(output, TextEntry.class).contains(yield));
            yield.settext("4.5"); window.resize(UI.scale(800, 500)); window.tick(0);
            assertEquals(4, model.potterOutputPerCraft());
            assertEquals("4.5", descendants(output, TextEntry.class).get(1).text());
        } finally { window.destroy(); }
    }

    private static <T> List<T> descendants(Widget root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for(Widget child : root.children()) {
            if(type.isInstance(child)) result.add(type.cast(child));
            result.addAll(descendants(child, type));
        }
        return result;
    }

    @Test void hiddenInputsRemainEditableAndKeepDraftsAcrossCollapsing() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        QualityWorkshopWindow window = new QualityWorkshopWindow(new CraftAtlasPreferences(), directory.resolve("hidden-inputs.json"), model);
        try {
            window.tick(0);
            Widget pane = descendants(window, Scrollport.class).get(0).cont;
            TextEntry field = descendants(pane, TextEntry.class).get(0);
            Key key = model.inputs().keySet().iterator().next();
            int count = descendants(pane, TextEntry.class).size();
            field.settext("123,5");
            Button hint = descendants(field.parent, Button.class).stream()
                    .filter(b -> b.c.y < field.c.y + field.sz.y).findFirst().get();
            assertTrue(hint.c.x + hint.sz.x <= field.c.x, "suggestion sits beside quality");
            button(field.parent, "hide").click(); window.tick(0);
            assertTrue(model.inputHidden(key));
            assertEquals(123.5, model.manual(key));
            assertEquals(count - 1, descendants(pane, TextEntry.class).size());
            toggle(pane, "hidden_inputs").click(); window.tick(0);
            List<TextEntry> fields = descendants(pane, TextEntry.class);
            TextEntry hidden = fields.get(fields.size() - 1);
            assertEquals("123,5", hidden.text());
            hidden.settext("invalid");
            toggle(pane, "hidden_inputs").click(); window.tick(0);
            toggle(pane, "hidden_inputs").click(); window.tick(0);
            fields = descendants(pane, TextEntry.class);
            hidden = fields.get(fields.size() - 1);
            assertEquals("invalid", hidden.text());
            button(hidden.parent, "restore").click(); window.tick(0);
            assertFalse(model.inputHidden(key));
            assertEquals(count, descendants(pane, TextEntry.class).size());
        } finally { window.destroy(); }
    }

    @Test void hiddenResultsKeepDependenciesAndCanBeEditedInBottomSection() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        QualityWorkshopWindow window = new QualityWorkshopWindow(new CraftAtlasPreferences(), directory.resolve("hidden-results.json"), model);
        try {
            window.tick(0);
            Widget pane = descendants(window, Scrollport.class).get(1).cont;
            List<Key> watched = new ArrayList<>(model.watched());
            double quality = model.value(Key.SOAP_CLAY);
            button(pane, "hide").click(); window.tick(0);
            assertTrue(model.resultHidden(Key.SOAP_CLAY));
            assertEquals(watched, model.watched());
            assertEquals(quality, model.value(Key.SOAP_CLAY));
            assertFalse(descendants(pane, Button.class).stream().anyMatch(b -> b.text.text.equals(L10n.get("quality_workshop.restore"))));
            Button toggle = toggle(pane, "hidden_results");
            toggle.click(); window.tick(0);
            Widget hiddenRow = button(pane, "restore").parent;
            assertTrue(hiddenRow.c.y > toggle(pane, "hidden_results").c.y);
            CheckBox cauldron = descendants(hiddenRow, CheckBox.class).get(0);
            cauldron.set(true); window.tick(0);
            assertTrue(model.clayCauldron());
            button(pane, "restore").click(); window.tick(0);
            assertFalse(model.resultHidden(Key.SOAP_CLAY));
            assertTrue(descendants(pane, Label.class).stream().noneMatch(l -> l.text().contains("Ring of Brodgar")));
        } finally { window.destroy(); }
    }

    private static Button button(Widget parent, String key) {
        return descendants(parent, Button.class).stream().filter(b -> b.text.text.equals(L10n.get("quality_workshop." + key))).findFirst().get();
    }
    @Test void miningAndSmeltingControlsRebuildInputsAndRespectHiddenSections() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        for(Key key : new ArrayList<>(model.watched())) model.unwatch(key);
        model.watch(Key.MINED_STONE);
        model.watch(Key.SMELTED_METAL);
        model.watch(Key.KILN);
        model.setResultHidden(Key.MINED_STONE, true);
        QualityWorkshopWindow window = new QualityWorkshopWindow(new CraftAtlasPreferences(), directory.resolve("mining.json"), model);
        try {
            window.tick(0);
            assertFalse(descendants(window, Button.class).stream().anyMatch(b -> b.text.text.equals(L10n.get("quality_workshop.mining_repeat"))));
            toggle(window, "hidden_results").click(); window.tick(0);
            button(window, "mining_repeat").click(); window.tick(0);
            assertEquals(2, model.miningGenerations());
            assertTrue(model.inputs().containsKey(Key.BRANCH));
            assertTrue(model.inputs().containsKey(Key.SURVIVAL));
            assertTrue(model.inputs().containsKey(Key.KILN_CLAY));
            assertFalse(model.inputs().containsKey(Key.KILN));
            button(window, "mining_remove_step").click(); window.tick(0);
            assertEquals(1, model.miningGenerations());
            toggle(window, "furnace").click(); window.tick(0);
            assertEquals(Key.STACK_FURNACE, model.smeltingFurnace());
            assertTrue(model.inputs().containsKey(Key.FUEL));
            assertFalse(model.inputs().containsKey(Key.COAL));
            toggle(window, "ore_source").click(); window.tick(0);
            assertTrue(model.watched().contains(Key.MINED_ORE));
            assertTrue(model.inputs().containsKey(Key.ORE_WALL));
        } finally { window.destroy(); }
    }
    @Test void kilnCopiesClayOnceAndReplacesAnyOldInputDraft() throws Exception {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.watch(Key.KILN);
        QualityWorkshopWindow window = new QualityWorkshopWindow(new CraftAtlasPreferences(), directory.resolve("kiln.json"), model);
        try {
            window.tick(0);
            Widget input = descendants(window, Label.class).stream()
                    .filter(l -> l.text().equals(QualityWorkshopCatalog.label(Key.KILN_CLAY))).findFirst().get().parent;
            descendants(input, TextEntry.class).get(0).settext("80"); window.tick(0);
            double copied = model.value(Key.SOAP_CLAY);
            button(window, "kiln_copy_clay").click();
            java.lang.reflect.Field pickerField = QualityWorkshopWindow.class.getDeclaredField("picker");
            pickerField.setAccessible(true);
            Window picker = (Window) pickerField.get(window);
            descendants(picker, Button.class).stream()
                    .filter(b -> b.text.text.startsWith(QualityWorkshopCatalog.label(Key.SOAP_CLAY) + " ·"))
                    .findFirst().get().click(); window.tick(0);
            assertEquals(copied, model.value(Key.KILN));
            input = descendants(window, Label.class).stream()
                    .filter(l -> l.text().equals(QualityWorkshopCatalog.label(Key.KILN_CLAY))).findFirst().get().parent;
            assertEquals(Double.toString(copied), descendants(input, TextEntry.class).get(0).text());
            model.setManual(Key.CAULDRON, 1000);
            assertNotEquals(copied, model.value(Key.SOAP_CLAY));
            assertEquals(copied, model.value(Key.KILN), "new clay must not silently rebuild the kiln");
        } finally { window.destroy(); }
    }
    private static Button toggle(Widget parent, String key) {
        return descendants(parent, Button.class).stream().filter(b -> b.text.text.contains(L10n.get("quality_workshop." + key))).findFirst().get();
    }
}
