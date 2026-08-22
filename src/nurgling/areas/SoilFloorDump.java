package nurgling.areas;

import haven.Coord2d;
import haven.Pair;
import nurgling.widgets.Specialisation;

public final class SoilFloorDump {
    public static final String ITEM = "Soil";

    private SoilFloorDump() {}

    public static boolean shouldDump(String itemName, NArea area) {
        if (area == null || !ITEM.equals(itemName))
            return false;
        String dump = Specialisation.SpecName.soilDump.toString();
        for (NArea.Specialisation spec : area.spec) {
            if (dump.equals(spec.name))
                return true;
        }
        return false;
    }

    public static Coord2d center(Pair<Coord2d, Coord2d> rca) {
        if (rca == null || rca.a == null || rca.b == null)
            return null;
        return rca.b.sub(rca.a).div(2).add(rca.a);
    }
}
