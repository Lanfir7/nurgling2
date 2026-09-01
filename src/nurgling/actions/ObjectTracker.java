package nurgling.actions;

import haven.Gob;
import haven.OCache;
import haven.Resource;
import haven.GobIcon;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.conf.NDiscordNotification;
import nurgling.tools.ClaimLand;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.tools.VSpec;
import nurgling.NMapView;
import nurgling.NCore;
import nurgling.db.service.AnimalMarkerService;
import haven.MCache;
import haven.Coord;
import haven.Coord2d;
import haven.MapFile;
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
    private boolean discordNotifyEnabled;
    private final NGameUI gui;
    private static boolean initialized = false;
    /** Фильтровать только kritter (животных) и пропускать трупы (knock). Для макроса Animal Markers — true. */
    private final boolean filterKritterOnly;

    public ObjectTracker(NGameUI gui, ArrayList<String> trackedPatterns, boolean discordNotifyEnabled) {
        this(gui, trackedPatterns, discordNotifyEnabled, true, false);
    }
    
    /** @param markInitiallyVisible если false (макрос «Маркеры животных») — не помечать уже видимые, ставить маркеры при первой встрече */
    public ObjectTracker(NGameUI gui, ArrayList<String> trackedPatterns, boolean discordNotifyEnabled, boolean markInitiallyVisible) {
        this(gui, trackedPatterns, discordNotifyEnabled, markInitiallyVisible, false);
    }
    
    /** 
     * @param markInitiallyVisible если false (макрос «Маркеры животных») — не помечать уже видимые
     * @param filterKritterOnly если true — фильтровать только kritter и пропускать трупы (для Animal Markers)
     */
    public ObjectTracker(NGameUI gui, ArrayList<String> trackedPatterns, boolean discordNotifyEnabled, boolean markInitiallyVisible, boolean filterKritterOnly) {
        this.gui = gui;
        this.trackedPatterns = trackedPatterns != null ? trackedPatterns : new ArrayList<>();
        this.discordNotifyEnabled = discordNotifyEnabled;
        this.filterKritterOnly = filterKritterOnly;
        
        if (!markInitiallyVisible || this.trackedPatterns.isEmpty()) return;
        // При первом создании помечаем все видимые объекты (чтобы не слать уведомления за уже видимое)
        if (!initialized) {
            if (gui != null) {
                gui.msg("ObjectTracker: Marking initially visible objects...");
            }
            markInitiallyVisibleObjects();
            initialized = true;
            if (gui != null) {
                gui.msg("ObjectTracker: Marked " + initiallyVisibleGobs.size() + " initially visible objects");
            }
        }
    }
    
    /**
     * Обновляет настройки отслеживания
     */
    public void updateSettings(ArrayList<String> trackedPatterns, boolean discordNotifyEnabled) {
        this.trackedPatterns.clear();
        if (trackedPatterns != null) {
            this.trackedPatterns.addAll(trackedPatterns);
        }
        this.discordNotifyEnabled = discordNotifyEnabled;
    }
    
    /**
     * Проверяет, является ли объект трупом (поза knocked/dead)
     */
    private static boolean isKnocked(Gob gob) {
        if (gob == null) return false;
        try {
            String pose = gob.pose();
            if (pose != null) {
                return pose.contains("knock") || pose.contains("dead");
            }
        } catch (Exception ignored) {}
        return false;
    }
    
    /**
     * Помечает все объекты, которые уже видны при старте бота
     */
    private void markInitiallyVisibleObjects() {
        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null) {
            return;
        }
        
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
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
        if (trackedPatterns == null || trackedPatterns.isEmpty()) {
            return; // Не логируем, чтобы не спамить
        }

        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null) {
            return; // Не логируем, чтобы не спамить
        }

        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                if (gob instanceof OCache.Virtual || gob.attr.isEmpty() || 
                    gob.getClass().getName().contains("GlobEffector")) {
                    continue;
                }

                if (gob.ngob == null || gob.ngob.name == null) {
                    continue;
                }

                String gobName = gob.ngob.name;
                
                // Фильтр: только kritter (животные) — только для Animal Markers
                if (filterKritterOnly) {
                    if (!gobName.contains("gfx/kritter/")) {
                        continue;
                    }
                    // Фильтр: пропускаем трупы (поза knock)
                    if (isKnocked(gob)) {
                        continue;
                    }
                }

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
                                // Уже нашли этот объект ранее - пропускаем (без логирования, чтобы не спамить)
                                break;
                            }
                            
                            // Проверяем, не был ли он виден при старте
                            synchronized (initiallyVisibleGobs) {
                                if (initiallyVisibleGobs.contains(gob.id)) {
                                    // Был виден при старте - пропускаем
                                    if (gui != null) {
                                        gui.msg("ObjectTracker: Object was visible at start: " + gobName + " (id: " + gob.id + ")");
                                    }
                                    break;
                                }
                            }
                            
                            // Отмечаем как найденный ПЕРЕД отправкой, чтобы избежать дублирования
                            foundObjects.add(uniqueKey);
                            
                            // Объект найден впервые - отправляем уведомление в отдельном потоке
                            String readableName = getReadableName(gob);
                            String displayName = readableName != null ? readableName : gobName;
                            // Save animal marker to Postgres (for shared map markers and quality updates)
                            saveAnimalMarkerToDb(gob, displayName, gobName);
                            
                            // Уведомления (в игре и Discord) — только если включено; макрос «Маркеры животных» без уведомлений
                            if (discordNotifyEnabled) {
                                if (gui != null) {
                                    gui.msg("ObjectTracker: NEW OBJECT FOUND! " + pattern + " - " + displayName + " (id: " + gob.id + ")");
                                }
                                String message = "Found " + displayName;
                                final Gob gobForNotification = gob;
                                final String finalMessage = message;
                                new Thread(() -> {
                                    sendDiscordNotificationWithMap(finalMessage, gobForNotification);
                                }, "ObjectTracker-DiscordNotification").start();
                            }
                        }
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

    private static final int MARKER_RETRY_DELAY_MS = 6000;
    private static final int MARKER_MAX_RETRIES = 2;

    /**
     * Saves animal marker to Postgres and adds it locally so the finder sees it immediately.
     * Skipped while the player stands on a personal or village claim; wilderness is unchanged.
     * Runs in bot thread (when gob is valid), adds mark locally and DB in background.
     * On map loading (haven.Loading) schedules one delayed retry so marker is placed after map is ready.
     */
    private void saveAnimalMarkerToDb(Gob gob, String displayName, String animalType) {
        saveAnimalMarkerToDb(gob, displayName, animalType, 0);
    }

    private void saveAnimalMarkerToDb(Gob gob, String displayName, String animalType, int attempt) {
        if (!ClaimLand.shouldPlaceAnimalMarker(ClaimLand.isOnClaimOrVillage(NUtils.player()))) {
            return;
        }
        if (gui == null || gui.labeledMarkService == null) {
            return;
        }
        AnimalMarkerService animalMarkerService = NCore.databaseManager != null ? NCore.databaseManager.getAnimalMarkerService() : null;
        final boolean dbAvailable = animalMarkerService != null && animalMarkerService.isAvailable();
        final String profile = gui.getGenus();
        if (profile == null || profile.isEmpty()) {
            return;
        }
        final long fGobId = gob.id;
        final String fAnimalType = animalType != null ? animalType : "";
        final String fDisplayName = displayName != null ? displayName : fAnimalType;
        try {
            if (gui.map == null || gui.map.glob == null || gui.map.glob.map == null ||
                gui.mapfile == null || gui.mapfile.file == null) {
                if (attempt < MARKER_MAX_RETRIES) {
                    scheduleMarkerRetry(gob, displayName, animalType, attempt);
                } else {
                    gui.msg("ObjectTracker: Map not ready for marker");
                }
                return;
            }
            Coord tc = gob.rc.floor(MCache.tilesz);
            MCache.Grid obg = gui.map.glob.map.getgrid(tc.div(MCache.cmaps));
            MapFile.GridInfo info = gui.mapfile.file.gridinfo.get(obg.id);
            long segmentId;
            int tileX;
            int tileY;
            if (info != null) {
                Coord sc = tc.add(info.sc.sub(obg.gc).mul(MCache.cmaps));
                segmentId = info.seg;
                tileX = sc.x;
                tileY = sc.y;
            } else {
                segmentId = gui.mapfile.playerSegmentId();
                if (segmentId == 0) {
                    gui.msg("ObjectTracker: No segment for marker");
                    return;
                }
                tileX = tc.x;
                tileY = tc.y;
            }
            long gridId = obg.id;
            int localTileX = tc.x - obg.gc.x * MCache.cmaps.x;
            int localTileY = tc.y - obg.gc.y * MCache.cmaps.y;
            BufferedImage icon = loadAnimalIcon(gob);
            if (icon == null) {
                try {
                    icon = Resource.loadsimg("gfx/invobjs/kritter");
                } catch (Exception ignored) { }
            }
            gui.labeledMarkService.addAnimalMarkerLocal(fGobId, fAnimalType, fDisplayName,
                segmentId, tileX, tileY, gridId, localTileX, localTileY, icon);
            gui.msg("ObjectTracker: Animal marker placed at segment " + segmentId);
            if (dbAvailable) {
                final long fSegmentId = segmentId;
                final int fTileX = tileX;
                final int fTileY = tileY;
                final Long fGridId = gridId;
                final Integer fLocalTileX = localTileX;
                final Integer fLocalTileY = localTileY;
                final String fProfile = profile;
                final String iconPath = getAnimalIconPath(fAnimalType, fDisplayName);
                try {
                    gui.getAnimalMarkerWorker().submit(() -> {
                        animalMarkerService.insert(fProfile, fGobId, fAnimalType, fDisplayName, iconPath,
                            fSegmentId, fTileX, fTileY, fGridId, fLocalTileX, fLocalTileY);
                    });
                } catch (Exception e) {
                    if (gui != null) gui.msg("ObjectTracker: не удалось поставить сохранение маркера в очередь: " + e.getMessage());
                }
            }
        } catch (haven.Loading e) {
            if (attempt < MARKER_MAX_RETRIES) {
                if (gui != null) {
                    gui.msg("ObjectTracker: Map loading, retrying marker in " + (MARKER_RETRY_DELAY_MS / 1000) + "s");
                }
                scheduleMarkerRetry(gob, displayName, animalType, attempt);
            } else {
                gui.msg("ObjectTracker: Map loading, marker skipped");
            }
        } catch (Exception e) {
            if (gui != null) {
                gui.msg("ObjectTracker: Failed to save animal marker: " + e.getMessage());
            }
        }
    }

    /** Пытается загрузить иконку животного из gob (GobIcon/Drawable или путь invobjs). */
    private static BufferedImage loadAnimalIcon(Gob gob) {
        if (gob == null) return null;
        try {
            GobIcon gicon = gob.getattr(GobIcon.class);
            if (gicon != null && gicon.icon != null && gicon.icon.res != null) {
                Resource.Image img = gicon.icon.res.layer(Resource.imgc);
                if (img != null && img.img != null) return img.img;
            }
            haven.Drawable d = gob.getattr(haven.Drawable.class);
            if (d != null) {
                Resource res = d.getres();
                if (res != null) {
                    Resource.Image img = res.layer(Resource.imgc);
                    if (img != null && img.img != null) return img.img;
                }
            }
            String name = gob.ngob != null ? gob.ngob.name : null;
            if (name != null) return loadAnimalIconFromPath(name);
        } catch (Exception ignored) { }
        return null;
    }

    /** Загружает иконку животного по пути ресурса (для маркеров из БД, когда gob нет). Сначала ВСПЕК (boar -> wildboar и т.д.), затем по display name. */
    public static BufferedImage loadAnimalIconFromPath(String animalTypePath) {
        return loadAnimalIconFromPath(animalTypePath, null);
    }

    /** С display name и опционально UI (параметр не используется, оставлен для совместимости). */
    public static BufferedImage loadAnimalIconFromPath(String animalTypePath, String displayName) {
        return loadAnimalIconFromPath(animalTypePath, displayName, null);
    }

    /** По пути gfx/kritter/... загружает иконку из gfx/invobjs/kritter/... (ВСПЕК boar->wildboar, суффикс, display name).
     *  НЕ возвращает fallback-иконку — для поддержки lazy-loading при перезаходе. */
    public static BufferedImage loadAnimalIconFromPath(String animalTypePath, String displayName, NGameUI gui) {
        if (animalTypePath == null || !animalTypePath.startsWith("gfx/kritter/")) return null;
        BufferedImage img = null;
        // 0) ВСПЕК: boar -> wildboar, sheep -> mouflon и т.д. (иначе иконки не находятся)
        if (VSpec.kritterToInvobjsIcon != null) {
            String mapped = VSpec.getKritterIconPath(animalTypePath);
            if (mapped != null) {
                img = loadIconFromResourcePath(mapped);
                if (img != null) return img;
            }
        }
        // 1) По суффиксу пути: gfx/kritter/boar/boar -> gfx/invobjs/kritter/boar/boar и варианты
        String suffix = animalTypePath.substring("gfx/kritter/".length());
        String[] pathsToTry = {
            "gfx/invobjs/kritter/" + suffix,
            "gfx/invobjs/kritter/" + suffix.replaceFirst("/[^/]+$", ""),
        };
        for (String invPath : pathsToTry) {
            if (invPath.equals("gfx/invobjs/kritter/")) continue;
            try {
                img = loadIconFromResourcePath(invPath);
                if (img != null) return img;
            } catch (Exception ignored) { }
        }
        // 2) По display name: "Wild Boar" -> gfx/invobjs/kritter/wildboar
        if (displayName != null && !displayName.isEmpty()) {
            String normalized = displayName.toLowerCase().trim().replaceAll("\\s+", "");
            if (!normalized.isEmpty()) {
                img = loadIconFromResourcePath("gfx/invobjs/kritter/" + normalized);
                if (img != null) return img;
            }
        }
        // НЕ возвращаем fallback gfx/invobjs/kritter — это позволяет lazy-loading работать
        // Fallback применяется только в loadAnimalIconForGob (локальный маркер при обнаружении)
        return null;
    }

    /**
     * Возвращает путь ресурса иконки, который успешно загружается (для сохранения в БД и загрузки после перезахода).
     * Порядок попыток тот же, что в loadAnimalIconFromPath. Если ни один путь не загрузился — null.
     */
    public static String getAnimalIconPath(String animalTypePath, String displayName) {
        if (animalTypePath == null || !animalTypePath.startsWith("gfx/kritter/")) return null;
        String path = null;
        if (VSpec.kritterToInvobjsIcon != null) {
            String mapped = VSpec.getKritterIconPath(animalTypePath);
            if (mapped != null && loadIconFromResourcePath(mapped) != null) return mapped;
        }
        String suffix = animalTypePath.substring("gfx/kritter/".length());
        String[] pathsToTry = {
            "gfx/invobjs/kritter/" + suffix,
            "gfx/invobjs/kritter/" + suffix.replaceFirst("/[^/]+$", ""),
        };
        for (String invPath : pathsToTry) {
            if (invPath.equals("gfx/invobjs/kritter/")) continue;
            if (loadIconFromResourcePath(invPath) != null) return invPath;
        }
        if (displayName != null && !displayName.isEmpty()) {
            String normalized = displayName.toLowerCase().trim().replaceAll("\\s+", "");
            if (!normalized.isEmpty()) {
                path = "gfx/invobjs/kritter/" + normalized;
                if (loadIconFromResourcePath(path) != null) return path;
            }
        }
        path = "gfx/invobjs/kritter";
        if (loadIconFromResourcePath(path) != null) return path;
        return null;
    }

    /** Публичный загрузчик иконки по пути ресурса (для LabeledMarkService при загрузке маркеров из БД по icon_path). */
    public static BufferedImage loadIconFromResourcePath(String path) {
        try {
            BufferedImage direct = Resource.loadsimg(path);
            if (direct != null) return direct;
        } catch (Exception ignored) { }
        try {
            Resource res = Resource.remote().loadwait(path);
            if (res != null) {
                Resource.Image img = res.layer(Resource.imgc);
                if (img != null && img.img != null) return img.img;
            }
        } catch (Exception ignored) { }
        return null;
    }

    /**
     * Загружает иконку животного через Icon Settings (iconconf) — всегда работает после загрузки игры.
     * Формат ключей в iconconf: gfx/kritter/{animal}/icon (например gfx/kritter/fox/icon)
     * @param animalType путь ресурса животного (gfx/kritter/fox/fox)
     * @param gui NGameUI для доступа к iconconf
     * @return BufferedImage иконки или null если не найдена
     */
    public static BufferedImage loadIconFromIconConf(String animalType, NGameUI gui) {
        if (animalType == null || gui == null || gui.iconconf == null) {
            return null;
        }
        try {
            // Извлекаем короткое имя животного: gfx/kritter/fox/fox -> fox, gfx/kritter/mammoth/mammoth -> mammoth
            String shortName = null;
            if (animalType.startsWith("gfx/kritter/")) {
                String suffix = animalType.substring("gfx/kritter/".length()); // fox/fox или mammoth/mammoth
                if (suffix.contains("/")) {
                    shortName = suffix.substring(0, suffix.indexOf("/")); // fox, mammoth
                } else {
                    shortName = suffix;
                }
            }
            
            if (shortName == null) return null;
            
            // Формируем ожидаемый ключ: gfx/kritter/fox/icon
            String expectedKey = "gfx/kritter/" + shortName + "/icon";
            
            // 1) Точное совпадение gfx/kritter/{name}/icon
            for (GobIcon.Setting setting : gui.iconconf.settings.values()) {
                if (setting.id != null && setting.id.res != null) {
                    if (expectedKey.equals(setting.id.res)) {
                        if (setting.icon != null) {
                            return setting.icon.image();
                        }
                    }
                }
            }
            
            // 2) Частичное совпадение: ищем ключи содержащие shortName
            String kritterPrefix = "gfx/kritter/" + shortName;
            for (GobIcon.Setting setting : gui.iconconf.settings.values()) {
                if (setting.id != null && setting.id.res != null) {
                    // Ключ начинается с gfx/kritter/{name}
                    if (setting.id.res.startsWith(kritterPrefix)) {
                        if (setting.icon != null) {
                            return setting.icon.image();
                        }
                    }
                }
            }
        } catch (Exception e) { 
            // Игнорируем
        }
        return null;
    }

    private void scheduleMarkerRetry(Gob gob, String displayName, String animalType, int attempt) {
        final NGameUI g = gui;
        if (g == null) return;
        new Thread(() -> {
            try {
                Thread.sleep(MARKER_RETRY_DELAY_MS);
                if (g.labeledMarkService == null) return;
                saveAnimalMarkerToDb(gob, displayName, animalType, attempt + 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "ObjectTracker-MarkerRetry").start();
    }
    
    /**
     * Отправляет уведомление в Discord с картой
     */
    private void sendDiscordNotificationWithMap(String message, Gob gob) {
        if (gui == null || message == null || message.isEmpty()) {
            if (gui != null) {
                gui.msg("ObjectTracker: Cannot send - gui is null or message is empty");
            }
            return;
        }
        
        NDiscordNotification discordSettings = NDiscordNotification.get("general");
        if (discordSettings == null) {
            if (gui != null) {
                gui.msg("ObjectTracker: Discord settings not found (general)");
            }
            return;
        }
        
        if (discordSettings.webhookUrl == null || discordSettings.webhookUrl.isEmpty()) {
            if (gui != null) {
                gui.msg("ObjectTracker: Discord webhook URL is empty");
            }
            return;
        }
        
        if (gui != null) {
            gui.msg("ObjectTracker: Sending Discord notification: " + message);
        }
        
        try {
            // Пытаемся создать скриншот карты с зеленой меткой
            // Делаем несколько попыток, так как карта может быть еще не загружена
            BufferedImage mapImage = null;
            int attempts = 0;
            int maxAttempts = 5; // Увеличиваем количество попыток
            
            while (mapImage == null && attempts < maxAttempts) {
                try {
                    mapImage = createMapScreenshotWithMarker(gob);
                    if (mapImage == null && attempts < maxAttempts - 1) {
                        // Ждем немного перед следующей попыткой
                        Thread.sleep(1000); // Увеличиваем задержку
                    }
                } catch (InterruptedException e) {
                    // Карта еще не загружена - ждем и пробуем снова
                    if (attempts < maxAttempts - 1) {
                        Thread.sleep(2000); // Увеличиваем задержку при ошибке
                    }
                }
                attempts++;
            }
            
            if (mapImage != null) {
                if (gui != null) {
                    gui.msg("ObjectTracker: Map image created, size: " + mapImage.getWidth() + "x" + mapImage.getHeight());
                }
                // Отправляем через Discord webhook с файлом
                sendDiscordWithImage(discordSettings, message, mapImage);
            } else {
                // Если не удалось создать карту, отправляем просто сообщение без карты
                if (gui != null) {
                    gui.msg("ObjectTracker: Map not ready, sending notification without map");
                    try {
                        gui.msgToDiscord(discordSettings, message);
                        gui.msg("ObjectTracker: Notification sent without map");
                    } catch (Exception e) {
                        gui.msg("ObjectTracker: ERROR sending notification without map: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            // В случае ошибки отправляем просто сообщение
            if (gui != null) {
                gui.msg("ObjectTracker: ERROR sending notification: " + e.getMessage() + ", sending without map");
                gui.msgToDiscord(discordSettings, message);
            }
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
            // Делаем несколько попыток, так как карта может быть еще не загружена
            BufferedImage centerGridImage = null;
            int attempts = 0;
            int maxAttempts = 3;
            
            while (centerGridImage == null && attempts < maxAttempts) {
                try {
                    centerGridImage = MinimapImageGenerator.drawmap(map, centerGrid);
                    if (centerGridImage == null && attempts < maxAttempts - 1) {
                        Thread.sleep(500);
                    }
                } catch (InterruptedException e) {
                    // Карта еще не загружена - ждем и пробуем снова
                    if (attempts < maxAttempts - 1) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                    }
                }
                attempts++;
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
            if (gui != null) {
                gui.msg("ObjectTracker: ERROR creating map screenshot: " + e.getMessage());
                e.printStackTrace();
            }
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
                if (gui != null) {
                    gui.msg("ObjectTracker: ERROR - Image bytes are empty!");
                }
                return;
            }
            
            if (gui != null) {
                gui.msg("ObjectTracker: Image converted, size: " + imageBytes.length + " bytes");
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
                Object response = multipart.finish();
                // Проверяем статус через рефлексию
                try {
                    java.lang.reflect.Field statusCodeField = response.getClass().getField("statusCode");
                    statusCodeField.setAccessible(true); // Разрешаем доступ к полю
                    int statusCode = statusCodeField.getInt(response);
                    
                    if (statusCode >= 200 && statusCode < 300) {
                        if (gui != null) {
                            gui.msg("ObjectTracker: Discord notification sent successfully! (status: " + statusCode + ")");
                        }
                    } else {
                        if (gui != null) {
                            gui.msg("ObjectTracker: ERROR - Discord returned status code: " + statusCode);
                        }
                    }
                } catch (Exception e) {
                    // Если не удалось проверить статус, но finish() не выбросил исключение, считаем успешным
                    if (gui != null) {
                        gui.msg("ObjectTracker: Discord notification sent (status check failed, but no exception)");
                    }
                }
            } catch (Exception e) {
                // В случае ошибки логируем
                if (gui != null) {
                    gui.msg("ObjectTracker: ERROR sending to Discord: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            // В случае ошибки логируем
            if (gui != null) {
                gui.msg("ObjectTracker: ERROR preparing Discord message: " + e.getMessage());
                e.printStackTrace();
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
