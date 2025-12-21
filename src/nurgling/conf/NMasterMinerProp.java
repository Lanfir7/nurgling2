package nurgling.conf;

import nurgling.NConfig;
import nurgling.NUI;
import nurgling.NUtils;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class NMasterMinerProp implements JConf {
    private final String username;
    private final String chrid;

    /** Минимальное "реальное качество камня в стене". Ниже — выкидывать. */
    public float minWallQ = 0f;

    /** Писать расчёт в чат. */
    public boolean showCalc = false;
    
    /** Порог качества для сброса камня. */
    public float dropThreshold = Float.NaN;
    
    /** Порог качества для сброса ракух и кэтголдов. */
    public float shellCatGoldThreshold = Float.NaN;
    
    /** Порог качества для постановки меток на карте. */
    public float markerThreshold = Float.NaN;

    public NMasterMinerProp(String username, String chrid) {
        this.username = username;
        this.chrid = chrid;
    }

    public NMasterMinerProp(HashMap<String, Object> values) {
        chrid = (String) values.get("chrid");
        username = (String) values.get("username");
        if (values.get("minWallQ") != null) {
            minWallQ = ((Number) values.get("minWallQ")).floatValue();
        }
        if (values.get("showCalc") != null) {
            showCalc = (Boolean) values.get("showCalc");
        }
        if (values.get("dropThreshold") != null) {
            Object dt = values.get("dropThreshold");
            if (dt instanceof Number) {
                float val = ((Number) dt).floatValue();
                dropThreshold = Float.isNaN(val) ? Float.NaN : val;
            }
        }
        if (values.get("shellCatGoldThreshold") != null) {
            Object dt = values.get("shellCatGoldThreshold");
            if (dt instanceof Number) {
                float val = ((Number) dt).floatValue();
                shellCatGoldThreshold = Float.isNaN(val) ? Float.NaN : val;
            }
        }
        if (values.get("markerThreshold") != null) {
            Object mt = values.get("markerThreshold");
            if (mt instanceof Number) {
                float val = ((Number) mt).floatValue();
                markerThreshold = Float.isNaN(val) ? Float.NaN : val;
            }
        }
    }

    public static void set(NMasterMinerProp prop) {
        ArrayList<NMasterMinerProp> props = (ArrayList<NMasterMinerProp>) NConfig.get(NConfig.Key.masterminerprop);
        if (props != null) {
            for (Iterator<NMasterMinerProp> i = props.iterator(); i.hasNext(); ) {
                NMasterMinerProp old = i.next();
                if (old.username.equals(prop.username) && old.chrid.equals(prop.chrid)) {
                    i.remove();
                    break;
                }
            }
        } else {
            props = new ArrayList<>();
        }
        props.add(prop);
        NConfig.set(NConfig.Key.masterminerprop, props);
    }

    @Override
    public JSONObject toJson() {
        JSONObject j = new JSONObject();
        j.put("type", "NMasterMinerProp");
        j.put("username", username);
        j.put("chrid", chrid);
        j.put("minWallQ", minWallQ);
        j.put("showCalc", showCalc);
        if (!Float.isNaN(dropThreshold)) {
            j.put("dropThreshold", dropThreshold);
        }
        if (!Float.isNaN(shellCatGoldThreshold)) {
            j.put("shellCatGoldThreshold", shellCatGoldThreshold);
        }
        if (!Float.isNaN(markerThreshold)) {
            j.put("markerThreshold", markerThreshold);
        }
        return j;
    }

    @Override
    public String toString() {
        return "NMasterMinerProp[" + username + "|" + chrid + "]";
    }

    public static NMasterMinerProp get(NUI.NSessInfo sessInfo) {
        if (sessInfo == null || NUtils.getGameUI() == null || NUtils.getGameUI().getCharInfo() == null) {
            return null;
        }
        String chrid = NUtils.getGameUI().getCharInfo().chrid;
        ArrayList<NMasterMinerProp> props = (ArrayList<NMasterMinerProp>) NConfig.get(NConfig.Key.masterminerprop);
        if (props == null) props = new ArrayList<>();
        for (NMasterMinerProp prop : props) {
            if (prop.username.equals(sessInfo.username) && prop.chrid.equals(chrid)) {
                return prop;
            }
        }
        return new NMasterMinerProp(sessInfo.username, chrid);
    }
}

