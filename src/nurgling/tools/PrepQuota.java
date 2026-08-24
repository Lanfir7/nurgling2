package nurgling.tools;

public final class PrepQuota {
    public enum Halt {
        WORKING,
        LOGNOTFOUND,
        TIMEFORDRINK,
        DANGER,
        NOFREESPACE,
        WOUND_DANGER
    }

    private PrepQuota() {}

    public static int parse(String s) {
        if (s == null)
            return 0;
        String t = s.trim();
        if (t.isEmpty())
            return 0;
        try {
            int n = Integer.parseInt(t);
            return n < 0 ? 0 : n;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean reached(int target, int inventory, int piledThisRun) {
        if (target <= 0)
            return false;
        return inventory + piledThisRun >= target;
    }

    public static boolean isLog(String name) {
        return name != null && name.startsWith("gfx/terobjs/trees/") && name.contains("log");
    }

    public static Halt pickBoards(boolean logGone, boolean danger, boolean noSpace, boolean drink) {
        if (logGone)
            return Halt.LOGNOTFOUND;
        if (danger)
            return Halt.DANGER;
        if (noSpace)
            return Halt.NOFREESPACE;
        if (drink)
            return Halt.TIMEFORDRINK;
        return Halt.WORKING;
    }

    public static Halt pickBlocks(boolean logGone, boolean danger, boolean noSpace, boolean drink, boolean wound) {
        Halt h = pickBoards(logGone, danger, noSpace, drink);
        if (wound)
            return Halt.WOUND_DANGER;
        return h;
    }
}
