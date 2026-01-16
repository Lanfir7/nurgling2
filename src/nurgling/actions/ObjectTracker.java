package nurgling.actions;

import haven.Gob;
import haven.OCache;
import haven.Resource;
import haven.GobIcon;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.conf.NDiscordNotification;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.NMapView;
import haven.MCache;
import haven.Coord;
import haven.Coord2d;
import mapv4.MinimapImageGenerator;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import mapv4.MultipartUtility;
import java.io.InputStream;

/**
 * Класс для отслеживания объектов во время движения по маршруту
 * и отправки уведомлений в Discord при первом обнаружении объекта.
 */
public class ObjectTracker {
    // Используем Set для отслеживания найденных объектов по ID
    // Формат: "pattern:gobId" чтобы отслеживать уникальные комбинации
    private static final Set<String> foundObjects = new HashSet<>();
    // Множество объектов, которые были видны при старте (чтобы их игнорировать)
    private static final Set<Long> initiallyVisibleGobs = new HashSet<>();
    private final ArrayList<String> trackedPatterns;
    private final boolean discordNotifyEnabled;
    private final NGameUI gui;
    private static boolean initialized = false;

    public ObjectTracker(NGameUI gui, ArrayList<String> trackedPatterns, boolean discordNotifyEnabled) {
        this.gui = gui;
        this.trackedPatterns = trackedPatterns;
        this.discordNotifyEnabled = discordNotifyEnabled;
        
        // При первом создании помечаем все видимые объекты
        if (!initialized) {
            markInitiallyVisibleObjects();
            initialized = true;
        }
    }
    
    /**
     * Помечает все объекты, которые уже видны при старте бота
     */
    private void markInitiallyVisibleObjects() {
        if (NUtils.getGameUI() == null || NUtils.getGameUI().ui == null || 
            NUtils.getGameUI().ui.sess == null || NUtils.getGameUI().ui.sess.glob == null) {
            return;
        }
        
        synchronized (NUtils.getGameUI().ui.sess.glob.oc) {
            for (Gob gob : NUtils.getGameUI().ui.sess.glob.oc) {
                if (gob instanceof OCache.Virtual || gob.attr.isEmpty() || 
                    gob.getClass().getName().contains("GlobEffector")) {
                    continue;
                }
                
                if (gob.ngob == null || gob.ngob.name == null) {
                    continue;
                }
                
                String gobName = gob.ngob.name;
                
                // Проверяем каждый паттерн
                for (String pattern : trackedPatterns) {
                    boolean matches = false;
                    
                    if (pattern.contains(".*") || pattern.contains("^") || pattern.contains("$") || 
                        pattern.contains("[") || pattern.contains("(") || pattern.contains("+") || 
                        pattern.contains("?") || pattern.contains("|")) {
                        try {
                            Pattern regexPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                            matches = regexPattern.matcher(gobName).find();
                        } catch (Exception e) {
                            matches = NParser.checkName(gobName, new NAlias(pattern));
                        }
                    } else {
                        matches = NParser.checkName(gobName, new NAlias(pattern));
                    }
                    
                    if (matches) {
                        // Помечаем этот объект как уже виденный при старте
                        synchronized (initiallyVisibleGobs) {
                            initiallyVisibleGobs.add(gob.id);
                        }
                        // Также помечаем в foundObjects, чтобы не отправлять уведомление
                        String uniqueKey = pattern + ":" + gob.id;
                        synchronized (foundObjects) {
                            foundObjects.add(uniqueKey);
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * Проверяет объекты вокруг игрока и отправляет уведомления в Discord
     * при первом обнаружении объекта из списка отслеживания.
     */
    public void checkObjects() {
        if (!discordNotifyEnabled || trackedPatterns.isEmpty()) {
            return;
        }

        if (NUtils.getGameUI() == null || NUtils.getGameUI().ui == null || 
            NUtils.getGameUI().ui.sess == null || NUtils.getGameUI().ui.sess.glob == null) {
            return;
        }

        synchronized (NUtils.getGameUI().ui.sess.glob.oc) {
            for (Gob gob : NUtils.getGameUI().ui.sess.glob.oc) {
                if (gob instanceof OCache.Virtual || gob.attr.isEmpty() || 
                    gob.getClass().getName().contains("GlobEffector")) {
                    continue;
                }

                if (gob.ngob == null || gob.ngob.name == null) {
                    continue;
                }

                String gobName = gob.ngob.name;

                // Проверяем каждый паттерн из списка отслеживания
                for (String pattern : trackedPatterns) {
                    boolean matches = false;
                    
                    // Проверяем, является ли паттерн регулярным выражением (содержит .* или другие regex символы)
                    if (pattern.contains(".*") || pattern.contains("^") || pattern.contains("$") || 
                        pattern.contains("[") || pattern.contains("(") || pattern.contains("+") || 
                        pattern.contains("?") || pattern.contains("|")) {
                        // Используем регулярное выражение
                        try {
                            Pattern regexPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                            // Используем find() для частичного совпадения, а не matches() для полного
                            matches = regexPattern.matcher(gobName).find();
                        } catch (Exception e) {
                            // Если паттерн некорректный, пробуем через NAlias
                            matches = NParser.checkName(gobName, new NAlias(pattern));
                        }
                    } else {
                        // Используем NAlias для простых паттернов
                        matches = NParser.checkName(gobName, new NAlias(pattern));
                    }
                    
                    if (matches) {
                        // Используем gob.id для отслеживания уникальных объектов
                        String uniqueKey = pattern + ":" + gob.id;
                        
                        // Проверяем, не был ли этот объект уже найден
                        synchronized (foundObjects) {
                            if (foundObjects.contains(uniqueKey)) {
                                // Уже нашли этот объект ранее - пропускаем
                                break;
                            }
                            
                            // Проверяем, не был ли он виден при старте
                            synchronized (initiallyVisibleGobs) {
                                if (initiallyVisibleGobs.contains(gob.id)) {
                                    // Был виден при старте - пропускаем
                                    break;
                                }
                            }
                            
                            // Отмечаем как найденный
                            foundObjects.add(uniqueKey);
                        }
                        
                        // Объект найден впервые - отправляем уведомление
                        String readableName = getReadableName(gob);
                        String displayName = readableName != null ? readableName : gobName;
                        String message = "Found " + displayName;
                        if (gui != null) {
                            gui.msg("ObjectTracker: Found " + pattern + " - " + displayName);
                        }
                        sendDiscordNotificationWithMap(message, gob);
                        break; // Не проверяем другие паттерны для этого объекта
                    }
                }
            }
        }
    }

    /**
     * Получает читаемое имя объекта
     */
    private String getReadableName(Gob gob) {
        try {
            // Пробуем получить через GobIcon
            GobIcon icon = gob.getattr(GobIcon.class);
            if (icon != null && icon.icon != null && icon.icon.res != null) {
                try {
                    Resource.Tooltip tt = icon.icon.res.layer(Resource.tooltip);
                    if (tt != null && tt.t != null && !tt.t.isEmpty() && !tt.t.equals("???")) {
                        return tt.t;
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
            }
            
            // Пробуем получить через Drawable
            haven.Drawable drawable = gob.getattr(haven.Drawable.class);
            if (drawable != null) {
                try {
                    Resource res = drawable.getres();
                    if (res != null) {
                        Resource.Tooltip tt = res.layer(Resource.tooltip);
                        if (tt != null && tt.t != null && !tt.t.isEmpty()) {
                            return tt.t;
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        return null;
    }
    
    /**
     * Отправляет уведомление в Discord с картой
     */
    private void sendDiscordNotificationWithMap(String message, Gob gob) {
        if (gui == null || message == null || message.isEmpty()) {
            return;
        }
        
        NDiscordNotification discordSettings = NDiscordNotification.get("general");
        if (discordSettings == null || discordSettings.webhookUrl == null || 
            discordSettings.webhookUrl.isEmpty()) {
            return;
        }
        
        try {
            // Создаем скриншот карты с зеленой меткой
            BufferedImage mapImage = createMapScreenshotWithMarker(gob);
            
            if (mapImage != null) {
                // Отправляем через Discord webhook с файлом
                sendDiscordWithImage(discordSettings, message, mapImage);
            }
            // Если не удалось создать скриншот, не отправляем сообщение вообще
        } catch (Exception e) {
            // В случае ошибки не отправляем сообщение
        }
    }
    
    /**
     * Создает скриншот карты 3x3 grid'ов с зеленой меткой в месте находки объекта
     */
    private BufferedImage createMapScreenshotWithMarker(Gob gob) {
        try {
            if (gui == null || gui.map == null || !(gui.map instanceof NMapView)) {
                return null;
            }
            
            NMapView mapView = (NMapView) gui.map;
            if (mapView.glob == null || mapView.glob.map == null) {
                return null;
            }
            
            MCache map = mapView.glob.map;
            Coord2d gobPos = gob.rc;
            
            // Получаем центральный grid, в котором находится объект
            Coord tilec = gobPos.div(MCache.tilesz).floor();
            Coord centerGridCoord = tilec.div(MCache.cmaps);
            MCache.Grid centerGrid = map.getgridt(tilec);
            if (centerGrid == null) {
                return null;
            }
            
            // Сначала рендерим центральный grid, чтобы узнать реальный размер
            BufferedImage centerGridImage;
            try {
                centerGridImage = MinimapImageGenerator.drawmap(map, centerGrid);
            } catch (InterruptedException e) {
                return null;
            }
            if (centerGridImage == null || centerGridImage.getWidth() <= 0 || centerGridImage.getHeight() <= 0) {
                return null;
            }
            
            // Размер одного grid'а в пикселях (берем из реального изображения)
            int gridPixelSizeX = centerGridImage.getWidth();
            int gridPixelSizeY = centerGridImage.getHeight();
            int totalSizeX = gridPixelSizeX * 3;
            int totalSizeY = gridPixelSizeY * 3;
            
            // Создаем большое изображение для 3x3 grid'ов
            BufferedImage combinedImage = new BufferedImage(totalSizeX, totalSizeY, BufferedImage.TYPE_INT_ARGB);
            Graphics2D combinedG = combinedImage.createGraphics();
            combinedG.setColor(Color.BLACK);
            combinedG.fillRect(0, 0, totalSizeX, totalSizeY);
            
            // Рендерим каждый grid из 3x3 области
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    Coord gridCoord = centerGridCoord.add(dx, dy);
                    Coord gridTilec = gridCoord.mul(MCache.cmaps);
                    MCache.Grid grid = map.getgridt(gridTilec);
                    
                    if (grid != null) {
                        try {
                            BufferedImage gridImage = MinimapImageGenerator.drawmap(map, grid);
                            if (gridImage != null && gridImage.getWidth() > 0 && gridImage.getHeight() > 0) {
                                // Рисуем grid на комбинированном изображении
                                int x = (dx + 1) * gridPixelSizeX;
                                int y = (dy + 1) * gridPixelSizeY;
                                combinedG.drawImage(gridImage, x, y, gridPixelSizeX, gridPixelSizeY, null);
                            }
                        } catch (InterruptedException e) {
                            // Прервано - пропускаем этот grid
                        }
                    }
                }
            }
            combinedG.dispose();
            
            // Вычисляем позицию объекта на комбинированном изображении
            // Центральный grid находится в позиции (1, 1) в 3x3 массиве
            Coord2d centerGridUlWorld = centerGrid.ul.mul(MCache.tilesz).add(MCache.tilehsz);
            Coord2d localPos = gobPos.sub(centerGridUlWorld);
            
            // Позиция центрального grid'а на комбинированном изображении
            int centerGridX = gridPixelSizeX; // Позиция центрального grid'а (1 * gridPixelSizeX)
            int centerGridY = gridPixelSizeY;
            
            // Переводим локальные координаты в координаты на изображении
            double scaleX = (double)gridPixelSizeX / MCache.cmaps.x;
            double scaleY = (double)gridPixelSizeY / MCache.cmaps.y;
            
            int markerX = centerGridX + (int)(localPos.x / MCache.tilesz.x * scaleX);
            int markerY = centerGridY + (int)(localPos.y / MCache.tilesz.y * scaleY);
            
            // Ограничиваем координаты
            markerX = Math.max(5, Math.min(markerX, totalSizeX - 6));
            markerY = Math.max(5, Math.min(markerY, totalSizeY - 6));
            
            // Рисуем зеленую метку на карте
            Graphics2D g = combinedImage.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Рисуем зеленый круг
            int markerSize = 20;
            g.setColor(new Color(0, 255, 0, 200)); // Полупрозрачный зеленый
            g.fillOval(markerX - markerSize/2, markerY - markerSize/2, markerSize, markerSize);
            
            // Рисуем белую обводку
            g.setColor(Color.WHITE);
            g.setStroke(new java.awt.BasicStroke(3));
            g.drawOval(markerX - markerSize/2, markerY - markerSize/2, markerSize, markerSize);
            
            // Рисуем крестик в центре для лучшей видимости
            g.setStroke(new java.awt.BasicStroke(2));
            g.drawLine(markerX - 5, markerY, markerX + 5, markerY);
            g.drawLine(markerX, markerY - 5, markerX, markerY + 5);
            
            g.dispose();
            
            return combinedImage;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Отправляет сообщение в Discord с изображением через multipart/form-data
     */
    private void sendDiscordWithImage(NDiscordNotification settings, String message, BufferedImage image) {
        try {
            // Проверяем, что изображение не null и имеет размер
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                // Если нет изображения, не отправляем сообщение вообще
                return;
            }
            
            // Конвертируем изображение в JPEG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean written = ImageIO.write(image, "jpg", baos);
            if (!written) {
                // Если JPEG не поддерживается, пробуем PNG
                baos.reset();
                ImageIO.write(image, "png", baos);
            }
            byte[] imageBytes = baos.toByteArray();
            
            if (imageBytes.length == 0) {
                // Если изображение пустое, не отправляем сообщение
                return;
            }
            
            // Используем MultipartUtility для правильной отправки
            MultipartUtility multipart = new MultipartUtility(settings.webhookUrl, "UTF-8");
            
            // Формируем JSON payload
            StringBuilder jsonPayload = new StringBuilder();
            jsonPayload.append("{\"content\":\"").append(escapeJson(message)).append("\"");
            if (settings.webhookUsername != null && !settings.webhookUsername.isEmpty()) {
                jsonPayload.append(",\"username\":\"").append(escapeJson(settings.webhookUsername)).append("\"");
            }
            if (settings.webhookIcon != null && !settings.webhookIcon.isEmpty()) {
                jsonPayload.append(",\"avatar_url\":\"").append(escapeJson(settings.webhookIcon)).append("\"");
            }
            jsonPayload.append("}");
            
            // Добавляем JSON payload
            multipart.addFormField("payload_json", jsonPayload.toString());
            
            // Добавляем файл изображения
            InputStream imageStream = new java.io.ByteArrayInputStream(imageBytes);
            multipart.addFilePart("file", imageStream, "map.jpg");
            
            // Отправляем запрос
            try {
                multipart.finish();
                // Если ошибка, просто игнорируем - не отправляем сообщение без картинки
            } catch (Exception e) {
                // В случае ошибки не отправляем сообщение
            }
        } catch (Exception e) {
            // В случае ошибки не отправляем сообщение
        }
    }
    
    /**
     * Экранирует специальные символы для JSON
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    

    /**
     * Очищает список найденных объектов (для нового цикла отслеживания)
     */
    public void reset() {
        synchronized (foundObjects) {
            foundObjects.clear();
        }
        synchronized (initiallyVisibleGobs) {
            initiallyVisibleGobs.clear();
        }
        initialized = false;
    }
    
    /**
     * Очищает список найденных объектов для конкретного паттерна
     */
    public static void resetPattern(String pattern) {
        synchronized (foundObjects) {
            foundObjects.removeIf(key -> key.startsWith(pattern + ":"));
        }
    }
}
