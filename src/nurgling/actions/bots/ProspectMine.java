package nurgling.actions.bots;

import haven.*;
import nurgling.NGameUI;
import nurgling.NGItem;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.actions.SelectFlowerAction;
import nurgling.tasks.*;
import nurgling.tools.NAlias;
import nurgling.widgets.NProspecting;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProspectMine implements Action {
    private static final Pattern DETECT_PATTERN = Pattern.compile("There appears to be (.*) directly below\\.");
    private static final Pattern NO_MINERALS_PATTERN = Pattern.compile("No minerals found directly below\\.");

    static Coord2d snapshotRc(Coord2d rc) {
        return rc == null ? null : Coord2d.of(rc.x, rc.y);
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        try {
            // Найти Clay Jar с rustroot extract в инвентаре
            WItem jar = findClayJarWithRustrootExtract(gui);
            if (jar == null) {
                return Results.ERROR("Clay Jar с rustroot extract не найден в инвентаре");
            }

            Gob player = gui.map != null ? gui.map.player() : null;
            Coord2d startPos = snapshotRc(player != null ? player.rc : null);
            if (startPos == null) {
                return Results.ERROR("Игрок не найден");
            }

            // ПКМ по предмету и выбор опции "Prospect"
            Results result = new SelectFlowerAction("Prospect", jar).run(gui);
            if (!result.IsSuccess()) {
                return Results.ERROR("Не удалось выбрать опцию 'Prospect'");
            }

            // Ждем появления окна проспектинга
            NUtils.getUI().core.addTask(new WaitWindow("Prospecting"));
            
            // Ждем завершения анимации (песочные часы)
            Thread.sleep(2000); // Даем время на анимацию
            
            // Ждем появления текста в окне
            String resourceType = waitForProspectingResult(gui);
            if (resourceType == null) {
                return Results.ERROR("Не удалось получить результат проспектинга");
            }
            
            // Маркер — в месте старта макроса, а не там, где игрок оказался после ожидания
            if (gui.prospectingLocationService != null) {
                gui.prospectingLocationService.saveProspectingLocation(resourceType, startPos);
            }

            // Закрываем окно проспектинга
            Window prospectingWindow = gui.getWindow("Prospecting");
            if (prospectingWindow != null) {
                // Ищем кнопку Dismiss
                for (Widget w = prospectingWindow.lchild; w != null; w = w.prev) {
                    if (w instanceof Button) {
                        Button btn = (Button) w;
                        // Проверяем текст кнопки через рефлексию или просто кликаем первую кнопку
                        try {
                            java.lang.reflect.Field textField = Button.class.getDeclaredField("text");
                            textField.setAccessible(true);
                            Text text = (Text) textField.get(btn);
                            if (text != null && text.text != null && text.text.contains("Dismiss")) {
                                btn.click();
                                break;
                            }
                        } catch (Exception e) {
                            // Если не удалось проверить текст, просто кликаем первую кнопку
                            btn.click();
                            break;
                        }
                    }
                }
            }

            return Results.SUCCESS();
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            return Results.ERROR("Ошибка: " + e.getMessage());
        }
    }

    /**
     * Находит Clay Jar с rustroot extract в инвентаре
     */
    private WItem findClayJarWithRustrootExtract(NGameUI gui) throws InterruptedException {
        // Ищем все предметы в инвентаре, которые могут быть jar с rustroot extract
        ArrayList<WItem> jars = new ArrayList<>();
        ArrayList<WItem> allItems = gui.getInventory().getItems();
        
        for (WItem item : allItems) {
            if (item.item instanceof NGItem) {
                NGItem ngItem = (NGItem) item.item;
                try {
                    String resourceName = ngItem.res != null ? ngItem.res.get().name : null;
                    String itemName = ngItem.name();
                    
                    // Проверяем по ресурсу jar-rustjuice или jar-rustroot
                    if (resourceName != null && (resourceName.contains("jar-rustjuice") || 
                        resourceName.contains("jar-rustroot") ||
                        (resourceName.contains("jar") && resourceName.contains("rust")))) {
                        jars.add(item);
                    } 
                    // Также проверяем по имени "Clay Jar"
                    else if (itemName != null && itemName.toLowerCase().contains("clay jar")) {
                        jars.add(item);
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки проверки
                }
            }
        }
        
        for (WItem jar : jars) {
            // Метод 1: Проверяем содержимое через NGItem.content() (NContent)
            if (jar.item instanceof NGItem) {
                NGItem ngItem = (NGItem) jar.item;
                try {
                    java.util.List<NGItem.NContent> contents = ngItem.content();
                    if (contents != null) {
                        for (NGItem.NContent content : contents) {
                            String contentName = content.name();
                            if (contentName != null) {
                                String lowerName = contentName.toLowerCase();
                                // Проверяем различные варианты: "rustroot extract", "doses of rustroot extract", "rustroot"
                                if (lowerName.contains("rustroot extract") || 
                                    lowerName.contains("rustroot") ||
                                    lowerName.contains("doses of rustroot")) {
                                    return jar;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
            }
            
            // Метод 2: Проверяем содержимое через gitem.contents (NInventory)
            if (jar.item instanceof GItem) {
                GItem gitem = (GItem) jar.item;
                if (gitem.contents != null && gitem.contents instanceof NInventory) {
                    NInventory jarInventory = (NInventory) gitem.contents;
                    // Ищем rustroot extract в содержимом кувшина
                    ArrayList<WItem> items = jarInventory.getItems(new NAlias("rustroot extract"));
                    if (!items.isEmpty()) {
                        return jar;
                    }
                    // Также проверяем через частичное совпадение
                    items = jarInventory.getItems(new NAlias("rustroot"));
                    if (!items.isEmpty()) {
                        return jar;
                    }
                }
            }
            
            // Метод 3: Проверяем содержимое через ItemInfo (на случай если оно там есть)
            if (jar.item instanceof GItem) {
                GItem gitem = (GItem) jar.item;
                if (gitem.info != null) {
                    for (ItemInfo info : gitem.info) {
                        // Проверяем ItemInfo.Contents для содержимого контейнера
                        if (info instanceof ItemInfo.Contents) {
                            ItemInfo.Contents contents = (ItemInfo.Contents) info;
                            if (contents.sub != null) {
                                for (ItemInfo subInfo : contents.sub) {
                                    if (subInfo instanceof ItemInfo.Name) {
                                        String name = ((ItemInfo.Name) subInfo).str.text;
                                        if (name.toLowerCase().contains("rustroot extract") || 
                                            name.toLowerCase().contains("rustroot")) {
                                            return jar;
                                        }
                                    }
                                }
                            }
                        }
                        // Проверяем ItemInfo.AdHoc для текстового описания содержимого
                        else if (info instanceof ItemInfo.AdHoc) {
                            String text = ((ItemInfo.AdHoc) info).str.text;
                            if (text != null && (text.toLowerCase().contains("rustroot extract") || 
                                text.toLowerCase().contains("rustroot"))) {
                                return jar;
                            }
                        }
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Ждет появления результата проспектинга в окне
     */
    private String waitForProspectingResult(NGameUI gui) throws InterruptedException {
        // Ждем до 10 секунд появления текста в окне
        for (int i = 0; i < 100; i++) {
            Window prospectingWindow = gui.getWindow("Prospecting");
            if (prospectingWindow instanceof NProspecting) {
                NProspecting np = (NProspecting) prospectingWindow;
                
                // Пытаемся получить текст из окна
                // NProspecting хранит detected в приватном поле, используем рефлексию
                try {
                    java.lang.reflect.Field detectedField = NProspecting.class.getDeclaredField("detected");
                    detectedField.setAccessible(true);
                    String detected = (String) detectedField.get(np);
                    
                    if (detected != null && !detected.isEmpty()) {
                        return detected;
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки рефлексии
                }
                
                // Альтернативный способ: читаем текст из виджетов окна
                String text = extractTextFromWindow(np);
                if (text != null) {
                    // Проверяем паттерн для найденных ресурсов
                    Matcher matcher = DETECT_PATTERN.matcher(text);
                    if (matcher.matches()) {
                        return matcher.group(1);
                    }
                    
                    // Проверяем паттерн для "ничего не найдено"
                    Matcher noMineralsMatcher = NO_MINERALS_PATTERN.matcher(text);
                    if (noMineralsMatcher.matches()) {
                        return "void";
                    }
                    
                    // Также проверяем через contains для надежности
                    if (text.toLowerCase().contains("no minerals found")) {
                        return "void";
                    }
                }
            }
            
            Thread.sleep(100);
        }
        
        return null;
    }

    /**
     * Извлекает текст из виджетов окна
     */
    private String extractTextFromWindow(Window window) {
        // Ищем текстовые виджеты в окне
        for (Widget w = window.lchild; w != null; w = w.prev) {
            if (w instanceof Label) {
                Label label = (Label) w;
                if (label.texts != null) {
                    String text = label.texts;
                    if (text != null && (text.contains("There appears to be") || 
                        text.contains("No minerals found"))) {
                        return text;
                    }
                }
            }
        }
        
        return null;
    }
}
