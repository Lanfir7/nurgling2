package nurgling.conf;

import nurgling.NConfig;
import nurgling.NUI;
import nurgling.NUtils;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;


public class NCarrierProp implements JConf
{
    final private String username;
    final private String chrid;
    public String object = null;
    /** ID выбранной зоны Carry Out (null = использовать глобальную/по умолчанию) */
    public Integer targetZoneId = null;
    public List<String> objectHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 30;


    public NCarrierProp(String username, String chrid) {
        this.username = username;
        this.chrid = chrid;
    }

    public NCarrierProp(HashMap<String, Object> values)
    {
        chrid = (String) values.get("chrid");
        username = (String) values.get("username");
        if (values.get("object") != null)
            object = (String) values.get("object");
        Object tzi = values.get("targetZoneId");
        if (tzi != null) {
            if (tzi instanceof Number)
                targetZoneId = ((Number) tzi).intValue();
            else
                targetZoneId = (Integer) tzi;
        }
        if (values.get("objectHistory") != null)
            objectHistory = (List<String>) values.get("objectHistory");
    }

    public void addToHistory(String objectName) {
        if (objectName == null || objectName.trim().isEmpty()) {
            return;
        }
        LinkedHashSet<String> uniqueHistory = new LinkedHashSet<>(objectHistory);
        uniqueHistory.remove(objectName);
        List<String> newHistory = new ArrayList<>();
        newHistory.add(objectName);
        newHistory.addAll(uniqueHistory);
        if (newHistory.size() > MAX_HISTORY_SIZE) {
            newHistory = newHistory.subList(0, MAX_HISTORY_SIZE);
        }
        objectHistory = newHistory;
    }

    public static void set(NCarrierProp prop)
    {
        ArrayList<NCarrierProp> carrierProps = ((ArrayList<NCarrierProp>) NConfig.get(NConfig.Key.carrierprop));
        if (carrierProps != null)
        {
            for (Iterator<NCarrierProp> i = carrierProps.iterator(); i.hasNext(); )
            {
                NCarrierProp oldprop = i.next();
                if(oldprop.username.equals(prop.username) && oldprop.chrid.equals(prop.chrid))
                {
                    i.remove();
                    break;
                }
            }

        }
        else
        {
            carrierProps = new ArrayList<>();
        }
        carrierProps.add(prop);
        NConfig.set(NConfig.Key.carrierprop, carrierProps);
    }

    @Override
    public String toString()
    {
        return "NCarrierProp[" + username + "|" + chrid + "]";
    }

    @Override
    public JSONObject toJson()
    {
        JSONObject jcarrier = new JSONObject();
        jcarrier.put("type", "NCarrierProp");
        jcarrier.put("username", username);
        jcarrier.put("chrid", chrid);
        jcarrier.put("object", object);
        if (targetZoneId != null)
            jcarrier.put("targetZoneId", targetZoneId);
        jcarrier.put("objectHistory", objectHistory);
        return jcarrier;
    }

    public static NCarrierProp get(NUI.NSessInfo sessInfo)
    {
        if (sessInfo == null || NUtils.getGameUI() == null || NUtils.getGameUI().getCharInfo() == null)
            return null;
        String chrid = NUtils.getGameUI().getCharInfo().chrid;
        ArrayList<NCarrierProp> carrierProps = ((ArrayList<NCarrierProp>) NConfig.get(NConfig.Key.carrierprop));
        if (carrierProps == null)
            carrierProps = new ArrayList<>();
        for (NCarrierProp prop : carrierProps)
        {
            if (prop.username.equals(sessInfo.username) && prop.chrid.equals(chrid))
            {
                return prop;
            }
        }
        return new NCarrierProp(sessInfo.username, chrid);
    }
}
