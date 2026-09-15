package nurgling.conf;

import nurgling.NConfig;
import haven.Gob;
import nurgling.tools.NParser;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class NAreaRad implements JConf
{
    // Migration default only (see the HashMap constructor below) - plain Rat is the one non-dangerous entry the old hardcoded exclusion covered.
    private static final String RAT_RESOURCE = "gfx/kritter/rat/rat";

    public String name;
    public boolean vis;
    public int radius;
    // Whether DangerousAnimalTrigger treats this ring as a threat - independent of vis (which only controls whether the ring is drawn).
    public boolean dangerous;

    public NAreaRad(String name, int radius) {
        this.name = name;
        this.vis = true;
        this.radius = radius;
        this.dangerous = true;
    }

    // 1.25x margin over the configured danger radius - pulls a detour/character away from real
    // danger, not just up to its edge. Shared by DangerousAnimalTrigger and
    // ForagerRouteConstraints.dangerousAnimalNearCorridor so the two can't drift apart.
    public static final double DANGER_MARGIN = 1.25;

    /** True if this ring should be treated as an active threat right now - dangerous, and not exempted by ignoreBats. */
    public boolean isActiveThreat(boolean ignoreBats) {
        if (!dangerous) return false;
        if (ignoreBats && isBat()) return false;
        return true;
    }

    /** Whether "bat" is a whole path segment of this ring's resource name (e.g. "gfx/kritter/bat/bat") -
     *  a plain substring match would also exempt any future species whose name merely contains "bat". */
    private boolean isBat() {
        if (name == null) return false;
        for (String segment : name.split("/")) {
            if (segment.equals("bat")) return true;
        }
        return false;
    }

    /** radius scaled by DANGER_MARGIN - the actual trigger distance every consumer of this ring should check against. */
    public double triggerDist() {
        return radius * DANGER_MARGIN;
    }

    public static boolean isDownOrDead(Gob gob) {
        String pose = gob == null ? null : gob.pose();
        return pose != null && NParser.checkName(pose, "dead", "knock");
    }

    public NAreaRad(HashMap<String, Object> values)
    {
        name = (String) values.get("name");
        if (values.get("vis") != null)
            vis = (Boolean) values.get("vis");
        if (values.get("radius") != null)
            radius = (Integer) values.get("radius");
        // No "dangerous" key in a pre-existing saved entry - default to match the old hardcoded exclusion (everything but plain Rat).
        dangerous = (values.get("dangerous") != null) ? (Boolean) values.get("dangerous") : !RAT_RESOURCE.equals(name);
    }

    @Override
    public JSONObject toJson()
    {
        JSONObject jobj = new JSONObject();
        jobj.put("type", "NAreaRad");
        jobj.put("name", name);
        jobj.put("vis", vis);
        jobj.put("radius", radius);
        jobj.put("dangerous", dangerous);
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
