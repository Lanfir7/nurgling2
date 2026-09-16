package nurgling.actions;

import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.tools.FuelZones;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;
import java.util.Arrays;

public class Validator implements Action{


    public Validator(ArrayList<NArea.Specialisation> req, ArrayList<NArea.Specialisation> opt)
    {
        this(req);
        this.opt = opt;
    }

    public Validator(ArrayList<NArea.Specialisation> req)
    {
        this.req = req;
    }

    ArrayList<NArea.Specialisation> req;
    ArrayList<NArea.Specialisation> opt;
    /** Requirement groups where any one configured specialisation is sufficient. */
    ArrayList<ArrayList<NArea.Specialisation>> reqAny = new ArrayList<>();
    /** Optional groups keep their warning-only behaviour while supporting Fuel fallback. */
    ArrayList<ArrayList<NArea.Specialisation>> optAny = new ArrayList<>();

    public Validator anyOf(NArea.Specialisation... alternatives) {
        reqAny.add(new ArrayList<>(Arrays.asList(alternatives)));
        return this;
    }

    /** Accepts a station-specific fuel zone or the legacy shared Fuel fallback. */
    public Validator fuel(Specialisation.SpecName zone, String material) {
        return anyOf(new NArea.Specialisation(zone.toString(), material),
                new NArea.Specialisation(FuelZones.GENERIC.toString(), material));
    }

    public Validator optionalFuel(Specialisation.SpecName zone, String material) {
        optAny.add(new ArrayList<>(Arrays.asList(
                new NArea.Specialisation(zone.toString(), material),
                new NArea.Specialisation(FuelZones.GENERIC.toString(), material))));
        return this;
    }

    private static String pretty(NArea.Specialisation s) {
        Specialisation.SpecialisationItem item = Specialisation.findSpecialisation(s.name);
        String name = item == null ? s.name : item.prettyName;
        return s.subtype == null ? name : name + " ( " + s.subtype + " )";
    }

    private static boolean exists(NArea.Specialisation s) {
        if (s.subtype != null)
            return NContext.findSpec(s.name, s.subtype) != null || NContext.findSpecGlobal(s.name, s.subtype) != null;
        return NContext.findSpec(s.name) != null || NContext.findSpecGlobal(s.name) != null;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if(NUtils.getEnergy()<0.22)
        {
            return Results.ERROR("WARNING: LOW ENERGY");
        }
        if(req != null) for(NArea.Specialisation s: req)
            if(!exists(s))
                return Results.ERROR("Area " + pretty(s) + " required, but not found!");

        for(ArrayList<NArea.Specialisation> group: reqAny) {
            boolean found = false;
            for(NArea.Specialisation s: group) if(exists(s)) { found = true; break; }
            if(!found) {
                ArrayList<String> names = new ArrayList<>();
                for(NArea.Specialisation s: group) names.add(pretty(s));
                return Results.ERROR("Area required, but not found! Set one of: " + String.join(" or ", names));
            }
        }

        if(opt != null) for(NArea.Specialisation s: opt)
        {
            if(!exists(s))
                NUtils.getGameUI().msg("Optional area " + pretty(s) + " not found.");
        }
        for(ArrayList<NArea.Specialisation> group: optAny) {
            boolean found = false;
            for(NArea.Specialisation s: group) if(exists(s)) { found = true; break; }
            if(!found) {
                ArrayList<String> names = new ArrayList<>();
                for(NArea.Specialisation s: group) names.add(pretty(s));
                NUtils.getGameUI().msg("Optional area not found. Set one of: " + String.join(" or ", names));
            }
        }
        return Results.SUCCESS();
    }
}
