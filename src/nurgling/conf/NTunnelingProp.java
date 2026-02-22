package nurgling.conf;

import nurgling.NConfig;
import nurgling.NUI;
import nurgling.NUtils;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class NTunnelingProp implements JConf {
    private final String username;
    private final String chrid;

    public int direction = 0;
    public int tunnelSide = 0;
    public int supportType = 0;
    public int wingOption = 0;
    public int wingSide = 0;
    public boolean doubleTunnel = false;
    public boolean wingNorth = false;
    public boolean wingSouth = false;
    public boolean wingEast = false;
    public boolean wingWest = false;

    public NTunnelingProp(String username, String chrid) {
        this.username = username;
        this.chrid = chrid;
    }

    public NTunnelingProp(HashMap<String, Object> values) {
        username = (String) values.get("username");
        chrid = (String) values.get("chrid");
        if (values.get("direction") != null)
            direction = ((Number) values.get("direction")).intValue();
        if (values.get("tunnelSide") != null)
            tunnelSide = ((Number) values.get("tunnelSide")).intValue();
        if (values.get("supportType") != null)
            supportType = ((Number) values.get("supportType")).intValue();
        if (values.get("wingOption") != null)
            wingOption = ((Number) values.get("wingOption")).intValue();
        if (values.get("wingSide") != null)
            wingSide = ((Number) values.get("wingSide")).intValue();
        if (values.get("doubleTunnel") != null)
            doubleTunnel = (Boolean) values.get("doubleTunnel");
        if (values.get("wingNorth") != null)
            wingNorth = (Boolean) values.get("wingNorth");
        if (values.get("wingSouth") != null)
            wingSouth = (Boolean) values.get("wingSouth");
        if (values.get("wingEast") != null)
            wingEast = (Boolean) values.get("wingEast");
        if (values.get("wingWest") != null)
            wingWest = (Boolean) values.get("wingWest");
    }

    public static void set(NTunnelingProp prop) {
        ArrayList<NTunnelingProp> props = (ArrayList<NTunnelingProp>) NConfig.get(NConfig.Key.tunnelingprop);
        if (props != null) {
            for (Iterator<NTunnelingProp> i = props.iterator(); i.hasNext(); ) {
                NTunnelingProp old = i.next();
                if (old.username.equals(prop.username) && old.chrid.equals(prop.chrid)) {
                    i.remove();
                    break;
                }
            }
        } else {
            props = new ArrayList<>();
        }
        props.add(prop);
        NConfig.set(NConfig.Key.tunnelingprop, props);
    }

    public static NTunnelingProp get(NUI.NSessInfo sessInfo) {
        if (sessInfo == null || NUtils.getGameUI() == null || NUtils.getGameUI().getCharInfo() == null)
            return null;
        String chrid = NUtils.getGameUI().getCharInfo().chrid;
        ArrayList<NTunnelingProp> props = (ArrayList<NTunnelingProp>) NConfig.get(NConfig.Key.tunnelingprop);
        if (props == null)
            props = new ArrayList<>();
        for (NTunnelingProp prop : props) {
            if (prop.username.equals(sessInfo.username) && prop.chrid.equals(chrid))
                return prop;
        }
        return new NTunnelingProp(sessInfo.username, chrid);
    }

    @Override
    public JSONObject toJson() {
        JSONObject j = new JSONObject();
        j.put("type", "NTunnelingProp");
        j.put("username", username);
        j.put("chrid", chrid);
        j.put("direction", direction);
        j.put("tunnelSide", tunnelSide);
        j.put("supportType", supportType);
        j.put("wingOption", wingOption);
        j.put("wingSide", wingSide);
        j.put("doubleTunnel", doubleTunnel);
        j.put("wingNorth", wingNorth);
        j.put("wingSouth", wingSouth);
        j.put("wingEast", wingEast);
        j.put("wingWest", wingWest);
        return j;
    }

    @Override
    public String toString() {
        return "NTunnelingProp[" + username + "|" + chrid + "]";
    }
}
