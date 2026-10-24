package nurgling.sessions;

import haven.*;
import nurgling.*;
import nurgling.widgets.BotsInterruptWidget;
import org.junit.jupiter.api.*;
import sun.misc.Unsafe;

import java.lang.reflect.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SessionTabMuteTest {
    private NConfig previousConfig;
    private nurgling.ClientResourceFixture resources;
    private SessionManager manager;
    private Map<String, SessionContext> sessions;
    private Map<String, SessionContext> previousSessions;
    private SessionContext previousActive;
    private SessionTabBar bar;
    private RecordingContext first, second;

    private static class RecordingContext extends SessionContext {
        boolean muted;
        NGameUI gui;
        @Override public boolean isMuted() { return muted; }
        @Override public void setMuted(boolean value) { muted = value; }
        @Override public NGameUI getGameUI() { return gui; }
    }

    private static class InertCheckBox extends ICheckBox {
        private InertCheckBox() { super((Tex) null, (Tex) null, (Tex) null); }
        @Override public boolean mousedown(Widget.MouseDownEvent event) { return false; }
    }

    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        resources = new nurgling.ClientResourceFixture();
        previousConfig = NConfig.current;
        NConfig.current = new NConfig();
        manager = SessionManager.getInstance();
        sessions = (Map<String, SessionContext>) field(SessionManager.class, "sessions").get(manager);
        previousSessions = new LinkedHashMap<>(sessions);
        previousActive = (SessionContext) field(SessionManager.class, "activeSession").get(manager);
        sessions.clear();
        first = context("First", 2);
        second = context("Second", 0);
        sessions.put(first.sessionId, first);
        sessions.put(second.sessionId, second);
        field(SessionManager.class, "activeSession").set(manager, first);
        bar = allocate(SessionTabBar.class);
        bar.visible = true;
        bar.sz = UI.scale(600, 300);
        InertCheckBox visible = allocate(InertCheckBox.class);
        visible.a = true;
        field(SessionTabBar.class, "btnVis").set(bar, visible);
        InertCheckBox locked = allocate(InertCheckBox.class);
        locked.a = true;
        field(SessionTabBar.class, "btnLock").set(bar, locked);
        field(SessionTabBar.class, "dragStartButton").setInt(bar, -1);
    }

    @AfterEach void teardown() throws Exception {
        sessions.clear();
        sessions.putAll(previousSessions);
        field(SessionManager.class, "activeSession").set(manager, previousActive);
        NConfig.current = previousConfig;
        resources.close();
    }

    @Test void speakerClickOnlyTogglesItsSessionWithoutSelectingClosingOrStartingDrag() throws Exception {
        Coord point = speaker(1).add(1, 1);
        assertTrue(new Widget.MouseDownEvent(point, 1).dispatch(bar));
        assertFalse(first.muted);
        assertTrue(second.muted);
        assertSame(first, manager.getActiveSession());
        assertEquals(2, manager.getSessionCount());
        assertNull(field(SessionTabBar.class, "dragStartPos").get(bar));
        assertEquals(-1, field(SessionTabBar.class, "dragStartButton").getInt(bar));
        bar.mouseup(new Widget.MouseUpEvent(point, 1));
        assertSame(first, manager.getActiveSession());
        assertTrue(new Widget.MouseDownEvent(point, 1).dispatch(bar));
        assertFalse(second.muted);
    }

    @Test void hoverTooltipAndRowHeightFollowMacroRowsAndScrolling() throws Exception {
        int secondY = rowHeight(first) + SessionTabBar.BUTTON_PADDING;
        assertTrue(secondY > SessionTabBar.BUTTON_HEIGHT + SessionTabBar.BUTTON_PADDING);
        field(SessionTabBar.class, "scrollY").setInt(bar, secondY - UI.scale(2));
        Coord point = speaker(1).add(1, 1);
        bar.mousemove(new Widget.MouseMoveEvent(point));
        assertEquals(1, field(SessionTabBar.class, "hoveredSpeaker").getInt(bar));
        assertTrue(bar.tooltip(point, null).toString().contains("Second"));
        second.muted = true;
        String mutedTip = bar.tooltip(point, null).toString();
        second.muted = false;
        assertNotEquals(mutedTip, bar.tooltip(point, null).toString());
        bar.mousemove(new Widget.MouseMoveEvent(new Coord(-1, -1)));
        assertEquals(-1, field(SessionTabBar.class, "hoveredSpeaker").getInt(bar));
        assertFalse(hit(new Coord(point.x, -1), 1));
        assertFalse(hit(new Coord(point.x, bar.sz.y), 1));
    }

    @Test void speakerDoesNotOverlapPortraitCloseStatusOrMacroControls() throws Exception {
        Coord point = speaker(0);
        assertTrue(point.x >= SessionTabBar.AVA_MARGIN + SessionTabBar.AVA_SIZE);
        int closeX = SessionTabBar.BUTTON_WIDTH - SessionTabBar.CLOSE_BTN_MARGIN - SessionTabBar.CLOSE_BTN_SIZE;
        assertTrue(point.x + SessionTabBar.SPEAKER_SIZE < closeX);
        int statusY = SessionTabBar.BUTTON_HEIGHT - SessionTabBar.STATUS_ICON_SIZE - UI.scale(4);
        assertTrue(point.y + SessionTabBar.SPEAKER_SIZE <= statusY);
        assertFalse(hit(new Coord(closeX + 1, SessionTabBar.CLOSE_BTN_MARGIN + 1), 0));
        assertFalse(hit(new Coord(SessionTabBar.BUTTON_WIDTH + UI.scale(8), 1), 0));
        assertFalse(hit(point.add(SessionTabBar.SPEAKER_SIZE, 0), 0));
        assertTrue(bar.mousedown(new Widget.MouseDownEvent(new Coord(UI.scale(60), UI.scale(25)), 1)));
        assertEquals(0, field(SessionTabBar.class, "dragStartButton").getInt(bar));
        assertFalse(first.muted);
    }

    @Test void hiddenAndDragModeControlsDoNotToggleOrExposeMuteTooltip() throws Exception {
        Coord point = speaker(0).add(1, 1);
        ICheckBox visible = (ICheckBox) field(SessionTabBar.class, "btnVis").get(bar);
        visible.a = false;
        assertFalse(bar.mousedown(new Widget.MouseDownEvent(point, 1)));
        assertNull(bar.tooltip(point, null));
        visible.a = true;
        NUI ui = allocate(NUI.class);
        ui.core = allocate(NCore.class);
        ui.core.mode = NCore.Mode.DRAG;
        field(UI.class, "grabs").set(ui, new LinkedList<>());
        bar.ui = ui;
        Coord dragPoint = SessionTabBar.speakerPosition(UI.scale(15), UI.scale(34)).add(1, 1);
        assertFalse(bar.mousedown(new Widget.MouseDownEvent(dragPoint, 1)));
        assertNull(bar.tooltip(dragPoint, null));
        assertFalse(first.muted);
    }

    @Test void switchingActiveIdentityKeepsMuteOnTheSelectedSessionsOnly() throws Exception {
        bar.mousedown(new Widget.MouseDownEvent(speaker(1).add(1, 1), 1));
        field(SessionManager.class, "activeSession").set(manager, second);
        bar.mousedown(new Widget.MouseDownEvent(speaker(0).add(1, 1), 1));
        assertTrue(first.muted);
        assertTrue(second.muted);
        assertSame(second, manager.getActiveSession());
        field(SessionManager.class, "activeSession").set(manager, first);
        assertTrue(first.muted);
        assertTrue(second.muted);
    }

    private RecordingContext context(String name, int bots) throws Exception {
        RecordingContext ctx = new RecordingContext();
        ctx.characterName = name;
        ctx.gui = allocate(NGameUI.class);
        ctx.gui.biw = new BotsInterruptWidget();
        for (int i = 0; i < bots; i++) ctx.gui.biw.addObserve(new Thread(() -> {}, "test-bot-" + i));
        return ctx;
    }
    private int rowHeight(SessionContext ctx) throws Exception {
        return (Integer) method("rowHeight", SessionContext.class).invoke(bar, ctx);
    }
    private Coord speaker(int index) throws Exception {
        int y = -field(SessionTabBar.class, "scrollY").getInt(bar);
        if (index == 1) y += rowHeight(first) + SessionTabBar.BUTTON_PADDING;
        return SessionTabBar.speakerPosition(0, y);
    }
    private boolean hit(Coord c, int index) throws Exception {
        return (Boolean) method("isSpeakerHit", Coord.class, int.class, List.class)
                .invoke(bar, c, index, new ArrayList<>(sessions.values()));
    }
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
