package haven;

import java.util.*;

public class GobIconsCustom {

    private static final Map<String, String> gob2icon = new HashMap<String, String>(){{
		put("gfx/terobjs/vehicle/knarr", "customclient/mapicons/knarr");
		put("gfx/terobjs/vehicle/snekkja", "customclient/mapicons/snekkja");
		put("gfx/terobjs/vehicle/rowboat", "customclient/mapicons/rowboat");
		put("gfx/terobjs/vehicle/dugout", "customclient/mapicons/dugout");
        put("gfx/terobjs/vehicle/coracle", "customclient/mapicons/coracle");
		put("gfx/terobjs/vehicle/spark", "customclient/mapicons/kicksled");
		put("gfx/terobjs/vehicle/skis-wilderness", "customclient/mapicons/skis");
		put("gfx/terobjs/vehicle/wagon", "customclient/mapicons/wagon");
		put("gfx/terobjs/vehicle/sleigh", "customclient/mapicons/sleigh");

		put("gfx/terobjs/vehicle/wheelbarrow", "customclient/mapicons/wheelbarrow");
		put("gfx/terobjs/vehicle/cart", "customclient/mapicons/cart");
		put("gfx/terobjs/vehicle/plow", "customclient/mapicons/plow");

		put("gfx/kritter/horse/stallion", "customclient/mapicons/tamedHorse");
		put("gfx/kritter/horse/mare", "customclient/mapicons/tamedHorse");

		put("gfx/terobjs/pclaim", "customclient/mapicons/pclaim");
		put("gfx/terobjs/villageidol", "customclient/mapicons/vclaim");

		put("gfx/terobjs/minehole", "customclient/mapicons/minehole");
		put("gfx/terobjs/ladder", "customclient/mapicons/mineladder");

		put("gfx/terobjs/items/mandrakespirited", "customclient/mapicons/mandrakespirited");
		put("gfx/kritter/opiumdragon/opiumdragon", "customclient/mapicons/opiumdragon");
		put("gfx/kritter/stalagoomba/stalagoomba", "customclient/mapicons/stalagoomba");
		put("gfx/kritter/dryad/dryad", "customclient/mapicons/dryad");
		put("gfx/kritter/ent/ent", "customclient/mapicons/treant");

		put("gfx/terobjs/vehicle/bram", "customclient/mapicons/bram");
		put("gfx/terobjs/vehicle/catapult", "customclient/mapicons/catapult");
		put("gfx/terobjs/vehicle/wreckingball", "customclient/mapicons/wreckingball");

		put("gfx/terobjs/trees/oldtrunk", "customclient/mapicons/mirkwoodlog");

		put ("gfx/kritter/midgeswarm/midgeswarm", "customclient/mapicons/midgeswarm");

		put("gfx/terobjs/map/cavepuddle", "customclient/mapicons/caveclaypuddle");

		put("gfx/terobjs/items/gems/gemstone", "customclient/mapicons/gem");
		put("gfx/terobjs/items/arrow", "customclient/mapicons/arrow");
		put("gfx/terobjs/road/milestone-wood-e", "customclient/mapicons/roadsign");
		put("gfx/terobjs/road/milestone-stone-e", "customclient/mapicons/milestone");

		put("gfx/terobjs/map/starshard", "customclient/mapicons/starshard");
		put("gfx/terobjs/map/gemstone", "customclient/mapicons/gemboulder");

		put("gfx/terobjs/items/truffle-black0", "customclient/mapicons/blacktruffle");
		put("gfx/terobjs/items/truffle-black1", "customclient/mapicons/blacktruffle");
		put("gfx/terobjs/items/truffle-black2", "customclient/mapicons/blacktruffle");
		put("gfx/terobjs/items/truffle-black3", "customclient/mapicons/blacktruffle");

		put("gfx/terobjs/items/truffle-white0", "customclient/mapicons/whitetruffle");
		put("gfx/terobjs/items/truffle-white1", "customclient/mapicons/whitetruffle");
		put("gfx/terobjs/items/truffle-white2", "customclient/mapicons/whitetruffle");
		put("gfx/terobjs/items/truffle-white3", "customclient/mapicons/whitetruffle");

        put("gfx/terobjs/well", "customclient/mapicons/well");
    }};


    public static GobIcon getIcon(Gob gob) {
        String resname = gob2icon.get(gob.getres().name);
        if(resname != null) {
            return new GobIcon(gob, Resource.remote().load(resname), new byte[0]);
        }
        return null;
    }

    public static void addCustomSettings(GobIcon.Settings.Loader loader, UI ui) {
        gob2icon.forEach((key, value) -> {
            addSetting(loader, value);
        });
    }

    private static void addSetting(GobIcon.Settings.Loader loader, String res) {
        if(loader.load.stream().noneMatch(q -> Objects.equals(q.res.name, res))) {
            Resource.Saved spec = new Resource.Saved(Resource.remote(), res, -1);
            GobIcon.Settings.ResID id = new GobIcon.Settings.ResID(spec, new byte[0]);
            GobIcon.Setting cfg = new GobIcon.Setting(spec, GobIcon.Icon.nilid);
            cfg.show = cfg.defshow = true;

            Collection<GobIcon.Setting> sets = new ArrayList<>();
            sets.add(cfg);
            loader.load.add(id);
            loader.resolve.put(id, sets);
        }
    }

}
