package nurgling.widgets;

import haven.CheckBox;
import haven.Widget;
import nurgling.ClientResourceFixture;
import nurgling.NConfig;
import nurgling.i18n.L10n;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsSearchWindowTest {
    @Test
    void translatedNestedOptionRetainsItsWidgetAndOpensInactivePanel() throws Exception {
        try(ClientResourceFixture resources = new ClientResourceFixture()) {
            NConfig previousConfig = NConfig.current;
            Locale previousLocale = L10n.getLocale();
            NConfig.current = new NConfig();
            try {
                L10n.setLanguage("ru");
                NSettingsWindow window = new NSettingsWindow();
                CheckBox target = window.qol.children(CheckBox.class).stream()
                        .filter(box -> L10n.get("qol.night_vision").equals(box.label()))
                        .findFirst().orElseThrow();

                Method build = NSettingsWindow.class.getDeclaredMethod("buildCatalog");
                build.setAccessible(true);
                List<?> catalog = (List<?>)build.invoke(window);
                List<SettingsSearch.Entry> entries = new ArrayList<>();
                Object targetHit = null;
                for(Object hit : catalog) {
                    Field widget = hit.getClass().getDeclaredField("widget");
                    Field entry = hit.getClass().getDeclaredField("entry");
                    widget.setAccessible(true);
                    entry.setAccessible(true);
                    entries.add((SettingsSearch.Entry)entry.get(hit));
                    if(widget.get(hit) == target)
                        targetHit = hit;
                }
                assertTrue(targetHit != null, "The nested option must retain its live widget");
                assertSame(((SettingsSearch.Entry)field(targetHit, "entry")),
                        SettingsSearch.query(entries, "нчзр").get(0).entry);
                assertSame(((SettingsSearch.Entry)field(targetHit, "entry")),
                        SettingsSearch.query(entries, "night vision").get(0).entry);

                Widget branch = target.parent;
                branch.hide();
                List<?> hiddenCatalog = (List<?>)build.invoke(window);
                assertTrue(hiddenCatalog.stream().noneMatch(hit -> {
                    try {
                        return field(hit, "widget") == target;
                    } catch(Exception error) {
                        throw new AssertionError(error);
                    }
                }), "Hidden settings must not be suggested");
                branch.show();

                assertTrue(window.currentPanel != window.qol);
                Method open = NSettingsWindow.class.getDeclaredMethod("openHit", targetHit.getClass());
                open.setAccessible(true);
                open.invoke(window, targetHit);
                assertSame(window.qol, window.currentPanel);
                assertTrue(target.parent != null);
            } finally {
                L10n.setLocale(previousLocale);
                NConfig.current = previousConfig;
            }
        }
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
