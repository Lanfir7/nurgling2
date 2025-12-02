package nurgling.widgets.options;

import haven.*;
import haven.Label;
import nurgling.NConfig;
import nurgling.NUtils;
import nurgling.areas.db.AreaDBManager;
import nurgling.widgets.nsettings.Panel;

import java.awt.Color;

public class SyncServerSettings extends Panel {
    private Widget prev;
    private TextEntry serverUrlEntry;
    private TextEntry zoneSyncEntry;
    private Label serverUrlLabel, zoneSyncLabel, syncIntervalLabel;
    private CheckBox enableCheckbox;
    private HSlider syncIntervalSlider;
    private Label intervalValueLabel;
    private final int labelWidth = UI.scale(120);
    private final int entryX = UI.scale(140);
    private final int margin = UI.scale(10);
    private final int sliderWidth = UI.scale(200);

    private boolean enabled;
    private String serverUrl;
    private String zoneSync;
    private int syncIntervalMinutes; // 1-20 минут

    public SyncServerSettings() {
        super("");
        int y = margin;

        // Чекбокс включения/выключения синхронизации
        prev = enableCheckbox = add(new CheckBox("Enable Zone Synchronization") {
            public void set(boolean val) {
                a = val;
                enabled = val;
                updateWidgetsVisibility();
            }
        }, new Coord(margin, y));
        y += enableCheckbox.sz.y + UI.scale(8);

        // Заголовок раздела
        prev = add(new Label("Sync Server Settings:"), new Coord(margin, y));
        y += prev.sz.y + UI.scale(5);

        // Поле для адреса сервера
        serverUrlLabel = add(new Label("Server URL:"), new Coord(margin, y));
        serverUrlEntry = add(new TextEntry(UI.scale(300), ""), new Coord(entryX, y));
        y += serverUrlEntry.sz.y + UI.scale(8);

        // Поле для zone_sync (идентификатор мира)
        zoneSyncLabel = add(new Label("Zone Sync ID:"), new Coord(margin, y));
        zoneSyncEntry = add(new TextEntry(UI.scale(200), ""), new Coord(entryX, y));
        y += zoneSyncEntry.sz.y + UI.scale(8);

        // Ползунок для интервала синхронизации
        syncIntervalLabel = add(new Label("Sync Interval:"), new Coord(margin, y));
        
        // Создаем слайдер (1-20 минут)
        syncIntervalSlider = add(new HSlider(sliderWidth, 1, 20, 5) {
            @Override
            public void changed() {
                super.changed();
                syncIntervalMinutes = this.val;
                updateIntervalLabel();
            }
            
            @Override
            public void fchanged() {
                super.fchanged();
                syncIntervalMinutes = this.val;
                updateIntervalLabel();
            }
        }, new Coord(entryX, y));
        
        // Метка для отображения текущего значения
        intervalValueLabel = add(new Label("5 min"), new Coord(entryX + sliderWidth + UI.scale(10), y));
        y += syncIntervalSlider.sz.y + UI.scale(10);

        load();
        updateWidgetsVisibility();
    }

    private void updateIntervalLabel() {
        if (intervalValueLabel != null) {
            intervalValueLabel.settext(syncIntervalMinutes + " min");
        }
    }

    @Override
    public void load() {
        enabled = getBool(NConfig.Key.syncServerEnabled);
        enableCheckbox.a = enabled;

        serverUrl = asString(NConfig.get(NConfig.Key.syncServerUrl));
        zoneSync = asString(NConfig.get(NConfig.Key.syncZoneSync));
        
        // Загружаем интервал синхронизации (по умолчанию 5 минут)
        Object intervalObj = NConfig.get(NConfig.Key.syncIntervalMinutes);
        if (intervalObj instanceof Integer) {
            syncIntervalMinutes = (Integer) intervalObj;
        } else if (intervalObj instanceof Number) {
            syncIntervalMinutes = ((Number) intervalObj).intValue();
        } else {
            syncIntervalMinutes = 5; // По умолчанию 5 минут
        }
        
        // Ограничиваем значение диапазоном 1-20
        if (syncIntervalMinutes < 1) syncIntervalMinutes = 1;
        if (syncIntervalMinutes > 20) syncIntervalMinutes = 20;

        serverUrlEntry.settext(serverUrl);
        zoneSyncEntry.settext(zoneSync);
        syncIntervalSlider.val = syncIntervalMinutes;
        updateIntervalLabel();

        updateWidgetsVisibility();
    }

    @Override
    public void save() {
        enabled = enableCheckbox.a;
        serverUrl = serverUrlEntry.text().trim();
        zoneSync = zoneSyncEntry.text().trim();
        syncIntervalMinutes = syncIntervalSlider.val;

        NConfig.set(NConfig.Key.syncServerEnabled, enabled);
        NConfig.set(NConfig.Key.syncServerUrl, serverUrl);
        NConfig.set(NConfig.Key.syncZoneSync, zoneSync);
        NConfig.set(NConfig.Key.syncIntervalMinutes, syncIntervalMinutes);

        // Инициализируем синхронизацию если включена
        if (enabled && !serverUrl.isEmpty() && !zoneSync.isEmpty()) {
            try {
                AreaDBManager.getInstance().initializeSync(serverUrl, zoneSync);
                if (NUtils.getGameUI() != null) {
                    NUtils.getGameUI().msg("Zone synchronization enabled", Color.GREEN);
                }
            } catch (Exception e) {
                System.err.println("SyncServerSettings: Failed to initialize sync: " + e.getMessage());
                e.printStackTrace();
                if (NUtils.getGameUI() != null) {
                    NUtils.getGameUI().msg("Failed to enable synchronization: " + e.getMessage(), Color.RED);
                }
            }
        } else if (!enabled) {
            // Отключаем синхронизацию
            if (NUtils.getGameUI() != null) {
                NUtils.getGameUI().msg("Zone synchronization disabled", Color.YELLOW);
            }
        }

        NConfig.needUpdate();
    }

    private void updateWidgetsVisibility() {
        boolean isEnabled = enabled;

        if (serverUrlLabel != null) {
            serverUrlLabel.visible = isEnabled;
            serverUrlEntry.visible = isEnabled;
            zoneSyncLabel.visible = isEnabled;
            zoneSyncEntry.visible = isEnabled;
            syncIntervalLabel.visible = isEnabled;
            syncIntervalSlider.visible = isEnabled;
            intervalValueLabel.visible = isEnabled;
        }

        // Переупаковываем виджет
        pack();
        sz.y = UI.scale(250);
    }

    private boolean getBool(NConfig.Key key) {
        Object val = NConfig.get(key);
        return val instanceof Boolean ? (Boolean) val : false;
    }

    private String asString(Object v) {
        return v == null ? "" : v.toString();
    }
}

