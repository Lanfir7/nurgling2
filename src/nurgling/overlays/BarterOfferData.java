package nurgling.overlays;

/** Wire format from gfx/fx/eq v21; barterstand v70 names its five anchors e0..e4. */
final class BarterOfferData {
    // Inventory paths from the existing VSpec item catalog.
    private static final java.util.Set<String> HERBS = java.util.Set.of(
        "ambergris",
        "baybolete",
        "bladderwrack",
        "bloatedbolete",
        "bloodstern",
        "blueberry",
        "camomile",
        "candleberry",
        "cattailfibre",
        "cattailhead",
        "cattailroots",
        "cavebulb",
        "chantrelle",
        "chimingbluebell",
        "chives",
        "clover",
        "coltsfoot",
        "commonstarfish",
        "dandelion",
        "dill",
        "duskfern",
        "edelweiss",
        "fieldblewit",
        "flotsam",
        "frogscrown",
        "ghostapple",
        "giantpuffball",
        "glimmermoss",
        "goosebarnacle",
        "greenkelp",
        "heartsease",
        "kvann",
        "ladysmantle",
        "ladysmantledew",
        "lakesnail",
        "lampstalk",
        "libertycap",
        "lingon",
        "lorchel",
        "lupine",
        "marshmallow",
        "mistletoe",
        "mussels",
        "oyster",
        "oystermushroom",
        "parasolshroom",
        "pearloyster",
        "perfectautumnleaf",
        "precioussnowflake",
        "rabbitfrost",
        "razorclams",
        "royaltoadstool",
        "rubybolete",
        "rustroot",
        "salvia",
        "sleighbell",
        "snapdragon",
        "snowtop",
        "spindlytaproot",
        "stalagoom",
        "stingingnettle",
        "strawberry",
        "tangledbramble",
        "tansy",
        "thornythistle",
        "thyme",
        "toadflax",
        "waybroad",
        "windweed",
        "wintergreen",
        "yarrow",
        "yellowfoot",
        "yulecracker",
        "yulelights",
        "yulestar"
    );
    final int slot, resourceId;

    private BarterOfferData(int slot, int resourceId) {
        this.slot = slot;
        this.resourceId = resourceId;
    }

    static BarterOfferData decode(byte[] data) {
        // uint16 resource, uint8 flags, NUL-terminated attachment name, optional sub-data.
        if(data == null || data.length < 6 || data[3] != 'e' || data[5] != 0 ||
                data[4] < '0' || data[4] > '4') return null;
        int flags = data[2] & 255;
        if((flags & ~3) != 0 || (flags & 1) != 0) return null; // Only stand-owned anchors.
        if((flags & 2) != 0 && (data.length < 7 || data.length != 7 + (data[6] & 255))) return null;
        if((flags & 2) == 0 && data.length != 6) return null;
        return new BarterOfferData(data[4] - '0', (data[0] & 255) | ((data[1] & 255) << 8));
    }

    static String inventoryResource(String worldResource) {
        String prefix = "gfx/terobjs/items/";
        if(worldResource == null || !worldResource.startsWith(prefix)) return null;
        String item = worldResource.substring(prefix.length());
        if(item.isEmpty() || item.contains("..")) return null;
        if(item.startsWith("gast/")) {
            item = item.substring(5);
            if(item.endsWith("-f")) item = item.substring(0, item.length() - 2);
            return "gfx/invobjs/" + item;
        }
        if(item.equals("frostflower")) return "gfx/invobjs/herbs/frostflower";
        if(item.equals("garmentneedle")) return "gfx/invobjs/garmentneedle2";
        if(item.equals("swanfeather")) return "gfx/invobjs/feather-swan";
        if(item.equals("egg")) return "gfx/invobjs/egg-chicken";
        if(item.equals("testis")) return "gfx/invobjs/meat-testis";
        if(item.equals("filet-r")) return "gfx/invobjs/fish";
        if(item.equals("seeds")) return "gfx/invobjs/seed-hemp";
        if(item.equals("cheese")) return "nurgling/bots/icons/cheese/u";
        if(item.startsWith("coins-") && item.length() > 6)
            return "gfx/invobjs/coins/" + item.substring(6) + "-1";
        if(HERBS.contains(item))
            return "gfx/invobjs/herbs/" + item;
        return "gfx/invobjs/" + item;
    }
}
