package nurgling.tools;

import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fuel areas reserved for individual burners, with the legacy shared Fuel area as fallback. */
public final class FuelZones {
    public static final String ICON = "nurgling/categories/fuel";
    public static final Specialisation.SpecName GENERIC = Specialisation.SpecName.fuel;

    public static final class Zone {
        public final Specialisation.SpecName spec;
        public final Specialisation.SpecName station;
        public final String prettyName;

        Zone(Specialisation.SpecName spec, Specialisation.SpecName station, String prettyName) {
            this.spec = spec;
            this.station = station;
            this.prettyName = prettyName;
        }
    }

    public static final List<Zone> all;
    static {
        ArrayList<Zone> zones = new ArrayList<>();
        zones.add(new Zone(Specialisation.SpecName.fuelSmelter, Specialisation.SpecName.smelter, "Fuel: Smelter"));
        zones.add(new Zone(Specialisation.SpecName.fuelSteelbox, Specialisation.SpecName.crucibles, "Fuel: Steelbox"));
        zones.add(new Zone(Specialisation.SpecName.fuelFforge, Specialisation.SpecName.fforge, "Fuel: Finery Forge"));
        zones.add(new Zone(Specialisation.SpecName.fuelKiln, Specialisation.SpecName.kiln, "Fuel: Kiln"));
        zones.add(new Zone(Specialisation.SpecName.fuelOven, Specialisation.SpecName.ovens, "Fuel: Oven"));
        zones.add(new Zone(Specialisation.SpecName.fuelCauldron, Specialisation.SpecName.boiler, "Fuel: Cauldron"));
        zones.add(new Zone(Specialisation.SpecName.fuelFireplace, Specialisation.SpecName.pow, "Fuel: Fire Place"));
        zones.add(new Zone(Specialisation.SpecName.fuelCrucible, Specialisation.SpecName.crucible, "Fuel: Crucible"));
        zones.add(new Zone(Specialisation.SpecName.fuelTarkiln, Specialisation.SpecName.tarkiln, "Fuel: Tarkiln"));
        all = Collections.unmodifiableList(zones);
    }

    private FuelZones() { }

    public static Zone of(Specialisation.SpecName spec) {
        if (spec == null) return null;
        for (Zone zone : all) if (zone.spec == spec) return zone;
        return null;
    }

    /** Resolves local then global station fuel, then local then global legacy Fuel. */
    public static NArea find(Specialisation.SpecName zone, String material) {
        String station = zone == null ? null : zone.toString();
        return firstAvailable(findLocal(station, material), findGlobal(station, material),
                findLocal(GENERIC.toString(), material), findGlobal(GENERIC.toString(), material));
    }

    private static NArea findLocal(String name, String material) {
        if (name == null)
            return null;
        if (material == null || material.isEmpty()) {
            return NContext.findSpec(name);
        }
        return NContext.findSpec(name, material);
    }

    private static NArea findGlobal(String name, String material) {
        if (name == null)
            return null;
        return material == null || material.isEmpty()
                ? NContext.findSpecGlobal(name) : NContext.findSpecGlobal(name, material);
    }

    @SafeVarargs
    static <T> T firstAvailable(T... candidates) {
        for (T candidate : candidates) {
            if (candidate != null)
                return candidate;
        }
        return null;
    }

    public static String describe(Specialisation.SpecName zone, String material) {
        Zone resolved = of(zone);
        String name = resolved == null ? "Fuel" : resolved.prettyName;
        return material == null || material.isEmpty() ? name : name + " (" + material + ")";
    }
}
