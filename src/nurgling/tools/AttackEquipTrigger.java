package nurgling.tools;

/**
 * Decides when vanilla Attack targeting should start {@code EquipShieldSword}.
 * Trigger is Attack paginae/hotkey plus the sword cursor; never stacks a second run.
 */
public final class AttackEquipTrigger {
    public static final String ATTACK_PAGINAE = "paginae/act/atk";
    public static final String ATTACK_CURSOR = "gfx/hud/curs/atk";

    private boolean pending;
    private boolean running;

    public static boolean shouldStart(boolean enabled, boolean attackActivation, boolean alreadyRunning) {
        return enabled && attackActivation && !alreadyRunning;
    }

    public static boolean isVanillaAttackPaginae(String resName) {
        return ATTACK_PAGINAE.equals(resName);
    }

    public static boolean isVanillaAttackAction(String[] ad) {
        return ad != null && ad.length > 0 && "atk".equals(ad[0]);
    }

    public static boolean isAttackCursor(String cursorRes) {
        return ATTACK_CURSOR.equals(cursorRes);
    }

    public static boolean isVanillaAttack(String resName, String[] ad) {
        if (resName != null && resName.startsWith("paginae/atk/")) {
            return false;
        }
        return isVanillaAttackPaginae(resName) || isVanillaAttackAction(ad);
    }

    public boolean requestStart(boolean enabled) {
        if (!shouldStart(enabled, true, running)) {
            return false;
        }
        pending = false;
        running = true;
        return true;
    }

    public boolean onPaginaeUsed(String resName, String[] ad, String currentCursor, boolean enabled) {
        if (!isVanillaAttack(resName, ad)) {
            return false;
        }
        if (!enabled || running) {
            pending = false;
            return false;
        }
        if (isAttackCursor(currentCursor)) {
            return requestStart(true);
        }
        pending = true;
        return false;
    }

    public boolean onCursorChanged(String cursorRes, boolean enabled) {
        if (!isAttackCursor(cursorRes)) {
            pending = false;
            return false;
        }
        if (!enabled) {
            pending = false;
            return false;
        }
        if (!pending || running) {
            return false;
        }
        return requestStart(true);
    }

    public boolean isRunning() {
        return running;
    }

    public void finished() {
        running = false;
    }
}
