package nurgling.conf;

import nurgling.NConfig;
import nurgling.NUI;
import nurgling.NUtils;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Last interactive drink choices, scoped to the current character. */
public class NDrinkerProp implements JConf {
    private final String username;
    private final String chrid;
    public final LinkedHashMap<String, Integer> sips = new LinkedHashMap<>();
    public final LinkedHashMap<String, String> cups = new LinkedHashMap<>();

    public NDrinkerProp(String username, String chrid) {
        this.username = username;
        this.chrid = chrid;
    }

    public NDrinkerProp(Map<String, Object> values) {
        this(values.get("username") instanceof String ? (String) values.get("username") : null,
                values.get("chrid") instanceof String ? (String) values.get("chrid") : null);
        Object counts = values.get("sips");
        if (counts instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) counts).entrySet())
                if (e.getKey() instanceof String && e.getValue() instanceof Number)
                    sips.put((String) e.getKey(), ((Number) e.getValue()).intValue());
        }
        Object choices = values.get("cups");
        if (choices instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) choices).entrySet())
                if (e.getKey() instanceof String && e.getValue() instanceof String)
                    cups.put((String) e.getKey(), (String) e.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    static ArrayList<NDrinkerProp> replace(Object raw, NDrinkerProp prop) {
        ArrayList<NDrinkerProp> result = new ArrayList<>();
        if (raw instanceof List<?>) {
            for (Object value : (List<?>) raw) {
                NDrinkerProp old = value instanceof NDrinkerProp ? (NDrinkerProp) value : null;
                if (old == null && value instanceof Map<?, ?>)
                    old = new NDrinkerProp((Map<String, Object>) value);
                if (old != null && !(Objects.equals(old.username, prop.username) && Objects.equals(old.chrid, prop.chrid)))
                    result.add(old);
            }
        }
        result.add(prop);
        return result;
    }

    public static synchronized void set(NDrinkerProp prop) {
        NConfig.set(NConfig.Key.drinkerprop, replace(NConfig.get(NConfig.Key.drinkerprop), prop));
    }

    public static NDrinkerProp get(NUI.NSessInfo sessInfo) {
        if (sessInfo == null || NUtils.getGameUI() == null || NUtils.getGameUI().getCharInfo() == null)
            return null;
        String chrid = NUtils.getGameUI().getCharInfo().chrid;
        Object raw = NConfig.get(NConfig.Key.drinkerprop);
        if (raw instanceof List<?>) {
            for (Object value : (List<?>) raw) {
                if (!(value instanceof NDrinkerProp)) continue;
                NDrinkerProp prop = (NDrinkerProp) value;
                if (Objects.equals(prop.username, sessInfo.username) && Objects.equals(prop.chrid, chrid))
                    return prop;
            }
        }
        return new NDrinkerProp(sessInfo.username, chrid);
    }

    @Override public JSONObject toJson() {
        return new JSONObject().put("type", "NDrinkerProp").put("username", username).put("chrid", chrid)
                .put("sips", new JSONObject(sips)).put("cups", new JSONObject(cups));
    }
}
