package nurgling.widgets.nsettings;

import haven.*;
import nurgling.NConfig;
import nurgling.NFlowerMenu;
import nurgling.NUI;
import nurgling.NUtils;
import nurgling.actions.bots.MasterMiner;
import nurgling.conf.NMasterMinerMarkingConfig;
import nurgling.widgets.AdaptiveSettingsPanel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiningMasterySettings extends Panel implements AdaptiveSettingsPanel {
    private static class ItemCheckbox extends Widget {
        private final CheckBox checkbox;
        private final Label nameLabel;
        private final Label thresholdLabel;
        private final String itemName;
        private double threshold;
        private final Runnable onThresholdChange;
        private static final int THRESHOLD_AREA_X = UI.scale(200); // Начало области threshold

        public ItemCheckbox(String itemName, double defaultThreshold, Runnable onThresholdChange) {
            super(Coord.z);
            this.itemName = itemName;
            this.threshold = defaultThreshold;
            this.onThresholdChange = onThresholdChange;

            checkbox = add(new CheckBox("") {
                @Override
                public void set(boolean val) {
                    boolean oldVal = a;
                    a = val;
                    // Сохраняем настройки при изменении чекбокса
                    if (oldVal != val && onThresholdChange != null) {
                        onThresholdChange.run();
                    }
                }
            }, new Coord(UI.scale(5), 0));

            nameLabel = add(new Label(itemName), new Coord(UI.scale(25), 0));
            
            // Threshold label - показываем только число без слова "threshold"
            String thresholdText = String.format("%.0f", threshold);
            thresholdLabel = add(new Label(thresholdText), new Coord(THRESHOLD_AREA_X, 0));
            
            resize(UI.scale(400), UI.scale(20));
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if (ev.b == 1 && checkhit(ev.c)) {
                // ЛКМ по правой части (где был threshold) - открываем окно редактирования
                if (ev.c.x >= THRESHOLD_AREA_X) {
                    editThreshold();
                    return true;
                }
            }
            return super.mousedown(ev);
        }

        private void editThreshold() {
            if (ui == null) return;
            Window thresholdWnd = new Window(UI.scale(300, 120), "Edit Threshold") {
                private TextEntry thresholdEntry;

                {
                    add(new Label("Enter quality threshold:"), new Coord(UI.scale(10), UI.scale(30)));
                    thresholdEntry = add(new TextEntry(UI.scale(100), String.valueOf((int)threshold)), 
                        new Coord(UI.scale(10), UI.scale(50)));
                    
                    add(new Button(UI.scale(80), "OK") {
                        @Override
                        public void click() {
                            try {
                                double newThreshold = Double.parseDouble(thresholdEntry.buf.line());
                                threshold = newThreshold;
                                thresholdLabel.settext(String.format("%.0f", threshold));
                                if (onThresholdChange != null) {
                                    onThresholdChange.run();
                                }
                            } catch (NumberFormatException e) {
                                // Ignore invalid input
                            }
                            parent.destroy();
                        }
                    }, new Coord(UI.scale(10), UI.scale(80)));
                    
                    add(new Button(UI.scale(80), "Cancel") {
                        @Override
                        public void click() {
                            parent.destroy();
                        }
                    }, new Coord(UI.scale(100), UI.scale(80)));
                }
            };
            ui.root.add(thresholdWnd, UI.scale(200, 200));
        }

        public boolean isEnabled() {
            return checkbox.a;
        }

        public void setEnabled(boolean enabled) {
            checkbox.a = enabled;
        }

        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double threshold) {
            this.threshold = threshold;
            // Обновляем текст label - только число
            thresholdLabel.settext(String.format("%.0f", threshold));
        }

        public String getItemName() {
            return itemName;
        }
    }

    private final Map<String, ItemCheckbox> checkboxes = new HashMap<>();
    private final List<ItemCheckbox> checkboxOrder = new ArrayList<>();
    private Scrollport scrollport;
    private boolean initialized = false;

    public MiningMasterySettings() {
        super();
        // Инициализация отложена до первого вызова load() или show()
    }
    
    private void initializeIfNeeded() {
        if (initialized) return;
        initialized = true;
        
        int margin = UI.scale(10);
        int y = UI.scale(36);

        add(new Label("Select which items to mark on map when mining:"), new Coord(margin, y));
        
        // Вычисляем позицию справа для кнопок - справа от верхнего текста
        int labelWidth = UI.scale(330); // Keeps both action buttons inside the standard page width.
        int buttonX = margin + labelWidth + UI.scale(20); // Справа от текста с отступом
        int buttonY = y; // На той же высоте, что и первый label
        
        // Кнопка "Threshold for all stones" - устанавливает порог для всех камней (кроме руд и драгоценных камней)
        add(new Button(UI.scale(150), "Threshold for all stones") {
            @Override
            public void click() {
                showThresholdForAllDialog();
            }
        }, new Coord(buttonX, buttonY));
        buttonY += UI.scale(30);
        
        // Кнопка "Threshold for all ores" - устанавливает порог для всех руд
        add(new Button(UI.scale(150), "Threshold for all ores") {
            @Override
            public void click() {
                showThresholdForAllOresDialog();
            }
        }, new Coord(buttonX, buttonY));
        
        y += UI.scale(32);
        add(new Label("Click on right side of item to edit quality threshold"), new Coord(margin, y));
        y += UI.scale(25);

        // Создаем список всех камней, руд и драгоценных камней (статический список для оптимизации)
        // Используем предварительно отсортированный список чтобы избежать сортировки при каждом создании
        List<String> allItems = Arrays.asList(
            "Alabaster", "Apatite", "Arkose", "Basalt", "Bat Rock",
            "Black Coal", "Black Ore", "Bloodstone", "Breccia", "Cassiterite",
            "Cat Gold", "Chalcopyrite", "Chert", "Cinnabar", "Diabase",
            "Diorite", "Direvein", "Dolomite", "Dross", "Eclogite",
            "Feldspar", "Flint", "Fluorospar", "Gabbro", "Galena",
            "Gneiss", "Granite", "Graywacke", "Greenschist", "Heavy Earth",
            "Horn Silver", "Hornblende", "Iron Ochre", "Jasper", "Korund",
            "Kyanite", "Lava Rock", "Lead Glance", "Leaf Ore", "Limestone",
            "Malachite", "Marble", "Meteorite", "Mica", "Microlite",
            "Obsidian", "Olivine", "Orthoclase", "Peacock Ore", "Pegmatite",
            "Petrified Seashell", "Petrified Shell", "Porphyry", "Pumice",
            "Quarryartz", "Quartz", "Rhyolite", "Rock Crystal", "Rock Salt",
            "Sandstone", "Schist", "Schrifterz", "Serpentine", "Shard of Conch",
            "Silvershine", "Slag", "Slate", "Soapstone", "Sodalite",
            "Sunstone", "Wine Glance", "Zincspar",
            // Драгоценные камни (gemstones)
            "Amber", "Amethyst", "Diamond", "Dust Jewel", "Emerald", "Jade",
            "Moonstone", "Onyx", "Opal", "Oyster Pearl", "Red Coral", "River Pearl",
            "Ruby", "Sapphire", "Star Shard", "Sugar Diamond", "Topaz", "Turquoise"
        );

        // Создаем Scrollport для прокрутки списка (шире для двух колонок)
        scrollport = add(new Scrollport(new Coord(UI.scale(560), UI.scale(400))), new Coord(margin, y));

        // Разделяем список на две колонки
        int columnWidth = UI.scale(270); // Ширина одной колонки
        int itemHeight = UI.scale(22);
        int itemsPerColumn = (allItems.size() + 1) / 2; // Округляем вверх
        
        int itemY = 0;
        int columnIndex = 0;
        int maxY = 0;
        for (String itemName : allItems) {
            ItemCheckbox itemCheckbox = new ItemCheckbox(itemName, 10.0, this::save);
            int columnX = columnIndex * columnWidth;
            scrollport.cont.add(itemCheckbox, new Coord(columnX, itemY));
            checkboxes.put(itemName, itemCheckbox);
            checkboxOrder.add(itemCheckbox);
            
            maxY = Math.max(maxY, itemY + itemHeight);
            
            // Переходим на следующую строку
            itemY += itemHeight;
            
            // Если достигли середины списка, переходим на вторую колонку
            if (itemY >= itemsPerColumn * itemHeight) {
                itemY = 0;
                columnIndex = 1;
            }
        }
        
        // Устанавливаем размер контента для двух колонок
        scrollport.cont.resize(columnWidth * 2, maxY);
    }

    @Override
    public void fitToWidth(int width, int columns) {
        fitToViewport(Coord.of(width, sz.y), columns);
    }

    @Override
    public void fitToViewport(Coord viewport, int columns) {
        initializeIfNeeded();
        int margin = UI.scale(10);
        int itemHeight = UI.scale(22);
        int y = 0;
        int contentWidth = Math.max(UI.scale(400), viewport.x - (margin * 2));
        for(ItemCheckbox item : checkboxOrder) {
            item.move(Coord.of(0, y));
            item.resize(Coord.of(contentWidth, itemHeight));
            y += itemHeight;
        }
        scrollport.cont.resize(Coord.of(contentWidth, y));
        scrollport.resize(Coord.of(
                Math.max(1, viewport.x - (margin * 2)),
                Math.max(UI.scale(80), viewport.y - scrollport.c.y - margin)));
        scrollport.cont.update();
        resize(viewport);
    }

    @Override
    public boolean ownsVerticalScroll() {
        return true;
    }

    @Override
    public void load() {
        initializeIfNeeded();
        NMasterMinerMarkingConfig config = NMasterMinerMarkingConfig.get();
        if (config == null) {
            // Создаем новый конфиг если его нет
            if (NUtils.getGameUI() != null && NUtils.getGameUI().getCharInfo() != null) {
                NUI.NSessInfo sessInfo = ((NUI)NUtils.getGameUI().ui).sessInfo;
                if (sessInfo != null) {
                    config = new NMasterMinerMarkingConfig(sessInfo.username, NUtils.getGameUI().getCharInfo().chrid);
                } else {
                    return; // Не можем загрузить без сессии
                }
            } else {
                return; // Не можем загрузить без сессии
            }
        }

        // Загружаем настройки для каждого элемента
        for (Map.Entry<String, ItemCheckbox> entry : checkboxes.entrySet()) {
            String itemName = entry.getKey();
            ItemCheckbox checkbox = entry.getValue();
            
            // По умолчанию: все руды включены с порогом 10, Quarryartz тоже включен
            boolean isOre = MasterMiner.isOre(itemName) || 
                           itemName.equals("Black Coal") || 
                           itemName.equals("Quartz") || 
                           itemName.equals("Flint");
            boolean isQuarryartz = itemName.equals("Quarryartz");
            // Проверяем, является ли это драгоценным камнем
            boolean isGemstone = MasterMiner.isGemstone(itemName);
            
            Boolean enabledObj = config.isEnabled(itemName);
            boolean enabled;
            if (enabledObj == null) {
                // По умолчанию руды, Quarryartz и драгоценные камни включены
                enabled = isOre || isQuarryartz || isGemstone;
                // Логируем для драгоценных камней
                if (isGemstone) {
                    System.out.println("Loading gemstone config: " + itemName + " -> default enabled: " + enabled);
                }
            } else {
                enabled = enabledObj;
                // Логируем для драгоценных камней
                if (isGemstone) {
                    System.out.println("Loading gemstone config: " + itemName + " -> loaded enabled: " + enabled);
                }
            }
            
            Double threshold = config.getThreshold(itemName);
            if (threshold == null) {
                // По умолчанию руды, Quarryartz и драгоценные камни с порогом 10
                threshold = (isOre || isQuarryartz || isGemstone) ? 10.0 : Double.NaN;
            }
            
            checkbox.setEnabled(enabled);
            checkbox.setThreshold(threshold.isNaN() ? 10.0 : threshold);
        }
    }

    @Override
    public void save() {
        if (!initialized) return;
        NMasterMinerMarkingConfig config = NMasterMinerMarkingConfig.get();
        if (config == null) {
            // Создаем новый конфиг если его нет
            if (NUtils.getGameUI() != null && NUtils.getGameUI().getCharInfo() != null) {
                NUI.NSessInfo sessInfo = ((NUI)NUtils.getGameUI().ui).sessInfo;
                if (sessInfo != null) {
                    config = new NMasterMinerMarkingConfig(sessInfo.username, NUtils.getGameUI().getCharInfo().chrid);
                } else {
                    return; // Не можем сохранить без сессии
                }
            } else {
                return; // Не можем сохранить без сессии
            }
        }

        // Сохраняем настройки для каждого элемента
        for (Map.Entry<String, ItemCheckbox> entry : checkboxes.entrySet()) {
            String itemName = entry.getKey();
            ItemCheckbox checkbox = entry.getValue();
            
            config.setEnabled(itemName, checkbox.isEnabled());
            config.setThreshold(itemName, checkbox.getThreshold());
            
            // Логируем сохранение для драгоценных камней для отладки
            if (MasterMiner.isGemstone(itemName)) {
                System.out.println("Saving gemstone config: " + itemName + " -> enabled: " + checkbox.isEnabled() + ", threshold: " + checkbox.getThreshold());
            }
        }
        
        NMasterMinerMarkingConfig.set(config);
        NConfig.needUpdate();
        System.out.println("MiningMasterySettings saved successfully");
    }
    
    /**
     * Показывает диалог для установки порога для всех обычных камней (кроме руд и драгоценных камней)
     */
    private void showThresholdForAllDialog() {
        if (ui == null) return;
        Window thresholdDialog = new Window(UI.scale(300, 140), "Threshold for all") {
            private TextEntry thresholdEntry;

            {
                add(new Label("Enter quality threshold for all stones"), new Coord(UI.scale(10), UI.scale(30)));
                add(new Label("(except ores and gemstones):"), new Coord(UI.scale(10), UI.scale(50)));
                thresholdEntry = add(new TextEntry(UI.scale(100), "10"), 
                    new Coord(UI.scale(10), UI.scale(70)));
                
                add(new Button(UI.scale(80), "OK") {
                    @Override
                    public void click() {
                        try {
                            double threshold = Double.parseDouble(thresholdEntry.buf.line());
                            // Устанавливаем порог только для обычных камней (исключая руды и драгоценные камни)
                            for (Map.Entry<String, ItemCheckbox> entry : checkboxes.entrySet()) {
                                String itemName = entry.getKey();
                                ItemCheckbox checkbox = entry.getValue();
                                
                                // Проверяем, является ли это рудой
                                boolean isOre = MasterMiner.isOre(itemName) || 
                                               itemName.equals("Black Coal") || 
                                               itemName.equals("Quartz") || 
                                               itemName.equals("Flint");
                                // Проверяем, является ли это драгоценным камнем
                                boolean isGemstone = MasterMiner.isGemstone(itemName);
                                
                                // Устанавливаем порог только для обычных камней (не руды и не драгоценные камни)
                                if (!isOre && !isGemstone) {
                                    checkbox.setThreshold(threshold);
                                }
                            }
                            // Сохраняем изменения
                            save();
                            parent.destroy();
                        } catch (NumberFormatException e) {
                            // Ignore invalid input
                        }
                    }
                }, new Coord(UI.scale(10), UI.scale(100)));
                
                add(new Button(UI.scale(80), "Cancel") {
                    @Override
                    public void click() {
                        parent.destroy();
                    }
                }, new Coord(UI.scale(100), UI.scale(100)));
            }
        };
        ui.root.add(thresholdDialog, UI.scale(200, 200));
    }
    
    /**
     * Показывает диалог для установки порога для всех руд
     */
    private void showThresholdForAllOresDialog() {
        if (ui == null) return;
        Window thresholdDialog = new Window(UI.scale(300, 140), "Threshold for all ores") {
            private TextEntry thresholdEntry;

            {
                add(new Label("Enter quality threshold for all ores:"), new Coord(UI.scale(10), UI.scale(30)));
                thresholdEntry = add(new TextEntry(UI.scale(100), "10"), 
                    new Coord(UI.scale(10), UI.scale(70)));
                
                add(new Button(UI.scale(80), "OK") {
                    @Override
                    public void click() {
                        try {
                            double threshold = Double.parseDouble(thresholdEntry.buf.line());
                            // Устанавливаем порог только для руд
                            for (Map.Entry<String, ItemCheckbox> entry : checkboxes.entrySet()) {
                                String itemName = entry.getKey();
                                ItemCheckbox checkbox = entry.getValue();
                                
                                // Проверяем, является ли это рудой
                                boolean isOre = MasterMiner.isOre(itemName) || 
                                               itemName.equals("Black Coal") || 
                                               itemName.equals("Quartz") || 
                                               itemName.equals("Flint");
                                
                                // Устанавливаем порог только для руд
                                if (isOre) {
                                    checkbox.setThreshold(threshold);
                                }
                            }
                            // Сохраняем изменения
                            save();
                            parent.destroy();
                        } catch (NumberFormatException e) {
                            // Ignore invalid input
                        }
                    }
                }, new Coord(UI.scale(10), UI.scale(100)));
                
                add(new Button(UI.scale(80), "Cancel") {
                    @Override
                    public void click() {
                        parent.destroy();
                    }
                }, new Coord(UI.scale(100), UI.scale(100)));
            }
        };
        ui.root.add(thresholdDialog, UI.scale(200, 200));
    }
}
