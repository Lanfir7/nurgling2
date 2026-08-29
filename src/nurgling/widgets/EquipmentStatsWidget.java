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
        applyTotals(EquipmentStatsCollector.collectFromItems(items));
    }
    
    public void updateStatsFromPreset(Map<Integer, String> slotConfig) {
        List<List<ItemInfo>> itemInfos = new ArrayList<>();
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
                    
                    if (info != null)
                        itemInfos.add(info);
                }
            } catch (Exception e) {
                // Resource not available or error loading
            }
        }
        applyTotals(EquipmentStatsCollector.collectFromInfoLists(itemInfos));
    }

    private void applyTotals(EquipmentStatsCollector.Totals totals) {
        attributeMap.clear();
        totalStats.clear();
        sortedStats.clear();
        for (Map.Entry<String, Double> entry : totals.sorted()) {
            totalStats.put(entry.getKey(), entry.getValue());
            Attribute attr = totals.attribute(entry.getKey());
            if (attr != null)
                attributeMap.put(entry.getKey(), attr);
        }
        sortedStats = new ArrayList<>(totals.sorted());
        needUpdate = true;
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
    
    private void renderStats() {
        if (sortedStats.isEmpty()) {
            // Show message when no stats available
            Text.Line emptyMsg = Text.render("Нет характеристик", new Color(150, 150, 150));
            statsTex = new TexI(emptyMsg.img);
            return;
        }
        
        // Use AttrMod.modimg style rendering with icons
        Collection<Mod> mods = new ArrayList<>();
        for (Map.Entry<String, Double> entry : sortedStats) {
            String attrName = entry.getKey();
            double value = entry.getValue();
            Attribute attr = attributeMap.get(attrName);
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
