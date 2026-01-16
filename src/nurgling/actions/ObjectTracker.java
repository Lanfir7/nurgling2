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
import javax.net.ssl.HttpsURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
                        String message = "Find " + displayName;
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
            } else {
                // Если не удалось создать скриншот, отправляем просто сообщение
                gui.msgToDiscord(discordSettings, message);
            }
        } catch (Exception e) {
            // В случае ошибки отправляем просто сообщение
            gui.msgToDiscord(discordSettings, message);
        }
    }
    
    /**
     * Создает скриншот карты с зеленой меткой в месте находки объекта
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
            
            // Получаем grid, в котором находится объект
            Coord tilec = gobPos.div(MCache.tilesz).floor();
            MCache.Grid grid = map.getgridt(tilec);
            if (grid == null) {
                return null;
            }
            
            // Рендерим карту используя MinimapImageGenerator
            BufferedImage mapImage = MinimapImageGenerator.drawmap(map, grid);
            if (mapImage == null) {
                return null;
            }
            
            // Вычисляем позицию объекта на карте
            // MCache.cmaps - это размер одного grid в тайлах (обычно 33x33)
            // gobPos - это координаты объекта в игровых координатах
            // grid.ul - это верхний левый угол grid в тайлах
            
            // Переводим координаты объекта в локальные координаты grid
            Coord2d gridUlWorld = grid.ul.mul(MCache.tilesz).add(MCache.tilehsz);
            Coord2d localPos = gobPos.sub(gridUlWorld);
            
            // Переводим в координаты на изображении карты
            // MCache.cmaps - размер grid в тайлах (33x33)
            // mapImage имеет размер MCache.cmaps в пикселях
            double scaleX = (double)mapImage.getWidth() / MCache.cmaps.x;
            double scaleY = (double)mapImage.getHeight() / MCache.cmaps.y;
            
            int markerX = (int)(localPos.x / MCache.tilesz.x * scaleX);
            int markerY = (int)(localPos.y / MCache.tilesz.y * scaleY);
            
            // Ограничиваем координаты
            markerX = Math.max(5, Math.min(markerX, mapImage.getWidth() - 6));
            markerY = Math.max(5, Math.min(markerY, mapImage.getHeight() - 6));
            
            // Рисуем зеленую метку на карте
            Graphics2D g = mapImage.createGraphics();
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
            
            return mapImage;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Отправляет сообщение в Discord с изображением через multipart/form-data
     */
    private void sendDiscordWithImage(NDiscordNotification settings, String message, BufferedImage image) {
        try {
            // Конвертируем изображение в JPEG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();
            
            // Создаем multipart/form-data запрос
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            URL url = URI.create(settings.webhookUrl).toURL();
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("User-Agent", "Java-DiscordWebhook");
            
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
            
            String jsonStr = jsonPayload.toString();
            byte[] jsonBytes = jsonStr.getBytes(StandardCharsets.UTF_8);
            
            // Формируем multipart данные
            String LINE_FEED = "\r\n";
            ByteArrayOutputStream multipartData = new ByteArrayOutputStream();
            
            // JSON payload часть
            multipartData.write(("--" + boundary + LINE_FEED).getBytes(StandardCharsets.UTF_8));
            multipartData.write("Content-Disposition: form-data; name=\"payload_json\"".getBytes(StandardCharsets.UTF_8));
            multipartData.write(LINE_FEED.getBytes(StandardCharsets.UTF_8));
            multipartData.write("Content-Type: application/json".getBytes(StandardCharsets.UTF_8));
            multipartData.write((LINE_FEED + LINE_FEED).getBytes(StandardCharsets.UTF_8));
            multipartData.write(jsonBytes);
            multipartData.write(LINE_FEED.getBytes(StandardCharsets.UTF_8));
            
            // Файл изображения часть
            multipartData.write(("--" + boundary + LINE_FEED).getBytes(StandardCharsets.UTF_8));
            multipartData.write("Content-Disposition: form-data; name=\"file\"; filename=\"map.jpg\"".getBytes(StandardCharsets.UTF_8));
            multipartData.write(LINE_FEED.getBytes(StandardCharsets.UTF_8));
            multipartData.write("Content-Type: image/jpeg".getBytes(StandardCharsets.UTF_8));
            multipartData.write((LINE_FEED + LINE_FEED).getBytes(StandardCharsets.UTF_8));
            multipartData.write(imageBytes);
            multipartData.write(LINE_FEED.getBytes(StandardCharsets.UTF_8));
            
            // Закрывающая граница
            multipartData.write(("--" + boundary + "--" + LINE_FEED).getBytes(StandardCharsets.UTF_8));
            
            byte[] multipartBytes = multipartData.toByteArray();
            connection.setFixedLengthStreamingMode(multipartBytes.length);
            
            try (java.io.OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(multipartBytes);
                outputStream.flush();
            }
            
            // Читаем ответ
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                // Успешно отправлено
            } else {
                // Ошибка - отправляем просто сообщение
                gui.msgToDiscord(settings, message);
            }
            
            connection.disconnect();
        } catch (Exception e) {
            // В случае ошибки отправляем просто сообщение
            if (gui != null) {
                gui.msgToDiscord(settings, message);
            }
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
