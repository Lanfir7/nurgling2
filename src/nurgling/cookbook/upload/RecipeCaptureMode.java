package nurgling.cookbook.upload;

public final class RecipeCaptureMode {
    private RecipeCaptureMode() {
    }

    public static boolean shouldCapture(boolean localCookbook, boolean sharing) {
        return localCookbook || sharing;
    }

    public static boolean isReady(boolean tooltipReceived, boolean qualityAvailable) {
        return tooltipReceived && qualityAvailable;
    }

    public static boolean isRemoteReady(boolean sharing, String genus) {
        return sharing && genus != null && !genus.trim().isEmpty();
    }

    public static String cacheKey(String recipeKey, boolean localCookbook, boolean sharing) {
        return (localCookbook ? "L" : "") + (sharing ? "S" : "") + ":" + recipeKey;
    }

    public static boolean isRemoteKey(String key) {
        if (key == null)
            return false;
        int separator = key.indexOf(':');
        return separator > 0 && key.substring(0, separator).indexOf('S') >= 0;
    }

    public static boolean isNewSharingGeneration(long handledGeneration, long currentGeneration) {
        return handledGeneration != currentGeneration;
    }
}
