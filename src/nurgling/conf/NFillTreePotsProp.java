package nurgling.conf;

import nurgling.NConfig;
import nurgling.NUI;
import nurgling.NUtils;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class NFillTreePotsProp implements JConf {

    final private String username;
    final private String chrid;
    /** ID выбранной зоны Mulch/Soil for Trees (null = не выбрано) */
    public Integer mulchZoneId = null;

    public NFillTreePotsProp(String username, String chrid) {
        this.username = username;
        this.chrid = chrid;
    }

    public NFillTreePotsProp(HashMap<String, Object> values) {
        chrid = (String) values.get("chrid");
        username = (String) values.get("username");
        Object mzi = values.get("mulchZoneId");
        if (mzi != null) {
            if (mzi instanceof Number)
                mulchZoneId = ((Number) mzi).intValue();
            else
                mulchZoneId = (Integer) mzi;
        }
    }

    public static void set(NFillTreePotsProp prop) {
        ArrayList<NFillTreePotsProp> props = ((ArrayList<NFillTreePotsProp>) NConfig.get(NConfig.Key.filltreepotsprop));
        if (props != null) {
            for (Iterator<NFillTreePotsProp> i = props.iterator(); i.hasNext(); ) {
                NFillTreePotsProp old = i.next();
                if (old.username.equals(prop.username) && old.chrid.equals(prop.chrid)) {
                    i.remove();
                    break;
                }
            }
        } else {
            props = new ArrayList<>();
        }
        props.add(prop);
        NConfig.set(NConfig.Key.filltreepotsprop, props);
    }

    @Override
    public String toString() {
        return "NFillTreePotsProp[" + username + "|" + chrid + "]";
    }

    @Override
    public JSONObject toJson() {
        JSONObject j = new JSONObject();
        j.put("type", "NFillTreePotsProp");
        j.put("username", username);
        j.put("chrid", chrid);
        if (mulchZoneId != null)
            j.put("mulchZoneId", mulchZoneId);
        return j;
    }

    public static NFillTreePotsProp get(NUI.NSessInfo sessInfo) {
        if (sessInfo == null || NUtils.getGameUI() == null || NUtils.getGameUI().getCharInfo() == null)
            return null;
        String chrid = NUtils.getGameUI().getCharInfo().chrid;
        ArrayList<NFillTreePotsProp> props = ((ArrayList<NFillTreePotsProp>) NConfig.get(NConfig.Key.filltreepotsprop));
        if (props == null)
            props = new ArrayList<>();
        for (NFillTreePotsProp prop : props) {
            if (prop.username.equals(sessInfo.username) && prop.chrid.equals(chrid)) {
                return prop;
            }
        }
        return new NFillTreePotsProp(sessInfo.username, chrid);
    }
}
