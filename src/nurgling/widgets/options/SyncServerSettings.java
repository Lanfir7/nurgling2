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
    private int syncIntervalSeconds; // 5 секунд - 20 минут (5-1200 секунд)

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
        
        // Создаем слайдер (5-1200 секунд, т.е. от 5 сек до 20 минут)
        // Значение по умолчанию: 300 секунд (5 минут)
        syncIntervalSlider = add(new HSlider(sliderWidth, 5, 1200, 300) {
            @Override
            public void changed() {
                super.changed();
                syncIntervalSeconds = this.val;
                updateIntervalLabel();
            }
            
            @Override
            public void fchanged() {
                super.fchanged();
                syncIntervalSeconds = this.val;
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
            String text;
            if (syncIntervalSeconds < 60) {
                text = syncIntervalSeconds + " sec";
            } else {
                int minutes = syncIntervalSeconds / 60;
                int remainingSeconds = syncIntervalSeconds % 60;
                if (remainingSeconds == 0) {
                    text = minutes + " min";
                } else {
                    text = minutes + " min " + remainingSeconds + " sec";
                }
            }
            intervalValueLabel.settext(text);
        }
    }

    @Override
    public void load() {
        enabled = getBool(NConfig.Key.syncServerEnabled);
        enableCheckbox.a = enabled;

        serverUrl = asString(NConfig.get(NConfig.Key.syncServerUrl));
        zoneSync = asString(NConfig.get(NConfig.Key.syncZoneSync));
        
        // Загружаем интервал синхронизации (по умолчанию 300 секунд = 5 минут)
        Object intervalObj = NConfig.get(NConfig.Key.syncIntervalMinutes);
        if (intervalObj instanceof Integer) {
            syncIntervalSeconds = (Integer) intervalObj;
        } else if (intervalObj instanceof Number) {
            syncIntervalSeconds = ((Number) intervalObj).intValue();
        } else {
            syncIntervalSeconds = 300; // По умолчанию 300 секунд (5 минут)
        }
        
        // Все значения хранятся в секундах (начиная с новой версии)
        // Ограничиваем значение диапазоном 5-1200 секунд
        if (syncIntervalSeconds < 5) syncIntervalSeconds = 5;
        if (syncIntervalSeconds > 1200) syncIntervalSeconds = 1200;

        serverUrlEntry.settext(serverUrl);
        zoneSyncEntry.settext(zoneSync);
        syncIntervalSlider.val = syncIntervalSeconds;
        updateIntervalLabel();

        updateWidgetsVisibility();
    }

    @Override
    public void save() {
        enabled = enableCheckbox.a;
        serverUrl = serverUrlEntry.text().trim();
        zoneSync = zoneSyncEntry.text().trim();
        syncIntervalSeconds = syncIntervalSlider.val;

        System.out.println("SyncServerSettings: Saving sync interval: " + syncIntervalSeconds + " seconds");

        NConfig.set(NConfig.Key.syncServerEnabled, enabled);
        NConfig.set(NConfig.Key.syncServerUrl, serverUrl);
        NConfig.set(NConfig.Key.syncZoneSync, zoneSync);
        NConfig.set(NConfig.Key.syncIntervalMinutes, syncIntervalSeconds);

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

