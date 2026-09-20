package nurgling.sessions;

import haven.Coord;
import haven.ICheckBox;
import haven.UI;
import haven.Widget;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.i18n.L10n;
import nurgling.widgets.BotsInterruptWidget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionMacroControlsTest {
    private NConfig previous;
    private nurgling.ClientResourceFixture resources;
    private SessionTabBar bar;
    private List<SessionContext> sessions;
    private int badgeX, stopX, badgeWidth, badgeHeight;

    @BeforeEach void setup() throws Exception {
        resources = new nurgling.ClientResourceFixture();
        previous = NConfig.current;
        if (previous == null) NConfig.current = new NConfig();
        bar = allocate(SessionTabBar.class);
        bar.sz = new Coord(600, 300);
        ICheckBox visibility = allocate(ICheckBox.class);
        visibility.a = true;
        field(SessionTabBar.class, "btnVis").set(bar, visibility);
        sessions = Arrays.asList(session("first", "second"), session("background"));
        stopX = SessionTabBar.BUTTON_WIDTH + constant("MACRO_LANE_GAP");
        badgeX = stopX + constant("MACRO_STOP_ALL_SIZE") + constant("MACRO_BADGE_GAP");
        badgeWidth = constant("MACRO_BADGE_WIDTH");
        badgeHeight = constant("MACRO_BADGE_HEIGHT");
    }

    @AfterEach void teardown() throws Exception {
        NConfig.current = previous;
        if (resources != null) resources.close();
    }

    @Test void entirePlateTargetsTheSameMacroAndGapsPassThrough() throws Exception {
        Object left = hit(badgeX + 1, UI.scale(4) + 1);
        Object right = hit(badgeX + badgeWidth - 1, UI.scale(4) + badgeHeight - 1);
        assertSame(value(left, "bot"), value(right, "bot"));
        assertSame(sessions.get(0), value(left, "context"));
        assertNull(hit(badgeX - 1, UI.scale(8)), "gap before the badge");
        assertNull(hit(badgeX + 1, UI.scale(4) + badgeHeight), "gap between macros");
        assertNull(hit(badgeX + badgeWidth, UI.scale(8)), "right edge is outside");
    }

    @Test void backgroundRowAndStopAllKeepTheirSessionIdentity() throws Exception {
        int row = (Integer) method("rowHeight", SessionContext.class).invoke(bar, sessions.get(0));
        int secondY = row + SessionTabBar.BUTTON_PADDING + UI.scale(4);
        Object macro = hit(badgeX + 2, secondY + 2);
        Object stop = hit(stopX + 2, secondY + 2);
        assertSame(sessions.get(1), value(macro, "context"));
        assertSame(sessions.get(1), value(stop, "context"));
        assertEquals(true, value(stop, "stopAll"));
        assertNull(value(stop, "bot"));

        field(SessionTabBar.class, "scrollY").setInt(bar, row + SessionTabBar.BUTTON_PADDING);
        assertSame(sessions.get(1), value(hit(badgeX + 2, UI.scale(6)), "context"));
    }

    @Test void hiddenControlsDoNotConsumeMouseOrScroll() throws Exception {
        ((ICheckBox) field(SessionTabBar.class, "btnVis").get(bar)).a = false;
        field(SessionTabBar.class, "contentHeight").setInt(bar, 1000);
        assertFalse(bar.mousedown(new Widget.MouseDownEvent(new Coord(badgeX + 2, UI.scale(6)), 1)));
        assertFalse(bar.mousewheel(new Widget.MouseWheelEvent(new Coord(badgeX + 2, UI.scale(6)), 1, 1)));
    }

    @Test void overflowScrollStopsAtContentBounds() throws Exception {
        field(SessionTabBar.class, "contentHeight").setInt(bar, 400);
        assertTrue(bar.mousewheel(new Widget.MouseWheelEvent(new Coord(badgeX + 2, UI.scale(6)), 100, 100)));
        assertEquals(100, field(SessionTabBar.class, "scrollY").getInt(bar));
        assertTrue(bar.mousewheel(new Widget.MouseWheelEvent(new Coord(badgeX + 2, UI.scale(6)), -100, -100)));
        assertEquals(0, field(SessionTabBar.class, "scrollY").getInt(bar));
    }

    @Test void toggleThreadNameResolvesTheRegisteredBotDisplayName() throws Exception {
        String display = (String) method("displayBotName", String.class).invoke(bar, "forager-ToggleThread");

        assertEquals(L10n.get("bot.forager.title"), display);
    }

    @Test void botIconSpansBothTextLinesWithoutTouchingThePlateBorder() throws Exception {
        int iconSize = constant("MACRO_ICON_SIZE");

        assertTrue(iconSize >= UI.scale(28));
        assertTrue(iconSize <= constant("MACRO_BADGE_HEIGHT") - UI.scale(8));
    }

    private SessionContext session(String... names) throws Exception {
        final NGameUI gui = allocate(NGameUI.class);
        gui.biw = new BotsInterruptWidget();
        for (String name : names) gui.biw.addObserve(new Thread(() -> {}, name));
        return new SessionContext() { @Override public NGameUI getGameUI() { return gui; } };
    }

    private Object hit(int x, int y) throws Exception {
        return method("getMacroHit", Coord.class, List.class).invoke(bar, new Coord(x, y), sessions);
    }

    private static Object value(Object object, String name) throws Exception {
        assertNotNull(object);
        return field(object.getClass(), name).get(object);
    }

    private static int constant(String name) throws Exception { return field(SessionTabBar.class, name).getInt(null); }
    private static Method method(String name, Class<?>... types) throws Exception {
        Method method = SessionTabBar.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(((Unsafe) field(Unsafe.class, "theUnsafe").get(null)).allocateInstance(type));
    }
}
