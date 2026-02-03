package nurgling.widgets;

import haven.*;
import haven.res.ui.tt.attrmod.AttrMod;
import haven.res.ui.tt.attrmod.Mod;
import haven.res.ui.tt.attrmod.Attribute;
import nurgling.NGItem;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;

public class EquipmentStatsWidget extends Widget {
    // Use attribute name as key to properly sum same attributes
    private Map<String, Attribute> attributeMap = new HashMap<>();
    private Map<String, Double> totalStats = new HashMap<>();
    private List<Map.Entry<String, Double>> sortedStats = new ArrayList<>();
    private Tex statsTex = null;
    private boolean needUpdate = true;
    private Label titleLabel;
    
    public EquipmentStatsWidget(Coord sz) {
        super(sz);
        titleLabel = add(new Label("Характеристики:"), new Coord(0, 0));
    }
    
    public void updateStatsFromItems(WItem[] items) {
        synchronized (this) {
        attributeMap.clear();
        totalStats.clear();
        sortedStats.clear();
        needUpdate = true;
        
        // Use a set to track processed items to avoid counting the same item twice
        // (some items can be in multiple slots, but they're the same GItem)
        Set<GItem> processedItems = new HashSet<>();
        
        // Collect stats from all equipped items
        for (WItem item : items) {
            if (item != null && item.item != null) {
                GItem gitem = item.item;
                
                // Skip if we've already processed this item
                if (processedItems.contains(gitem)) {
                    continue;
                }
                processedItems.add(gitem);
                
                try {
                    List<ItemInfo> info = null;
                    try {
                        info = gitem.info();
                    } catch (Loading e) {
                        // Item info not ready yet, skip this item for now
                        // It will be processed on next update
                        continue;
                    }
                    
                    if (info != null) {
                        // Collect from all AttrMod instances (base item modifiers)
                        for (ItemInfo inf : info) {
                            if (inf instanceof AttrMod) {
                                AttrMod attrMod = (AttrMod) inf;
                                if (attrMod.tab != null) {
                                    for (haven.res.ui.tt.attrmod.Entry entry2 : attrMod.tab) {
                                        if (entry2 instanceof Mod) {
                                            Mod mod = (Mod) entry2;
                                            Attribute attr = mod.attr;
                                            String attrName = attr.name();
                                            double value = mod.mod;
                                            
                                            // Store attribute object for later use
                                            attributeMap.put(attrName, attr);
                                            
                                            // Sum up values for the same attribute by name
                                            totalStats.put(attrName, totalStats.getOrDefault(attrName, 0.0) + value);
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Collect from ISlots (Gilding slots)
                        for (ItemInfo inf : info) {
                            if (inf.getClass().getName().equals("haven.res.ui.tt.slots.ISlots") || 
                                inf.getClass().getName().equals("haven.res.ui.tt.slots_alt.ISlots")) {
                                try {
                                    java.lang.reflect.Field sField = inf.getClass().getField("s");
                                    Collection<?> slots = (Collection<?>) sField.get(inf);
                                    for (Object slot : slots) {
                                        try {
                                            java.lang.reflect.Field infoField = slot.getClass().getField("info");
                                            List<ItemInfo> slotInfo = (List<ItemInfo>) infoField.get(slot);
                                            collectAttrModsFromList(slotInfo);
                                        } catch (Exception e) {
                                            // Field not found or error
                                        }
                                    }
                                } catch (Exception e) {
                                    // Field not found or error
                                }
                            }
                        }
                        
                        // Collect from Slotted (Gilding items)
                        for (ItemInfo inf : info) {
                            if (inf.getClass().getName().equals("haven.res.ui.tt.slot.Slotted") || 
                                inf.getClass().getName().equals("haven.res.ui.tt.slot_alt.Slotted")) {
                                try {
                                    java.lang.reflect.Field subField = inf.getClass().getField("sub");
                                    List<ItemInfo> subInfo = (List<ItemInfo>) subField.get(inf);
                                    collectAttrModsFromList(subInfo);
                                } catch (Exception e) {
                                    // Field not found or error
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Item info not available or error
                }
            }
        }
        
        // Sort by absolute value (largest first)
        sortedStats = new ArrayList<>(totalStats.entrySet());
        sortedStats.sort((a, b) -> Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue())));
        
        needUpdate = true;
        }
    }
    
    public void updateStatsFromPreset(Map<Integer, String> slotConfig) {
        synchronized (this) {
        attributeMap.clear();
        totalStats.clear();
        sortedStats.clear();
        needUpdate = true;
        
        // Collect stats from all items in the set
        for (Map.Entry<Integer, String> entry : slotConfig.entrySet()) {
            String resName = entry.getValue();
            try {
                Resource res = Resource.remote().loadwait(resName);
                if (res != null) {
                    // Create a temporary GItem to get item info
                    Indir<Resource> resIndir = res.indir();
                    MessageBuf sdt = new MessageBuf();
                    NGItem tempItem = new NGItem(resIndir, sdt);
                    
                    // Wait for item info to load
                    List<ItemInfo> info = null;
                    try {
                        info = tempItem.info();
                    } catch (Loading e) {
                        // Item info not ready yet, skip this item
                        continue;
                    }
                    
                    if (info != null) {
                        // Collect from all AttrMod instances (base item modifiers)
                        for (ItemInfo inf : info) {
                            if (inf instanceof AttrMod) {
                                AttrMod attrMod = (AttrMod) inf;
                                if (attrMod.tab != null) {
                                    for (haven.res.ui.tt.attrmod.Entry entry2 : attrMod.tab) {
                                        if (entry2 instanceof Mod) {
                                            Mod mod = (Mod) entry2;
                                            Attribute attr = mod.attr;
                                            String attrName = attr.name();
                                            double value = mod.mod;
                                            
                                            // Store attribute object for later use
                                            attributeMap.put(attrName, attr);
                                            
                                            // Sum up values for the same attribute by name
                                            totalStats.put(attrName, totalStats.getOrDefault(attrName, 0.0) + value);
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Collect from ISlots (Gilding slots)
                        for (ItemInfo inf : info) {
                            if (inf.getClass().getName().equals("haven.res.ui.tt.slots.ISlots") || 
                                inf.getClass().getName().equals("haven.res.ui.tt.slots_alt.ISlots")) {
                                try {
                                    java.lang.reflect.Field sField = inf.getClass().getField("s");
                                    Collection<?> slots = (Collection<?>) sField.get(inf);
                                    for (Object slot : slots) {
                                        try {
                                            java.lang.reflect.Field infoField = slot.getClass().getField("info");
                                            List<ItemInfo> slotInfo = (List<ItemInfo>) infoField.get(slot);
                                            collectAttrModsFromList(slotInfo);
                                        } catch (Exception e) {
                                            // Field not found or error
                                        }
                                    }
                                } catch (Exception e) {
                                    // Field not found or error
                                }
                            }
                        }
                        
                        // Collect from Slotted (Gilding items)
                        for (ItemInfo inf : info) {
                            if (inf.getClass().getName().equals("haven.res.ui.tt.slot.Slotted") || 
                                inf.getClass().getName().equals("haven.res.ui.tt.slot_alt.Slotted")) {
                                try {
                                    java.lang.reflect.Field subField = inf.getClass().getField("sub");
                                    List<ItemInfo> subInfo = (List<ItemInfo>) subField.get(inf);
                                    collectAttrModsFromList(subInfo);
                                } catch (Exception e) {
                                    // Field not found or error
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Resource not available or error loading
            }
        }
        
        // Sort by absolute value (largest first)
        sortedStats = new ArrayList<>(totalStats.entrySet());
        sortedStats.sort((a, b) -> Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue())));
        
        needUpdate = true;
        }
    }
    
    @Override
    public void draw(GOut g) {
        if (needUpdate) {
            renderStats();
            needUpdate = false;
        }
        
        // Draw title background
        g.chcolor(0, 0, 0, 128);
        g.frect(Coord.z, new Coord(sz.x, titleLabel.sz.y + UI.scale(4)));
        g.chcolor();
        
        super.draw(g);
        
        // Draw stats below title
        if (statsTex != null) {
            int statsY = titleLabel.sz.y + UI.scale(8);
            g.image(statsTex, new Coord(0, statsY));
        }
    }
    
    private void collectAttrModsFromList(List<ItemInfo> infoList) {
        if (infoList == null) return;
        for (ItemInfo inf : infoList) {
            if (inf instanceof AttrMod) {
                AttrMod attrMod = (AttrMod) inf;
                if (attrMod.tab != null) {
                    for (haven.res.ui.tt.attrmod.Entry entry2 : attrMod.tab) {
                        if (entry2 instanceof Mod) {
                            Mod mod = (Mod) entry2;
                            Attribute attr = mod.attr;
                            String attrName = attr.name();
                            double value = mod.mod;
                            
                            // Store attribute object for later use
                            attributeMap.put(attrName, attr);
                            
                            // Sum up values for the same attribute by name
                            totalStats.put(attrName, totalStats.getOrDefault(attrName, 0.0) + value);
                        }
                    }
                }
            }
        }
    }
    
    private void renderStats() {
        List<Map.Entry<String, Double>> statsCopy;
        Map<String, Attribute> attrCopy;
        synchronized (this) {
            if (sortedStats.isEmpty()) {
                Text.Line emptyMsg = Text.render("Нет характеристик", new Color(150, 150, 150));
                statsTex = new TexI(emptyMsg.img);
                return;
            }
            statsCopy = new ArrayList<>(sortedStats);
            attrCopy = new HashMap<>(attributeMap);
        }
        
        // Use AttrMod.modimg style rendering with icons
        Collection<Mod> mods = new ArrayList<>();
        for (Map.Entry<String, Double> entry : statsCopy) {
            String attrName = entry.getKey();
            double value = entry.getValue();
            Attribute attr = attrCopy.get(attrName);
            if (attr != null) {
                mods.add(new Mod(attr, value));
            }
        }
        
        if (mods.isEmpty()) {
            Text.Line emptyMsg = Text.render("Нет характеристик", new Color(150, 150, 150));
            statsTex = new TexI(emptyMsg.img);
            return;
        }
        
        // Use the same rendering method as AttrMod.modimg
        BufferedImage result = AttrMod.modimg(mods);
        statsTex = new TexI(result);
    }
}
