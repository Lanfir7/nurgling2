package nurgling.agent;

import nurgling.tools.DefaultAnimalAlarms;

public final class AgentWorldClassifier {
    private AgentWorldClassifier() {}

    public static String classify(String resName) {
        if (resName == null || resName.trim().isEmpty()) {
            return "other";
        }
        String lower = resName.toLowerCase();
        if (isAggressive(resName)) {
            return "aggressive";
        }
        if (lower.contains("/kritter/") || lower.contains("gfx/kritter")) {
            return "animal";
        }
        if (isGateName(lower)) {
            return "gate";
        }
        if (lower.contains("palisade")) {
            return "palisade";
        }
        if (lower.contains("/cart") || lower.endsWith("cart")) {
            return "cart";
        }
        if (lower.contains("wagon")) {
            return "wagon";
        }
        boolean treePath = lower.contains("gfx/terobjs/tree") || lower.contains("/trees/") || lower.contains("/bushes/");
        if (treePath && (lower.contains("oldtrunk") || lower.contains("log"))) {
            return "log";
        }
        if (treePath && lower.contains("stump")) {
            return "stump";
        }
        if (treePath) {
            return "tree";
        }
        return "other";
    }

    public static boolean isAggressive(String resName) {
        return DefaultAnimalAlarms.hasSound(resName);
    }

    public static boolean isGateName(String lower) {
        if (lower == null) return false;
        return lower.contains("palisadegate")
                || lower.contains("palisadebiggate")
                || lower.contains("polegate")
                || lower.contains("polebiggate")
                || lower.contains("drystonewallgate")
                || lower.contains("drystonewallbiggate");
    }
}
