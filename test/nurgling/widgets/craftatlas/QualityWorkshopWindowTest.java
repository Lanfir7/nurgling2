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
    private static Button toggle(Widget parent, String key) {
        return descendants(parent, Button.class).stream().filter(b -> b.text.text.contains(L10n.get("quality_workshop." + key))).findFirst().get();
    }
}
