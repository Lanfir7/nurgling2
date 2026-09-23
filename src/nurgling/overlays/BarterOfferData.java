package nurgling.overlays;

/** Wire format from gfx/fx/eq v21; barterstand v70 names its five anchors e0..e4. */
final class BarterOfferData {
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
        return "gfx/invobjs/" + item;
    }
}
