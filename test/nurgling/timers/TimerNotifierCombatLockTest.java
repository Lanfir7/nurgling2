package nurgling.timers;

import haven.Coord;
import haven.Fightview;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NUI;
import nurgling.sessions.SessionContext;
import nurgling.sessions.SessionManager;
import nurgling.sessions.ThreadLocalUI;
import nurgling.todo.TodoItem;
import nurgling.todo.TodoStore;
import nurgling.widgets.timers.TimerBanners;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TimerNotifierCombatLockTest {
    @TempDir Path temp;

    @BeforeAll static void initializeIsolatedConfigForWidgetStatics() {
        NConfig.getGlobalInstance();
    }

    @Test void combatCheckFinishesUnderUiLockWhileAnotherThreadHoldsSessionManagerLock() throws Exception {
        SessionManager manager = SessionManager.getInstance();
        Object sessionsLock = field(SessionManager.class, manager, "sessionsLock");
        NUI ui = bareUI();
        NGameUI gui = gameUI(ui, fightview(true));
        TimerNotifier notifier = new TimerNotifier(gui);
        CountDownLatch managerLockHeld = new CountDownLatch(1);
        CountDownLatch releaseManagerLock = new CountDownLatch(1);
        Thread lockHolder = new Thread(() -> {
            synchronized (sessionsLock) {
                managerLockHeld.countDown();
                try {
                    releaseManagerLock.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "TimerNotifier-test-manager-lock");
        lockHolder.setDaemon(true);
        ExecutorService caller = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TimerNotifier-test-ui-tick");
            t.setDaemon(true);
            return t;
        });
        try {
            lockHolder.start();
            assertTrue(managerLockHeld.await(1, TimeUnit.SECONDS));
            Future<Boolean> result = caller.submit(() -> {
                synchronized (ui) { // same monitor held by SessionContext's headless tick loop
                    return inCombat(notifier);
                }
            });
            assertTrue(result.get(1, TimeUnit.SECONDS),
                    "combat lookup must not wait for sessionsLock while holding the UI monitor");
        } finally {
            releaseManagerLock.countDown();
            lockHolder.join(1000);
            caller.shutdownNow();
            assertTrue(caller.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    @Test void combatQuietHoldsDueTimerUntilFightEndsAndBackgroundTimerWaitsForItsSession() throws Exception {
        SessionManager manager = SessionManager.getInstance();
        Field activeField = declaredField(SessionManager.class, "activeSession");
        SessionContext previousActive = (SessionContext) activeField.get(manager);
        NUI previousThreadUI = ThreadLocalUI.get();
        NUI ui = bareUI();
        NConfig config = new NConfig();
        setConfig(config, NConfig.Key.timerCombatQuiet, true);
        setConfig(config, NConfig.Key.timerFlashTaskbar, false);
        setConfig(config, NConfig.Key.timerSoundPin, TimerNotifier.SOUND_NONE);
        setConfig(config, NConfig.Key.timerSoundReminder, TimerNotifier.SOUND_NONE);
        setConfig(config, NConfig.Key.taskHeadsUpMinutes, 60);
        setConfig(config, NConfig.Key.timerMyCharacters, "Timer test");
        ui.sessionConfig = config;
        Fightview fight = fightview(true);
        NGameUI gui = gameUI(ui, fight);
        TimerStore store = new TimerStore("timer-notifier-test", temp.resolve("timers.json").toString());
        gui.timerStore = store;
        gui.todoStore = todoStore(73);
        RecordingBanners banners = new RecordingBanners();
        gui.timerBanners = banners;
        TimerNotifier notifier = new TimerNotifier(gui);
        SessionContext thisSession = new SessionContext(null, ui);
        activeField.set(manager, thisSession);
        ThreadLocalUI.set(ui);

        try {
            Timer combatTimer = readyTimer("combat", System.currentTimeMillis());
            store.put(combatTimer);
            Timer headsUp = new Timer("heads-up", Timer.Kind.TASK, 0, 0, 0, null, "Task", null,
                    System.currentTimeMillis(), 60 * 60_000L, 0, "Timer test", false, 0, 0, null,
                    73, "");
            store.put(headsUp);
            assertEquals(1, store.dueSoon(System.currentTimeMillis(), 60 * 60_000L).size());
            notifier.tick();
            assertEquals(1, store.due(System.currentTimeMillis()).size(),
                    "quiet combat must leave a due timer eligible for the first post-combat announcement");
            assertEquals(1, store.dueSoon(System.currentTimeMillis(), 60 * 60_000L).size(),
                    "quiet combat must not mark a task heads-up as already shown");
            assertEquals(0, banners.taskSoon);

            fight.lsrel.clear();
            resetTick(notifier);
            notifier.tick();
            assertTrue(store.due(System.currentTimeMillis()).isEmpty(),
                    "the timer should be announced once combat ends");
            assertEquals(1, banners.taskSoon);
            assertTrue(store.dueSoon(System.currentTimeMillis(), 60 * 60_000L).isEmpty());
            resetTick(notifier);
            notifier.tick();
            assertEquals(1, banners.taskSoon, "a task heads-up should appear only once per cycle");

            setConfig(config, NConfig.Key.timerCombatQuiet, false);
            fight.lsrel.add(null);
            Timer duringCombat = readyTimer("quiet-disabled", System.currentTimeMillis());
            store.put(duringCombat);
            resetTick(notifier);
            notifier.tick();
            assertTrue(store.due(System.currentTimeMillis()).isEmpty(),
                    "disabling combat quiet must allow a due timer to announce during combat");
            assertEquals(2, banners.timerCount);

            Timer backgroundTimer = readyTimer("background", System.currentTimeMillis());
            store.put(backgroundTimer);
            NUI otherUI = bareUI();
            ThreadLocalUI.set(otherUI);
            activeField.set(manager, new SessionContext(null, otherUI));
            resetTick(notifier);
            notifier.tick();
            assertTrue(notifier.needsAttention(), "a background session should mark its tab while retaining its due timer");
            assertEquals(1, store.due(System.currentTimeMillis()).size());

            activeField.set(manager, thisSession);
            ThreadLocalUI.set(ui);
            resetTick(notifier);
            notifier.tick();
            assertFalse(notifier.needsAttention());
            assertTrue(store.due(System.currentTimeMillis()).isEmpty(),
                    "switching to the session should announce the retained timer exactly once");
            assertEquals(3, banners.timerCount);
            resetTick(notifier);
            notifier.tick();
            assertEquals(3, banners.timerCount, "switching back must not duplicate an already announced timer");
        } finally {
            activeField.set(manager, previousActive);
            if (previousThreadUI == null)
                ThreadLocalUI.clear();
            else
                ThreadLocalUI.set(previousThreadUI);
        }
    }

    @Test void combatPredicateUsesOnlyThisGuisVisibleFightviewWithOpponents() throws Exception {
        NUI ui = bareUI();
        NGameUI gui = gameUI(ui, null);
        TimerNotifier notifier = new TimerNotifier(gui);
        assertFalse(inCombat(notifier));

        Fightview fight = fightview(false);
        gui.fv = fight;
        assertFalse(inCombat(notifier), "hidden fight view is not combat");
        fight.show();
        assertFalse(inCombat(notifier), "visible fight view without opponents is not combat");
        Field relations = declaredField(Fightview.class, "lsrel");
        relations.set(fight, null);
        assertFalse(inCombat(notifier), "a missing opponent list is not combat");
        relations.set(fight, new LinkedList<Fightview.Relation>());
        fight.lsrel.add(null);
        assertTrue(inCombat(notifier));

        SessionManager manager = SessionManager.getInstance();
        Field activeField = declaredField(SessionManager.class, "activeSession");
        SessionContext previousActive = (SessionContext) activeField.get(manager);
        NUI previousThreadUI = ThreadLocalUI.get();
        NUI otherUI = bareUI();
        try {
            activeField.set(manager, new SessionContext(null, otherUI));
            ThreadLocalUI.set(otherUI);
            assertTrue(inCombat(notifier), "the notifier must inspect its own game's fight view");
        } finally {
            activeField.set(manager, previousActive);
            if (previousThreadUI == null) ThreadLocalUI.clear();
            else ThreadLocalUI.set(previousThreadUI);
        }
    }

    private static Timer readyTimer(String id, long now) {
        return new Timer(id, Timer.Kind.PIN, 0, 0, 0, null, id, null,
                now - 2_000, 1_000, 0, null, false, 0, 0, null);
    }

    private static NGameUI gameUI(NUI ui, Fightview fight) throws Exception {
        NGameUI gui = (NGameUI) allocate(NGameUI.class);
        gui.ui = ui;
        gui.fv = fight;
        return gui;
    }

    private static NUI bareUI() throws Exception {
        return (NUI) allocate(NUI.class);
    }

    private static TodoStore todoStore(int taskId) throws Exception {
        TodoStore store = (TodoStore) allocate(TodoStore.class);
        fieldRef(TodoStore.class, store, "lock").set(store, new Object());
        fieldRef(TodoStore.class, store, "loadedMode").set(store, TodoStore.Mode.LOCAL);
        TodoItem item = new TodoItem();
        item.id = taskId;
        Constructor<TodoStore.View> constructor = TodoStore.View.class.getDeclaredConstructor(
                java.util.List.class, Map.class, int.class, String.class, long.class);
        constructor.setAccessible(true);
        Object view = constructor.newInstance(Collections.emptyList(),
                Collections.singletonMap(taskId, item), 0, null, 0L);
        fieldRef(TodoStore.class, store, "view").set(store, view);
        return store;
    }

    private static Fightview fightview(boolean hasOpponent) throws Exception {
        Fightview fight = (Fightview) allocate(Fightview.class);
        Field relations = declaredField(Fightview.class, "lsrel");
        relations.set(fight, new LinkedList<Fightview.Relation>());
        if (hasOpponent)
            fight.lsrel.add(null);
        if (hasOpponent)
            fight.show();
        return fight;
    }

    private static boolean inCombat(TimerNotifier notifier) throws Exception {
        Method method = TimerNotifier.class.getDeclaredMethod("inCombat");
        method.setAccessible(true);
        return (boolean) method.invoke(notifier);
    }

    private static void resetTick(TimerNotifier notifier) throws Exception {
        declaredField(TimerNotifier.class, "next").setDouble(notifier, 0);
    }

    private static void setConfig(NConfig config, NConfig.Key key, Object value) throws Exception {
        @SuppressWarnings("unchecked") Map<NConfig.Key, Object> values =
                (Map<NConfig.Key, Object>) field(NConfig.class, config, "conf");
        values.put(key, value);
    }

    private static Field fieldRef(Class<?> owner, Object target, String name) throws Exception {
        return declaredField(owner, name);
    }

    private static Object field(Class<?> owner, Object target, String name) throws Exception {
        return declaredField(owner, name).get(target);
    }

    private static Field declaredField(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object unsafe() throws Exception {
        Class<?> type = Class.forName("sun.misc.Unsafe");
        Field singleton = type.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        return singleton.get(null);
    }

    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        return unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe(), type);
    }

    private static final class RecordingBanners extends TimerBanners {
        int timerCount;
        int taskSoon;

        @Override public void post(java.util.List<Timer> due, boolean away) { timerCount += due.size(); }

        @Override public void postDeadlines(Type type, java.util.List<Timer> deadlines, boolean away) {
            if (type == Type.TASK_SOON) taskSoon += deadlines.size();
            else timerCount += deadlines.size();
        }
    }
}
