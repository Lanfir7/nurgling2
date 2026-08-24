package nurgling.tools;

import haven.Loading;
import haven.QuestWnd;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NUtils;
import org.json.JSONArray;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class QuestTrackFilter {
    private final Set<String> muted = new LinkedHashSet<String>();

    public boolean isMuted(String title) {
        if (title == null || title.isEmpty())
            return false;
        return muted.contains(title);
    }

    public boolean isTracked(String title) {
        return !isMuted(title);
    }

    public void setTracked(String title, boolean tracked) {
        if (title == null || title.isEmpty())
            return;
        if (tracked)
            muted.remove(title);
        else
            muted.add(title);
    }

    public int compareQuests(String aTitle, int aMtime, String bTitle, int bMtime) {
        boolean at = isTracked(aTitle);
        boolean bt = isTracked(bTitle);
        if (at != bt)
            return at ? -1 : 1;
        return Integer.compare(bMtime, aMtime);
    }

    public JSONArray toJson() {
        JSONArray arr = new JSONArray();
        for (String t : muted)
            arr.put(t);
        return arr;
    }

    public static QuestTrackFilter fromStored(Object stored) {
        QuestTrackFilter f = new QuestTrackFilter();
        if (stored instanceof JSONArray) {
            JSONArray arr = (JSONArray) stored;
            for (int i = 0; i < arr.length(); i++) {
                Object v = arr.get(i);
                if (v != null)
                    f.setTracked(String.valueOf(v), false);
            }
        } else if (stored instanceof Collection) {
            for (Object v : (Collection<?>) stored) {
                if (v != null)
                    f.setTracked(String.valueOf(v), false);
            }
        }
        return f;
    }

    public static QuestTrackFilter load() {
        Object stored = null;
        try {
            stored = NConfig.get(NConfig.Key.mutedQuests);
        } catch (RuntimeException ignored) {
        }
        return fromStored(stored);
    }

    public static boolean isMutedTitle(String title) {
        return load().isMuted(title);
    }

    public static void persistTracked(String title, boolean tracked) {
        QuestTrackFilter f = load();
        f.setTracked(title, tracked);
        NConfig.set(NConfig.Key.mutedQuests, f.toJson());
    }

    public static String safeTitle(QuestWnd.Quest q) {
        if (q == null)
            return null;
        try {
            return q.title();
        } catch (Loading e) {
            return q.title;
        }
    }

    public static void notifyHelper() {
        NGameUI gui = NUtils.getGameUI();
        if (gui != null && gui.questinfo != null)
            gui.questinfo.requestUpdate();
    }
}
