package nurgling.conf;

import nurgling.NConfig;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class NAreaRad implements JConf
{
    public String name;
    public boolean vis;
    public int radius;

    public NAreaRad(String name, int radius) {
        this.name = name;
        this.vis = true;
        this.radius = radius;
    }

    public NAreaRad(HashMap<String, Object> values)
    {
        name = (String) values.get("name");
        if (values.get("vis") != null)
            vis = (Boolean) values.get("vis");
        if (values.get("radius") != null)
            radius = (Integer) values.get("radius");
    }

    @Override
    public JSONObject toJson()
    {
        JSONObject jobj = new JSONObject();
        jobj.put("type", "NAreaRad");
        jobj.put("name", name);
        jobj.put("vis", vis);
        jobj.put("radius", radius);
        return jobj;
    }

    public static NAreaRad get(String val)
    {
        ArrayList<NAreaRad> radProps = ((ArrayList<NAreaRad>) NConfig.get(NConfig.Key.animalrad));
        if (radProps == null)
            radProps = new ArrayList<>();
        for (NAreaRad prop : radProps)
        {
            if (prop.name.equals(val))
            {
                return prop;
            }
        }
        return null;
    }

    public static final String WILDGOAT_OLD = "gfx/kritter/wildgoat/wildgoat";
    public static final String WILDGOAT = "gfx/kritter/goat/wildgoat";

    /**
     * Fix the mountain-goat resource path and insert it when missing.
     * @return true if the list was modified
     */
    public static boolean migrateList(ArrayList<NAreaRad> rads) {
        if (rads == null)
            return false;
        boolean changed = false;
        NAreaRad oldGoat = null;
        NAreaRad newGoat = null;
        for (NAreaRad r : rads) {
            if (WILDGOAT_OLD.equals(r.name))
                oldGoat = r;
            else if (WILDGOAT.equals(r.name))
                newGoat = r;
        }
        if (newGoat == null) {
            NAreaRad n;
            if (oldGoat != null) {
                n = new NAreaRad(WILDGOAT, oldGoat.radius);
                n.vis = oldGoat.vis;
            } else {
                n = new NAreaRad(WILDGOAT, 100);
            }
            rads.add(n);
            changed = true;
        }
        if (oldGoat != null) {
            rads.remove(oldGoat);
            changed = true;
        }
        return changed;
    }

    public static void set(String val, NAreaRad prop)
    {
        ArrayList<NAreaRad> radProps = ((ArrayList<NAreaRad>) NConfig.get(NConfig.Key.animalrad));
        if (radProps != null)
        {
            for (Iterator<NAreaRad> i = radProps.iterator(); i.hasNext(); )
            {
                NAreaRad oldprop = i.next();
                if (oldprop.name.equals(prop.name))
                {
                    i.remove();
                    break;
                }
            }

        }
        else
        {
            radProps = new ArrayList<>();
        }
        radProps.add(prop);
        NConfig.set(NConfig.Key.animalrad, radProps);
    }
}
