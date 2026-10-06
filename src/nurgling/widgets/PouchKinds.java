package nurgling.widgets;

import nurgling.ExtraInvGroupTransfer;

import java.util.Locale;

/** Same-type pouches that should share one window. */
public final class PouchKinds {
    private PouchKinds() {}

    public static String key(String itemName, String contentsName, String resourceName) {
        String fromRes = fromResource(resourceName);
        if (fromRes != null)
            return fromRes;
        if (ExtraInvGroupTransfer.isExternalBag(itemName))
            return itemName;
        if (ExtraInvGroupTransfer.isExternalBag(contentsName))
            return contentsName;
        return null;
    }

    static String fromResource(String resourceName) {
        if (resourceName == null || resourceName.isEmpty())
            return null;
        int slash = resourceName.lastIndexOf('/');
        String leaf = (slash >= 0 ? resourceName.substring(slash + 1) : resourceName).toLowerCase(Locale.ROOT);
        if ("silkpurse".equals(leaf))
            return "Silk Purse";
        if ("leatherpurse".equals(leaf))
            return "Leather Purse";
        if ("seedbag".equals(leaf))
            return "Seedbag";
        if ("creel".equals(leaf))
            return "Creel";
        if ("poacherpouch".equals(leaf) || "poacherspouch".equals(leaf))
            return "Poacher's Pouch";
        return null;
    }
}
