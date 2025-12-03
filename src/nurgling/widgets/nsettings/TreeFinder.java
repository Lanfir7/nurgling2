package nurgling.widgets.nsettings;

import haven.*;
import nurgling.*;

public class TreeFinder extends Panel {
    // Temporary settings structure
    private static class TreeFinderSettings {
        boolean enabled;
        boolean saveToMap;
        int saveToMapMinGrowth;
        boolean showNotification;
        int showNotificationMinGrowth;
        int notificationAutoCloseTime;
    }

    private final TreeFinderSettings tempSettings = new TreeFinderSettings();
    private CheckBox enabled;
    private CheckBox saveToMap;
    private HSlider saveToMapSlider;
    private Label saveToMapLabel;
    private CheckBox showNotification;
    private HSlider showNotificationSlider;
    private Label showNotificationLabel;
    private HSlider notificationAutoCloseSlider;
    private Label notificationAutoCloseLabel;
    
    private Scrollport scrollport;
    private Widget content;

    public TreeFinder() {
        super("");
        int margin = UI.scale(10);

        // Create scrollport to contain all settings
        int scrollWidth = UI.scale(560);
        int scrollHeight = UI.scale(550);
        scrollport = add(new Scrollport(new Coord(scrollWidth, scrollHeight)), new Coord(margin, margin));

        // Create main content container
        content = new Widget(new Coord(scrollWidth - UI.scale(20), UI.scale(50))) {
            @Override
            public void pack() {
                resize(contentsz());
            }
        };
        scrollport.cont.add(content, Coord.z);

        int contentMargin = UI.scale(5);
        
        // Tree Finder section
        Widget prev = content.add(new Label("● Tree Finder"), new Coord(contentMargin, contentMargin));
        
        prev = enabled = content.add(new CheckBox("Enable Tree Finder") {
            public void set(boolean val) {
                tempSettings.enabled = val;
                a = val;
            }
        }, prev.pos("bl").adds(0, 5));

        prev = saveToMap = content.add(new CheckBox("Save found trees to map") {
            public void set(boolean val) {
                tempSettings.saveToMap = val;
                a = val;
                updateSaveToMapState();
            }
        }, prev.pos("bl").adds(0, 10));

        prev = saveToMapLabel = content.add(new Label("Minimum growth for saving: 100%"), prev.pos("bl").adds(0, 5));
        prev = saveToMapSlider = content.add(new HSlider(UI.scale(200), 100, 300, tempSettings.saveToMapMinGrowth) {
            public void changed() {
                tempSettings.saveToMapMinGrowth = val;
                saveToMapLabel.settext("Minimum growth for saving: " + val + "%");
            }
        }, prev.pos("bl").adds(0, 5));

        prev = showNotification = content.add(new CheckBox("Show notification when tree found") {
            public void set(boolean val) {
                tempSettings.showNotification = val;
                a = val;
                updateShowNotificationState();
            }
        }, prev.pos("bl").adds(0, 10));

        prev = showNotificationLabel = content.add(new Label("Minimum growth for notification: 100%"), prev.pos("bl").adds(0, 5));
        prev = showNotificationSlider = content.add(new HSlider(UI.scale(200), 100, 300, tempSettings.showNotificationMinGrowth) {
            public void changed() {
                tempSettings.showNotificationMinGrowth = val;
                showNotificationLabel.settext("Minimum growth for notification: " + val + "%");
            }
        }, prev.pos("bl").adds(0, 5));

        prev = notificationAutoCloseLabel = content.add(new Label("Notification auto-close time: 30 sec"), prev.pos("bl").adds(0, 10));
        prev = notificationAutoCloseSlider = content.add(new HSlider(UI.scale(200), 1, 120, tempSettings.notificationAutoCloseTime) {
            public void changed() {
                tempSettings.notificationAutoCloseTime = val;
                notificationAutoCloseLabel.settext("Notification auto-close time: " + val + " sec");
            }
        }, prev.pos("bl").adds(0, 5));
        
        // Pack content and update scrollbar
        content.pack();
        scrollport.cont.update();
        
        pack();
        updateSaveToMapState();
        updateShowNotificationState();
    }

    private void updateSaveToMapState() {
        boolean enabledState = tempSettings.saveToMap;
        saveToMapSlider.visible = enabledState;
        saveToMapLabel.visible = enabledState;
    }

    private void updateShowNotificationState() {
        boolean enabledState = tempSettings.showNotification;
        showNotificationSlider.visible = enabledState;
        showNotificationLabel.visible = enabledState;
    }

    @Override
    public void load() {
        // Load current settings into temporary structure
        tempSettings.enabled = (Boolean) NConfig.get(NConfig.Key.treeFinderEnabled);
        tempSettings.saveToMap = (Boolean) NConfig.get(NConfig.Key.treeFinderSaveToMap);
        Object saveToMapMinGrowthObj = NConfig.get(NConfig.Key.treeFinderSaveToMapMinGrowth);
        tempSettings.saveToMapMinGrowth = (saveToMapMinGrowthObj instanceof Integer) ? (Integer) saveToMapMinGrowthObj : 100;
        tempSettings.showNotification = (Boolean) NConfig.get(NConfig.Key.treeFinderShowNotification);
        Object showNotificationMinGrowthObj = NConfig.get(NConfig.Key.treeFinderShowNotificationMinGrowth);
        tempSettings.showNotificationMinGrowth = (showNotificationMinGrowthObj instanceof Integer) ? (Integer) showNotificationMinGrowthObj : 100;
        Object notificationAutoCloseTimeObj = NConfig.get(NConfig.Key.treeFinderNotificationAutoCloseTime);
        tempSettings.notificationAutoCloseTime = (notificationAutoCloseTimeObj instanceof Integer) ? (Integer) notificationAutoCloseTimeObj : 30;

        // Update UI components
        enabled.a = tempSettings.enabled;
        saveToMap.a = tempSettings.saveToMap;
        saveToMapSlider.val = tempSettings.saveToMapMinGrowth;
        saveToMapLabel.settext("Minimum growth for saving: " + tempSettings.saveToMapMinGrowth + "%");
        showNotification.a = tempSettings.showNotification;
        showNotificationSlider.val = tempSettings.showNotificationMinGrowth;
        showNotificationLabel.settext("Minimum growth for notification: " + tempSettings.showNotificationMinGrowth + "%");
        notificationAutoCloseSlider.val = tempSettings.notificationAutoCloseTime;
        notificationAutoCloseLabel.settext("Notification auto-close time: " + tempSettings.notificationAutoCloseTime + " sec");
        updateSaveToMapState();
        updateShowNotificationState();
    }

    @Override
    public void save() {
        // Save temporary settings to config
        NConfig.set(NConfig.Key.treeFinderEnabled, tempSettings.enabled);
        NConfig.set(NConfig.Key.treeFinderSaveToMap, tempSettings.saveToMap);
        NConfig.set(NConfig.Key.treeFinderSaveToMapMinGrowth, tempSettings.saveToMapMinGrowth);
        NConfig.set(NConfig.Key.treeFinderShowNotification, tempSettings.showNotification);
        NConfig.set(NConfig.Key.treeFinderShowNotificationMinGrowth, tempSettings.showNotificationMinGrowth);
        NConfig.set(NConfig.Key.treeFinderNotificationAutoCloseTime, tempSettings.notificationAutoCloseTime);

        // Mark configuration as needing update to file
        NConfig.needUpdate();
    }
}

