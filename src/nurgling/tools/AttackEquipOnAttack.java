package nurgling.tools;

import haven.UI;
import nurgling.NConfig;
import nurgling.actions.bots.EquipShieldSword;
import nurgling.sessions.BotExecutor;

import java.util.WeakHashMap;

/**
 * Wires {@link AttackEquipTrigger} to MenuGrid / cursor messages and starts
 * Equip Shield/Sword in the background without touching Attack targeting.
 */
public final class AttackEquipOnAttack {
    public static final AttackEquipOnAttack INSTANCE = new AttackEquipOnAttack();

    static final String BOT_THREAD_NAME = "shieldsword-on-attack";

    private final WeakHashMap<UI, AttackEquipTrigger> triggers = new WeakHashMap<>();
    private final WeakHashMap<UI, Thread> running = new WeakHashMap<>();

    AttackEquipOnAttack() {}

    public static String cursorOf(UI ui) {
        if (ui == null) {
            return null;
        }
        String best = ui.root != null ? ui.root.cursorRes : null;
        if (ui.gui != null) {
            best = preferAttack(best, ui.gui.cursorRes);
            if (ui.gui.map != null) {
                best = preferAttack(best, ui.gui.map.cursorRes);
            }
        }
        return best;
    }

    private static String preferAttack(String current, String candidate) {
        if (AttackEquipTrigger.isAttackCursor(candidate)) {
            return candidate;
        }
        return current != null ? current : candidate;
    }

    private AttackEquipTrigger trigger(UI ui) {
        if (ui == null) {
            return new AttackEquipTrigger();
        }
        synchronized (triggers) {
            return triggers.computeIfAbsent(ui, ignored -> new AttackEquipTrigger());
        }
    }

    public void onMenuUse(UI ui, String resName, String[] ad, String currentCursor) {
        if (trigger(ui).onPaginaeUsed(resName, ad, currentCursor, enabled())) {
            startBot(ui);
        }
    }

    public void onCursorRes(UI ui, String cursorRes) {
        if (trigger(ui).onCursorChanged(cursorRes, enabled())) {
            startBot(ui);
        }
    }

    private boolean enabled() {
        Object val = NConfig.get(NConfig.Key.equipSwordShieldOnAttack);
        return val instanceof Boolean && (Boolean) val;
    }

    private boolean botAlive(UI ui) {
        if (ui == null) {
            return false;
        }
        synchronized (running) {
            Thread t = running.get(ui);
            return t != null && t.isAlive();
        }
    }

    private void startBot(UI ui) {
        if (botAlive(ui)) {
            return;
        }
        Thread t = BotExecutor.runWithSupports(BOT_THREAD_NAME, new EquipShieldSword(), false, () -> finished(ui));
        if (t == null) {
            trigger(ui).finished();
            return;
        }
        synchronized (running) {
            running.put(ui, t);
        }
    }

    private void finished(UI ui) {
        synchronized (running) {
            running.remove(ui);
        }
        trigger(ui).finished();
    }
}
