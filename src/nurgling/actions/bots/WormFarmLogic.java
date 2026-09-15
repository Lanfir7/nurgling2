package nurgling.actions.bots;

final class WormFarmLogic {
    static final int DEEP_OFFSET = 40;
    static final int DEFAULT_MIN_FREE = 1;
    static final int METAL_SHOVEL_MIN_FREE = 3;
    static final String METAL_SHOVEL = "Metal Shovel";

    private WormFarmLogic() {}

    static boolean isKeepItem(String name) {
        return "Soil".equals(name) || "Earthworm".equals(name) || "Odd Tuber".equals(name);
    }

    static boolean shouldDropJunk(String name) {
        return !isKeepItem(name);
    }

    static boolean isMetalShovel(String name) {
        if (name == null) return false;
        return name.equals(METAL_SHOVEL) || name.contains("shovel-m");
    }

    static int minFreeSlots(String toolName) {
        return isMetalShovel(toolName) ? METAL_SHOVEL_MIN_FREE : DEFAULT_MIN_FREE;
    }

    static boolean shouldStopDig(int freeSoilSlots) {
        return shouldStopDig(freeSoilSlots, DEFAULT_MIN_FREE);
    }

    static boolean shouldStopDig(int freeSoilSlots, int minFree) {
        return freeSoilSlots >= 0 && freeSoilSlots < minFree;
    }

    static boolean waitDigTickDone(boolean filling, int idleCount, boolean restoreNeeds, int calcFreeSpace) {
        return waitDigTickDone(filling, idleCount, restoreNeeds, calcFreeSpace, DEFAULT_MIN_FREE);
    }

    static boolean waitDigTickDone(boolean filling, int idleCount, boolean restoreNeeds, int calcFreeSpace, int minFree) {
        if (filling && idleCount >= 20) return true;
        if (idleCount >= 360) return true;
        if (restoreNeeds) return true;
        return !filling && shouldStopDig(calcFreeSpace, minFree);
    }

    static boolean shouldSkipFill(int soilCount) {
        return soilCount <= 0;
    }

    static boolean shouldClickDig(boolean filling, boolean fillMode) {
        return true;
    }

    static boolean isFillMode(String wlbl) {
        return wlbl != null && wlbl.contains("Units of soil required");
    }

    static boolean shouldStopFill(boolean fillMode, int soilCount, int soilRequired) {
        if (soilCount <= 0) return true;
        return fillMode && soilRequired <= 0;
    }

    static boolean fillDidNotUseSoil(boolean fillMode, int soilBefore, int soilAfter) {
        return !fillMode && soilAfter >= soilBefore;
    }

    static int parseTargetLevel(String tllbl) {
        if (tllbl == null) return 0;
        int colon = tllbl.lastIndexOf(':');
        String rem = colon >= 0 ? tllbl.substring(colon + 1).trim() : tllbl.trim();
        int i = 0;
        while (i < rem.length() && isGrouping(rem.charAt(i))) i++;
        boolean neg = false;
        if (i < rem.length() && isMinus(rem.charAt(i))) {
            neg = true;
            i++;
        }
        long val = 0;
        boolean any = false;
        while (i < rem.length()) {
            char c = rem.charAt(i);
            if (Character.isDigit(c)) {
                val = val * 10 + (c - '0');
                if (val > Integer.MAX_VALUE) return 0;
                any = true;
            } else if (isGrouping(c)) {
                if (!any) return 0;
            } else {
                break;
            }
            i++;
        }
        if (!any) return 0;
        int n = (int) val;
        return neg ? -n : n;
    }

    static boolean canReadTarget(String tllbl) {
        if (tllbl == null || tllbl.contains("...")) return false;
        int colon = tllbl.lastIndexOf(':');
        String rem = colon >= 0 ? tllbl.substring(colon + 1).trim() : tllbl.trim();
        int i = 0;
        while (i < rem.length() && isGrouping(rem.charAt(i))) i++;
        if (i < rem.length() && isMinus(rem.charAt(i))) i++;
        while (i < rem.length() && isGrouping(rem.charAt(i))) i++;
        return i < rem.length() && Character.isDigit(rem.charAt(i));
    }

    static boolean isRangeTarget(String tllbl) {
        if (tllbl == null) return false;
        int colon = tllbl.lastIndexOf(':');
        String rem = colon >= 0 ? tllbl.substring(colon + 1).trim() : tllbl.trim();
        int i = 0;
        while (i < rem.length() && isGrouping(rem.charAt(i))) i++;
        if (i < rem.length() && isMinus(rem.charAt(i))) i++;
        boolean digits = false;
        while (i < rem.length() && (Character.isDigit(rem.charAt(i)) || isGrouping(rem.charAt(i)))) {
            if (Character.isDigit(rem.charAt(i))) digits = true;
            i++;
        }
        if (!digits || i >= rem.length()) return false;
        return rem.charAt(i) == '-';
    }

    private static boolean isMinus(char c) {
        return c == '-' || c == '\u2212';
    }

    private static boolean isGrouping(char c) {
        return c == ' ' || c == ',' || c == '\u00a0' || c == '\u202f' || c == '\'';
    }

    static boolean isUsablePlaneTarget(int level) {
        return Math.abs(level) < 2000;
    }

    static boolean planeShowsLowered(int remembered, int shown) {
        return shown < remembered;
    }

    static boolean planeShowsRestored(int remembered, int shown) {
        return shown == remembered;
    }

    static boolean shouldRestoreNeeds(double stamina, double energy) {
        return stamina < 0.25 || energy < 0.3;
    }

    static int deepHeight(int currentMin) {
        return currentMin - DEEP_OFFSET;
    }

    static int[] copyHeights(int[] dz) {
        if (dz == null) return new int[0];
        int[] out = new int[dz.length];
        System.arraycopy(dz, 0, out, 0, dz.length);
        return out;
    }

    static void applyUniform(float[] wz, int[] dz, int height) {
        for (int i = 0; i < dz.length; i++) {
            dz[i] = height;
            if (wz != null && i < wz.length)
                wz[i] = height;
        }
    }

    static void restoreHeights(float[] wz, int[] dz, int[] snapshot) {
        int n = Math.min(dz.length, snapshot.length);
        for (int i = 0; i < n; i++) {
            dz[i] = snapshot[i];
            if (wz != null && i < wz.length)
                wz[i] = snapshot[i];
        }
    }

    static String putError(int worms, int tubers) {
        if (worms > 0) return "Worm Farm: no earthworm PUT area available";
        if (tubers > 0) return "Worm Farm: no Odd Tuber PUT area available";
        return null;
    }
}
