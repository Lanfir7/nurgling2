package nurgling.actions.bots;

final class WormFarmLogic {
    static final int DEEP_OFFSET = 10000;

    private WormFarmLogic() {}

    static boolean isKeepItem(String name) {
        return "Soil".equals(name) || "Earthworm".equals(name) || "Odd Tuber".equals(name);
    }

    static boolean shouldDropJunk(String name) {
        return !isKeepItem(name);
    }

    static boolean shouldStopDig(int freeSoilSlots) {
        return freeSoilSlots < 1;
    }

    static boolean shouldSkipFill(int soilCount) {
        return soilCount <= 0;
    }

    static boolean shouldStopFill(int soilCount, int soilRequired) {
        return soilCount <= 0 || soilRequired <= 0;
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
