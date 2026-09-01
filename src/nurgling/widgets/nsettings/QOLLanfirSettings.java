package nurgling.widgets.nsettings;

import haven.*;
import nurgling.NConfig;
import nurgling.NUtils;
import nurgling.i18n.L10n;
import nurgling.sessions.SessionContext;
import nurgling.sessions.SessionManager;
import nurgling.tools.ForageMarkerLogic;
import nurgling.tools.TreeGrowth;
import nurgling.widgets.compass.NCompassSettings;

public class QOLLanfirSettings extends Panel {
    
    private CheckBox treeResizeEnabled;
    private CheckBox equipSwordShieldOnAttack;
    private CheckBox showCompassBar;
    private CheckBox showLegacyCompassPointers;
    private CheckBox showCompassQuests;
    private CheckBox showCompassParty;
    private CheckBox showCompassDatabasePeers;
    private CheckBox showCompassNearbyPlayers;
    private CheckBox showCompassCombatTargets;
    private HSlider treeResizePercentageSlider;
    private Label treeResizePercentageLabel;
    
    private HSlider permIconScaleSlider;
    private Label permIconScaleLabel;
    private HSlider prospectIconScaleSlider;
    private Label prospectIconScaleLabel;
    private HSlider forageMarkerMinQualitySlider;
    private Label forageMarkerMinQualityLabel;
    private HSlider compassBackgroundOpacitySlider;
    private Label compassBackgroundOpacityLabel;
    
    public QOLLanfirSettings() {
        super("QOL Lanfir");
        
        int y = UI.scale(40);
        int margin = UI.scale(10);
        
        // TreeResiz section
        add(new Label("TreeResiz"), margin, y);
        y += UI.scale(25);
        
        treeResizeEnabled = add(new CheckBox("Enable tree resizing") {
            @Override
            public void changed(boolean val) {
                super.changed(val);
                updateSliderVisibility();
            }
        }, margin, y);
        y += UI.scale(30);
        
        // Slider for percentage (visible when checkbox is unchecked)
        add(new Label("Tree resize percentage:"), margin, y);
        y += UI.scale(20);
        
        treeResizePercentageLabel = new Label("100%");
        treeResizePercentageSlider = new HSlider(UI.scale(300), 0, 200, 100) {
            @Override
            public void changed() {
                treeResizePercentageLabel.settext(String.format("%d%%", this.val));
            }
        };
        
        addhlp(Coord.of(margin, y), UI.scale(5), treeResizePercentageSlider, treeResizePercentageLabel);
        y += UI.scale(50);
        
        // Icon scale section
        add(new Label("PermIcon Scale"), margin, y);
        y += UI.scale(20);
        
        permIconScaleLabel = new Label("100%");
        permIconScaleSlider = new HSlider(UI.scale(300), 1, 200, 100) {
            @Override
            public void changed() {
                permIconScaleLabel.settext(String.format("%d%%", this.val));
            }
        };
        
        addhlp(Coord.of(margin, y), UI.scale(5), permIconScaleSlider, permIconScaleLabel);
        y += UI.scale(50);
        
        add(new Label("Icon Scale"), margin, y);
        y += UI.scale(20);
        
        prospectIconScaleLabel = new Label("100%");
        prospectIconScaleSlider = new HSlider(UI.scale(300), 1, 200, 100) {
            @Override
            public void changed() {
                prospectIconScaleLabel.settext(String.format("%d%%", this.val));
            }
        };
        
        addhlp(Coord.of(margin, y), UI.scale(5), prospectIconScaleSlider, prospectIconScaleLabel);
        y += UI.scale(50);

        add(new Label("Forage marker min quality:"), margin, y);
        y += UI.scale(20);

        forageMarkerMinQualityLabel = new Label("40");
        forageMarkerMinQualitySlider = new HSlider(UI.scale(300), 10, 100, 40) {
            @Override
            public void changed() {
                forageMarkerMinQualityLabel.settext(String.valueOf(this.val));
                NConfig.set(NConfig.Key.forageMarkerMinQuality, this.val);
                NConfig.needUpdate();
            }
        };
        addhlp(Coord.of(margin, y), UI.scale(5), forageMarkerMinQualitySlider, forageMarkerMinQualityLabel);
        y += UI.scale(50);

        equipSwordShieldOnAttack = add(new CheckBox(L10n.get("qol.equip_sword_shield_on_attack")), margin, y);
        y += UI.scale(30);

        showCompassBar = add(new CheckBox(L10n.get("qol.compass_bar")) {
            @Override
            public void changed(boolean val) {
                super.changed(val);
                NConfig.set(NConfig.Key.showCompassBar, val);
                NConfig.needUpdate();
                for (SessionContext context : SessionManager.getInstance().getAllSessions()) {
                    nurgling.NGameUI sessionGui = context.getGameUI();
                    if (sessionGui == null)
                        continue;
                    if (context.ui != null) {
                        synchronized (context.ui) {
                            sessionGui.setCompassVisible(val);
                        }
                    } else {
                        sessionGui.setCompassVisible(val);
                    }
                }
            }
        }, margin, y);
        y += UI.scale(30);

        add(new Label(L10n.get("qol.compass_markers")), margin, y);
        y += UI.scale(22);

        showCompassQuests = add(compassCategory(
                L10n.get("qol.compass_quests"), NConfig.Key.showCompassQuests), margin, y);
        y += UI.scale(25);
        showCompassParty = add(compassCategory(
                L10n.get("qol.compass_party"), NConfig.Key.showCompassParty), margin, y);
        y += UI.scale(25);
        showCompassDatabasePeers = add(compassCategory(
                L10n.get("qol.compass_database_peers"), NConfig.Key.showCompassDatabasePeers), margin, y);
        y += UI.scale(25);
        showCompassNearbyPlayers = add(compassCategory(
                L10n.get("qol.compass_nearby_players"), NConfig.Key.showCompassNearbyPlayers), margin, y);
        y += UI.scale(25);
        showCompassCombatTargets = add(compassCategory(
                L10n.get("qol.compass_combat_targets"), NConfig.Key.showCompassCombatTargets), margin, y);
        y += UI.scale(32);

        add(new Label(L10n.get("qol.compass_background_opacity")), margin, y);
        y += UI.scale(20);
        compassBackgroundOpacityLabel = new Label("75%");
        compassBackgroundOpacitySlider = new HSlider(UI.scale(300), 0, 100, 75) {
            @Override
            public void changed() {
                compassBackgroundOpacityLabel.settext(this.val + "%");
                NConfig.set(NConfig.Key.compassBackgroundOpacity, this.val);
                NConfig.needUpdate();
            }
        };
        addhlp(Coord.of(margin, y), UI.scale(5),
                compassBackgroundOpacitySlider, compassBackgroundOpacityLabel);
        y += UI.scale(45);

        showLegacyCompassPointers = add(new CheckBox(L10n.get("qol.legacy_compass_pointers")) {
            @Override
            public void changed(boolean val) {
                super.changed(val);
                NConfig.set(NConfig.Key.showLegacyCompassPointers, val);
                NConfig.needUpdate();
            }
        }, margin, y);
        
        pack();
    }

    private CheckBox compassCategory(String label, NConfig.Key key) {
        return new CheckBox(label) {
            @Override
            public void changed(boolean val) {
                super.changed(val);
                NConfig.set(key, val);
                NConfig.needUpdate();
            }
        };
    }
    
    private void updateSliderVisibility() {
        // Slider is visible when checkbox is checked (function enabled)
        boolean visible = treeResizeEnabled.a;
        treeResizePercentageSlider.visible = visible;
        treeResizePercentageLabel.visible = visible;
    }
    
    @Override
    public void load() {
        treeResizeEnabled.a = getBool(NConfig.Key.treeResizeEnabled);
        
        Object percentage = NConfig.get(NConfig.Key.treeResizePercentage);
        int percentageValue = 100; // Default
        if (percentage instanceof Number) {
            percentageValue = ((Number) percentage).intValue();
        }
        treeResizePercentageSlider.val = percentageValue;
        treeResizePercentageLabel.settext(String.format("%d%%", percentageValue));
        
        updateSliderVisibility();
        
        // Load icon scale settings
        Object permScale = NConfig.get(NConfig.Key.permIconScale);
        int permScaleValue = 100; // Default
        if (permScale instanceof Number) {
            permScaleValue = ((Number) permScale).intValue();
        }
        permIconScaleSlider.val = permScaleValue;
        permIconScaleLabel.settext(String.format("%d%%", permScaleValue));
        
        Object prospectScale = NConfig.get(NConfig.Key.prospectIconScale);
        int prospectScaleValue = 100; // Default
        if (prospectScale instanceof Number) {
            prospectScaleValue = ((Number) prospectScale).intValue();
        }
        prospectIconScaleSlider.val = prospectScaleValue;
        prospectIconScaleLabel.settext(String.format("%d%%", prospectScaleValue));

        Object forageMinQ = NConfig.get(NConfig.Key.forageMarkerMinQuality);
        int forageMinQValue = ForageMarkerLogic.minQualityFromConfig(forageMinQ);
        forageMarkerMinQualitySlider.val = forageMinQValue;
        forageMarkerMinQualityLabel.settext(String.valueOf(forageMinQValue));

        equipSwordShieldOnAttack.a = getBool(NConfig.Key.equipSwordShieldOnAttack);
        showCompassBar.a = NCompassSettings.showBar();
        showLegacyCompassPointers.a = NCompassSettings.showLegacyPointers();
        showCompassQuests.a = NCompassSettings.showQuests();
        showCompassParty.a = NCompassSettings.showParty();
        showCompassDatabasePeers.a = NCompassSettings.showDatabasePeers();
        showCompassNearbyPlayers.a = NCompassSettings.showNearbyPlayers();
        showCompassCombatTargets.a = NCompassSettings.showCombatTargets();
        int compassOpacity = NCompassSettings.backgroundOpacity();
        compassBackgroundOpacitySlider.val = compassOpacity;
        compassBackgroundOpacityLabel.settext(compassOpacity + "%");
    }
    
    @Override
    public void save() {
        NConfig.set(NConfig.Key.treeResizeEnabled, treeResizeEnabled.a);
        NConfig.set(NConfig.Key.treeResizePercentage, treeResizePercentageSlider.val);
        NConfig.set(NConfig.Key.permIconScale, permIconScaleSlider.val);
        NConfig.set(NConfig.Key.prospectIconScale, prospectIconScaleSlider.val);
        NConfig.set(NConfig.Key.forageMarkerMinQuality, forageMarkerMinQualitySlider.val);
        NConfig.set(NConfig.Key.equipSwordShieldOnAttack, equipSwordShieldOnAttack.a);
        NConfig.set(NConfig.Key.showCompassBar, showCompassBar.a);
        NConfig.set(NConfig.Key.showLegacyCompassPointers, showLegacyCompassPointers.a);
        NConfig.set(NConfig.Key.showCompassQuests, showCompassQuests.a);
        NConfig.set(NConfig.Key.showCompassParty, showCompassParty.a);
        NConfig.set(NConfig.Key.showCompassDatabasePeers, showCompassDatabasePeers.a);
        NConfig.set(NConfig.Key.showCompassNearbyPlayers, showCompassNearbyPlayers.a);
        NConfig.set(NConfig.Key.showCompassCombatTargets, showCompassCombatTargets.a);
        NConfig.set(NConfig.Key.compassBackgroundOpacity, compassBackgroundOpacitySlider.val);
        NConfig.needUpdate();
        
        // Apply tree resizing to all existing trees in the world
        applyTreeResizing();
    }
    
    private void applyTreeResizing() {
        if (NUtils.getGameUI() == null || NUtils.getGameUI().ui == null || 
            NUtils.getGameUI().ui.sess == null) {
            return;
        }
        
        boolean enabled = treeResizeEnabled.a;
        int percentage = treeResizePercentageSlider.val;
        float scaleMultiplier = percentage / 100.0f;
        
        OCache oc = NUtils.getGameUI().ui.sess.glob.oc;
        synchronized(oc) {
            for(Gob gob : oc) {
                if(gob != null && gob.ngob != null && gob.ngob.name != null) {
                    String resName = gob.ngob.name;
                    // Check if it's a tree (not a log or oldtrunk)
                    if(resName.startsWith("gfx/terobjs/trees") && 
                       !resName.endsWith("oldtrunk")) {
                        haven.res.lib.tree.TreeScale ts = gob.getattr(haven.res.lib.tree.TreeScale.class);
                        
                        if(enabled) {
                            float originalScale = (ts != null) ? TreeGrowth.serverScale(ts) : 1.0f;
                            float newScale = originalScale * scaleMultiplier;
                            gob.delattr(haven.res.lib.tree.TreeScale.class);
                            gob.setattr(new haven.res.lib.tree.TreeScale(gob, newScale, originalScale));
                        } else {
                            // Remove custom scaling - restore to original scale if we have it
                            if(ts != null) {
                                if(ts.originalScale > 0 && ts.originalScale != ts.scale) {
                                    // Restore original scale
                                    gob.delattr(haven.res.lib.tree.TreeScale.class);
                                    gob.setattr(new haven.res.lib.tree.TreeScale(gob, ts.originalScale));
                                } else {
                                    // Remove TreeScale to let Tree constructor handle natural scaling
                                    gob.delattr(haven.res.lib.tree.TreeScale.class);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private boolean getBool(NConfig.Key key) {
        Object val = NConfig.get(key);
        return val instanceof Boolean ? (Boolean) val : false;
    }
}

