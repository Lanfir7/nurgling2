package nurgling.widgets;

import haven.*;
import nurgling.NConfig;
import java.awt.Color;

/**
 * Popup notification window for Tree Finder when a high-growth tree is found
 */
public class TreeFinderNotificationWindow extends Window {
    private double createTime;
    private double autoCloseTime;
    
    public TreeFinderNotificationWindow(String treeName, int growthPercent, int threshold) {
        super(UI.scale(350, 120), "Найдено дерево!", false);
        this.createTime = Utils.rtime();
        // Get auto-close time from config (default 30 seconds)
        Object autoCloseTimeObj = NConfig.get(NConfig.Key.treeFinderNotificationAutoCloseTime);
        this.autoCloseTime = (autoCloseTimeObj instanceof Integer) ? (Integer) autoCloseTimeObj : 30;
        
        // Create content area
        Widget content = add(new Widget(new Coord(UI.scale(330), UI.scale(60))), UI.scale(10), UI.scale(10));
        
        int y = 0;
        int lineHeight = UI.scale(20);
        
        // Main message
        String message = "Найдено дерево:";
        Label messageLabel = new Label(message);
        messageLabel.setcolor(Color.WHITE);
        content.add(messageLabel, 0, y);
        y += lineHeight;
        
        // Tree name and growth
        String treeInfo = String.format("%s (%d%%)", treeName, growthPercent);
        Label treeLabel = new Label(treeInfo);
        treeLabel.setcolor(new Color(144, 238, 144)); // Light green
        content.add(treeLabel, 0, y);
        y += lineHeight + UI.scale(5);
        
        // Close button
        Button closeBtn = new Button(UI.scale(100), "OK") {
            @Override
            public void click() {
                TreeFinderNotificationWindow.this.destroy();
            }
        };
        add(closeBtn, UI.scale(125), UI.scale(75));
        
        pack();
    }
    
    @Override
    public void tick(double dt) {
        super.tick(dt);
        // Auto-close after configured time
        if (Utils.rtime() - createTime > autoCloseTime) {
            destroy();
        }
    }
    
    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (msg.equals("close")) {
            destroy();
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }
}

