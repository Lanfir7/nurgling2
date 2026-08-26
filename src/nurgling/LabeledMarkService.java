package nurgling;

import haven.*;
import nurgling.profiles.ConfigFactory;
import nurgling.profiles.ProfileAwareService;
import nurgling.tools.ForageMarkerLogic;
import nurgling.tools.NFileUtils;
import nurgling.widgets.LabeledMinimapMark;
import nurgling.NGameUI;
import nurgling.db.dao.AnimalMarkerDao;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.awt.image.BufferedImage;

/**
 * Service for managing labeled minimap marks (water/soil quality marks from Checker bots).
 * Supports persistence and world-specific profiles via ProfileAwareService.
 *
 * Reads are lock-free: the render thread asks for a segment's marks every frame, so
 * {@link #segIndex} holds pre-grouped immutable lists that are swapped in on mutation.
 * Writes to disk are coalesced onto a background thread and never run while a lock is
 * held, so a long save cannot stall rendering.
 */
public class LabeledMarkService implements ProfileAwareService {
    private final Map<String, LabeledMinimapMark> labeledMarks = new ConcurrentHashMap<>();
    /** Кэш иконок животных по gobId: при добавлении локально (есть Gob) иконка сохраняется; при merge из БД берётся отсюда. */
    private final Map<Long, BufferedImage> animalIconCache = new ConcurrentHashMap<>();
    // Индексы для быстрого поиска маркеров
    private final Map<String, List<LabeledMinimapMark>> resourceTypeIndex = new ConcurrentHashMap<>();
    private final Map<Long, List<LabeledMinimapMark>> segmentIndex = new ConcurrentHashMap<>();
    /** Immutable per-segment view of {@link #labeledMarks}, replaced wholesale on change. */
    private volatile Map<Long, List<LabeledMinimapMark>> segIndex = Collections.emptyMap();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private String dataFile;
    private final NGameUI gui;
    private String genus;
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LabeledMarkService-Save");
        t.setDaemon(true);
        return t;
    });

    /* Background writer. saveQueued gates enqueuing so a burst of samples collapses
     * into a single write; it is cleared as the write starts, so a sample taken during
     * a write still queues the next one. */
    private final Object writeLock = new Object();
    /** Serializes the actual file writes so the background and shutdown writers cannot overlap. */
    private final Object fileLock = new Object();
    private Thread writer;
    private boolean saveQueued = false;
    private boolean shutdown = false;
    private boolean suppressReindex = false;
    
    // Очередь для неблокирующего добавления маркеров (устраняет лаги UI)
    private final ConcurrentLinkedQueue<PendingMark> pendingMarks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processingScheduled = new AtomicBoolean(false);
    private static final long PROCESS_DELAY_MS = 50; // Минимальная задержка для батчинга
    
    // Структура для хранения ожидающих маркеров
    private static class PendingMark {
        final String locationId;
        final String label;
        final String resourceType;
        final long segmentId;
        final Coord tileCoords;
        final long gridId;            // Grid ID for ChunkNav navigation (-1 if unknown)
        final Coord localTileCoords;  // Local tile coords within grid (null if unknown)
        final BufferedImage iconImage;
        final int radiusTiles;
        
        PendingMark(String locationId, String label, String resourceType, long segmentId, 
                   Coord tileCoords, long gridId, Coord localTileCoords,
                   BufferedImage iconImage, int radiusTiles) {
            this.locationId = locationId;
            this.label = label;
            this.resourceType = resourceType;
            this.segmentId = segmentId;
            this.tileCoords = tileCoords;
            this.gridId = gridId;
            this.localTileCoords = localTileCoords;
            this.iconImage = iconImage;
            this.radiusTiles = radiusTiles;
        }
    }

    public LabeledMarkService(NGameUI gui) {
        this.gui = gui;
        this.dataFile = NUtils.getDataFile("labeled_marks.nurgling.json");
        loadLabeledMarks();
    }

    /**
     * Constructor for profile-aware initialization
     */
    public LabeledMarkService(NGameUI gui, String genus) {
        this.gui = gui;
        this.genus = genus;
        initializeForProfile(genus);
    }

    // ProfileAwareService implementation

    @Override
    public void initializeForProfile(String genus) {
        this.genus = genus;
        NConfig config = ConfigFactory.getConfig(genus);
        this.dataFile = config.getLabeledMarksPath();
        load();
    }

    @Override
    public String getGenus() {
        return genus;
    }

    @Override
    public void load() {
        loadLabeledMarks();
    }

    @Override
    public void save() {
        writeSnapshot(snapshot());
    }

    /**
     * Add a labeled mark asynchronously and return locationId for icon update.
     * Используется для создания маркера без иконки, с последующей асинхронной загрузкой иконки.
     * НЕБЛОКИРУЮЩИЙ: добавляет маркер в очередь и обрабатывает в фоне.
     */
    public String addLabeledMarkAsync(String label, String resourceType, long segmentId, 
                                      Coord tileCoords, BufferedImage iconImage) {
        return addLabeledMarkAsync(label, resourceType, segmentId, tileCoords, iconImage, 2);
    }
    
    /**
     * Add a labeled mark asynchronously with custom radius for duplicate checking.
     * НЕБЛОКИРУЮЩИЙ: добавляет маркер в очередь и обрабатывает в фоне, не блокируя UI.
     * @param radiusTiles радиус проверки дубликатов в тайлах (0 = только точно на том же месте, 1 = в радиусе 1 тайла, 2 = в радиусе 2 тайлов)
     */
    public String addLabeledMarkAsync(String label, String resourceType, long segmentId, 
                                      Coord tileCoords, BufferedImage iconImage, int radiusTiles) {
        // Генерируем locationId заранее чтобы вернуть его сразу
        String locationId = resourceType + "_" + segmentId + "_" + tileCoords.x + "_" + tileCoords.y + "_" + System.currentTimeMillis();
        
        // Получаем gridId и localTileCoords для ChunkNav навигации
        long gridId = -1;
        Coord localTileCoords = null;
        try {
            if (gui != null && gui.map != null && gui.map.glob != null && gui.map.glob.map != null) {
                MCache mcache = gui.map.glob.map;
                // tileCoords - абсолютные координаты тайла в сегменте карты
                // Пытаемся получить grid для этих координат
                MCache.Grid grid = mcache.getgridt(tileCoords);
                if (grid != null) {
                    gridId = grid.id;
                    localTileCoords = tileCoords.sub(grid.ul);
                }
            }
        } catch (MCache.LoadingMap e) {
            // Grid еще не загружен - оставляем gridId = -1
        } catch (Exception e) {
            // Игнорируем другие ошибки
        }
        
        // Добавляем в очередь без блокировки (ConcurrentLinkedQueue lock-free)
        pendingMarks.offer(new PendingMark(locationId, label, resourceType, segmentId, tileCoords, gridId, localTileCoords, iconImage, radiusTiles));
        
        // Планируем обработку очереди
        scheduleProcessing();
        
        return locationId;
    }
    
    /**
     * Планирует асинхронную обработку очереди маркеров
     */
    private void scheduleProcessing() {
        if (processingScheduled.compareAndSet(false, true)) {
            saveExecutor.submit(() -> {
                try {
                    // Небольшая задержка для батчинга нескольких маркеров
                    Thread.sleep(PROCESS_DELAY_MS);
                    processPendingMarks();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    processingScheduled.set(false);
                    // Если есть ещё маркеры в очереди, планируем ещё раз
                    if (!pendingMarks.isEmpty()) {
                        scheduleProcessing();
                    }
                }
            });
        }
    }
    
    /**
     * Обрабатывает все ожидающие маркеры из очереди (вызывается в фоновом потоке)
     */
    private void processPendingMarks() {
        List<PendingMark> batch = new ArrayList<>();
        PendingMark pm;
        while ((pm = pendingMarks.poll()) != null) {
            batch.add(pm);
        }
        
        if (batch.isEmpty()) return;
        
        // Обрабатываем весь батч с одной блокировкой
        lock.writeLock().lock();
        try {
            suppressReindex = true;
            for (PendingMark mark : batch) {
                processMarkInternal(mark);
            }
            suppressReindex = false;
            reindex();
            // Сохраняем один раз для всего батча
            scheduleSave();
        } finally {
            suppressReindex = false;
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Внутренняя обработка одного маркера (вызывается внутри блокировки)
     */
    private void processMarkInternal(PendingMark pm) {
        // Получаем список маркеров этого типа ресурса из индекса
        List<LabeledMinimapMark> marksToCheck = resourceTypeIndex.getOrDefault(pm.resourceType, new ArrayList<>());
        
        // Ищем маркеры в том же сегменте в указанном радиусе
        List<String> toRemove = new ArrayList<>();
        int checkedCount = 0;
        int maxChecks = 100; // Уменьшено для производительности
        for (LabeledMinimapMark mark : marksToCheck) {
            if (checkedCount++ >= maxChecks) break;
            if (mark.segmentId == pm.segmentId && 
                mark.isNear(pm.segmentId, pm.tileCoords, pm.radiusTiles) && 
                pm.resourceType.equals(mark.resourceType)) {
                toRemove.add(mark.getLocationId());
            }
        }
        
        // Удаляем найденные маркеры
        for (String locationId : toRemove) {
            removeMarkFromIndexes(locationId);
        }
        
        // Создаем и добавляем новый маркер с gridId для ChunkNav навигации
        LabeledMinimapMark mark = new LabeledMinimapMark(pm.label, pm.resourceType, pm.segmentId, pm.tileCoords, 
                                                         pm.gridId, pm.localTileCoords, pm.iconImage, null);
        labeledMarks.put(mark.getLocationId(), mark);
        addMarkToIndexes(mark);
    }
    
    /**
     * Add a labeled mark (e.g., water or soil quality).
     * Removes any existing mark at the same location.
     * Оптимизировано с использованием индексов для быстрого поиска.
     */
    public void addLabeledMark(String label, String resourceType, double quality, long segmentId,
                               Coord tileCoords, BufferedImage iconImage) {
        lock.writeLock().lock();
        try {
            // Используем индекс для быстрого поиска маркеров того же типа ресурса
            // Вместо прохода по всем маркерам, ищем только в нужном сегменте и типе ресурса
            final Coord tc = tileCoords;
            final long segId = segmentId;
            final String resType = resourceType;
            
            // Получаем список маркеров этого типа ресурса из индекса
            List<LabeledMinimapMark> marksToCheck = resourceTypeIndex.getOrDefault(resType, new ArrayList<>());
            
            // Ищем маркеры в том же сегменте в радиусе 2 тайлов
            List<String> toRemove = new ArrayList<>();
            for (LabeledMinimapMark mark : marksToCheck) {
                if (mark.segmentId == segId && mark.isNear(segId, tc, 2) && resType.equals(mark.resourceType)) {
                    toRemove.add(mark.getLocationId());
                }
            }
            
            // Удаляем найденные маркеры
            for (String locationId : toRemove) {
                removeMarkFromIndexes(locationId);
            }
            
            // Create and add new mark
            LabeledMinimapMark mark = new LabeledMinimapMark(label, resourceType, quality, segmentId, tileCoords, iconImage);
            labeledMarks.put(mark.getLocationId(), mark);
            addMarkToIndexes(mark);
            
            // Сохраняем асинхронно, чтобы избежать пролога
            scheduleSave();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addForageMark(String label, String resourceType, long segmentId,
                             Coord tileCoords, BufferedImage iconImage) {
        if (resourceType == null || tileCoords == null) return;
        lock.writeLock().lock();
        try {
            List<LabeledMinimapMark> sameType =
                resourceTypeIndex.getOrDefault(resourceType, new ArrayList<LabeledMinimapMark>());
            List<ForageMarkerLogic.Neighbor> nearby = new ArrayList<ForageMarkerLogic.Neighbor>();
            for (LabeledMinimapMark mark : sameType) {
                if (!ForageMarkerLogic.isForageId(mark.getLocationId())) continue;
                if (mark.segmentId != segmentId) continue;
                if (!mark.isNear(segmentId, tileCoords, ForageMarkerLogic.DEDUP_RADIUS)) continue;
                nearby.add(new ForageMarkerLogic.Neighbor(
                    mark.getLocationId(), ForageMarkerLogic.parseQuality(mark.label)));
            }
            ForageMarkerLogic.Dedup dedup =
                ForageMarkerLogic.decideDedup(ForageMarkerLogic.parseQuality(label), nearby);
            if (dedup.skip) return;
            for (String id : dedup.removeIds) {
                removeMarkFromIndexes(id);
            }
            String locationId = ForageMarkerLogic.forageLocationId(
                segmentId, tileCoords.x, tileCoords.y, resourceType);
            LabeledMinimapMark mark = new LabeledMinimapMark(
                locationId, label, resourceType, segmentId, tileCoords, iconImage, null);
            labeledMarks.put(locationId, mark);
            addMarkToIndexes(mark);
            scheduleSave();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Добавляет маркер в индексы для быстрого поиска
     */
    private void addMarkToIndexes(LabeledMinimapMark mark) {
        // Индекс по типу ресурса
        resourceTypeIndex.computeIfAbsent(mark.resourceType, k -> new ArrayList<>()).add(mark);
        
        // Индекс по сегменту
        segmentIndex.computeIfAbsent(mark.segmentId, k -> new ArrayList<>()).add(mark);
        if (!suppressReindex) {
            reindex();
        }
    }
    
    /**
     * Удаляет маркер из индексов
     */
    private void removeMarkFromIndexes(String locationId) {
        LabeledMinimapMark mark = labeledMarks.get(locationId);
        if (mark == null) return;
        
        // Удаляем из индекса по типу ресурса
        List<LabeledMinimapMark> resourceList = resourceTypeIndex.get(mark.resourceType);
        if (resourceList != null) {
            resourceList.remove(mark);
            if (resourceList.isEmpty()) {
                resourceTypeIndex.remove(mark.resourceType);
            }
        }
        
        // Удаляем из индекса по сегменту
        List<LabeledMinimapMark> segmentList = segmentIndex.get(mark.segmentId);
        if (segmentList != null) {
            segmentList.remove(mark);
            if (segmentList.isEmpty()) {
                segmentIndex.remove(mark.segmentId);
            }
        }
        
        // Удаляем из основного хранилища
        labeledMarks.remove(locationId);
        if (!suppressReindex) {
            reindex();
        }
    }
    
    /**
     * Обновляет позицию и качество существующего маркера (для "плавающих" маркеров)
     * Перемещает маркер в новое место с лучшим качеством
     */
    public String updateMarkPosition(String locationId, String newLabel, Coord newTileCoords) {
        lock.writeLock().lock();
        try {
            LabeledMinimapMark oldMark = labeledMarks.get(locationId);
            if (oldMark == null) return null;
            
            // Создаем новый маркер с обновленными координатами и label, сохраняя старый locationId
            LabeledMinimapMark newMark = new LabeledMinimapMark(
                locationId, // Сохраняем старый locationId
                newLabel, 
                oldMark.resourceType, 
                oldMark.segmentId, 
                newTileCoords, 
                oldMark.iconImage,
                oldMark.labelColor
            );
            
            // Заменяем старый маркер на новый (locationId остается тот же)
            labeledMarks.put(locationId, newMark);
            
            // Обновляем индексы
            List<LabeledMinimapMark> resourceList = resourceTypeIndex.get(oldMark.resourceType);
            if (resourceList != null) {
                for (int i = 0; i < resourceList.size(); i++) {
                    if (resourceList.get(i).getLocationId().equals(locationId)) {
                        resourceList.set(i, newMark);
                        break;
                    }
                }
            }
            
            List<LabeledMinimapMark> segmentList = segmentIndex.get(oldMark.segmentId);
            if (segmentList != null) {
                for (int i = 0; i < segmentList.size(); i++) {
                    if (segmentList.get(i).getLocationId().equals(locationId)) {
                        segmentList.set(i, newMark);
                        break;
                    }
                }
            }
            
            // Сохраняем асинхронно
            scheduleSave();
            reindex();
            
            return locationId;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Обновляет иконку маркера (создает новый маркер с иконкой и заменяет старый)
     * Используется для асинхронной загрузки иконок
     */
    public void updateMarkIcon(String locationId, BufferedImage iconImage) {
        lock.writeLock().lock();
        try {
            LabeledMinimapMark oldMark = labeledMarks.get(locationId);
            if (oldMark == null) return;
            
            // Создаем новый маркер с иконкой
            LabeledMinimapMark newMark = new LabeledMinimapMark(
                oldMark.label, 
                oldMark.resourceType, 
                oldMark.segmentId, 
                oldMark.tileCoords, 
                iconImage,
                oldMark.labelColor
            );
            
            // Заменяем старый маркер на новый
            labeledMarks.put(locationId, newMark);
            
            // Обновляем индексы (ищем по locationId, так как equals может не работать)
            List<LabeledMinimapMark> resourceList = resourceTypeIndex.get(oldMark.resourceType);
            if (resourceList != null) {
                for (int i = 0; i < resourceList.size(); i++) {
                    if (resourceList.get(i).getLocationId().equals(locationId)) {
                        resourceList.set(i, newMark);
                        break;
                    }
                }
            }
            
            List<LabeledMinimapMark> segmentList = segmentIndex.get(oldMark.segmentId);
            if (segmentList != null) {
                for (int i = 0; i < segmentList.size(); i++) {
                    if (segmentList.get(i).getLocationId().equals(locationId)) {
                        segmentList.set(i, newMark);
                        break;
                    }
                }
            }
            
            // Сохраняем асинхронно
            scheduleSave();
            reindex();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Rebuild the per-segment render index. Called under the write lock.
     */
    private void reindex() {
        Map<Long, List<LabeledMinimapMark>> next = new HashMap<>();
        for (LabeledMinimapMark mark : labeledMarks.values()) {
            next.computeIfAbsent(mark.segmentId, k -> new ArrayList<>()).add(mark);
        }
        for (Map.Entry<Long, List<LabeledMinimapMark>> e : next.entrySet()) {
            e.setValue(Collections.unmodifiableList(e.getValue()));
        }
        segIndex = next;
    }

    /**
     * Take a consistent copy of the marks to serialize outside the lock.
     */
    private List<LabeledMinimapMark> snapshot() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(labeledMarks.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Serialize and write the given marks. Must not be called while holding a lock:
     * PNG encoding and file I/O here take long enough to stall the render thread.
     */
    private void writeSnapshot(List<LabeledMinimapMark> marks) {
        synchronized (fileLock) {
        try {
            JSONObject main = new JSONObject();
            JSONArray jMarks = new JSONArray();
            Set<String> types = new HashSet<>();
            for (LabeledMinimapMark mark : marks) {
                if (mark.getLocationId().startsWith("animal_")) continue;
                jMarks.put(mark.toJson());
                types.add(mark.resourceType);
            }
            /* One icon per resource type instead of one per mark: the old format
             * re-encoded every icon on every save, which grew with the sample count. */
            JSONObject icons = new JSONObject();
            for (String type : types) {
                String encoded = LabeledMinimapMark.iconBase64(type);
                if (encoded != null) {
                    icons.put(type, encoded);
                }
            }
            main.put("labeledMarks", jMarks);
            main.put("icons", icons);
            main.put("version", 2);
            main.put("lastSaved", java.time.Instant.now().toString());

            NFileUtils.writeAtomically(dataFile, main.toString());
        } catch (IOException e) {
            System.err.println("Failed to save labeled marks: " + e.getMessage());
        }
        }
    }

    /**
     * Request a save. Saves are coalesced and run on a background thread so that
     * sampling many spots in a row never blocks the game.
     */
    private void scheduleSave() {
        synchronized (writeLock) {
            if (shutdown || saveQueued) {
                return;
            }
            saveQueued = true;
            if (writer == null) {
                writer = new Thread(this::writeLoop, "labeled-marks-writer");
                writer.setDaemon(true);
                writer.start();
            }
            writeLock.notifyAll();
        }
    }

    /**
     * Drains save requests until the service is disposed. Interruption ends the
     * thread; it is a daemon and the final save happens in {@link #dispose()}.
     */
    private void writeLoop() {
        while (true) {
            synchronized (writeLock) {
                while (!saveQueued && !shutdown) {
                    try {
                        writeLock.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                if (shutdown) {
                    return;
                }
                saveQueued = false;
            }
            writeSnapshot(snapshot());
        }
    }

    /**
     * Add or update a single animal marker locally (e.g. when this client just found the animal).
     * Does not schedule file save (animal marks are not persisted to file).
     */
    public void addAnimalMarkerLocal(long gobId, String animalType, String displayName, long segmentId,
                                    int tileX, int tileY, Long gridId, Integer localTileX, Integer localTileY,
                                    BufferedImage icon) {
        String locationId = "animal_" + gobId;
        // Подпись только когда будет качество (q40); без качества — пусто
        String label = "";
        // Для подсказки на карте используем короткое имя (Fox), а не путь
        String resourceType = (displayName != null && !displayName.isEmpty()) ? displayName : ((animalType != null && !animalType.contains("/")) ? animalType : "Animal");
        Coord tileCoords = new Coord(tileX, tileY);
        long gid = gridId != null ? gridId : -1;
        Coord localTileCoords = (localTileX != null && localTileY != null) ? new Coord(localTileX, localTileY) : null;
        lock.writeLock().lock();
        try {
            removeMarkFromIndexes(locationId);
            if (icon != null) animalIconCache.put(gobId, icon);
            LabeledMinimapMark mark = new LabeledMinimapMark(locationId, label, resourceType, segmentId, tileCoords, gid, localTileCoords, icon, null);
            labeledMarks.put(locationId, mark);
            addMarkToIndexes(mark);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Merge animal markers from DB (Postgres) into the displayed marks.
     * Removes all existing animal_ marks and adds/updates from the list.
     * Does not schedule file save (animal marks are not persisted to file).
     */
    public void mergeAnimalMarkersFromDb(List<AnimalMarkerDao.AnimalMarkerData> fromDb, BufferedImage defaultIcon) {
        mergeAnimalMarkersFromDb(fromDb, defaultIcon, null);
    }

    /**
     * То же с передачей NGameUI для загрузки иконок через Icon Settings (iconconf) и NStyle.iconMap (mm/).
     */
    public void mergeAnimalMarkersFromDb(List<AnimalMarkerDao.AnimalMarkerData> fromDb, BufferedImage defaultIcon, NGameUI gui) {
        if (fromDb == null) return;
        lock.writeLock().lock();
        try {
            suppressReindex = true;
            List<String> toRemove = new ArrayList<>();
            for (String locationId : labeledMarks.keySet()) {
                if (locationId.startsWith("animal_")) toRemove.add(locationId);
            }
            for (String locationId : toRemove) {
                removeMarkFromIndexes(locationId);
            }
            for (AnimalMarkerDao.AnimalMarkerData data : fromDb) {
                String locationId = "animal_" + data.getGobId();
                // Подпись только при наличии качества: "q40", иначе пусто
                String label = data.getQuality() != null ? ("q" + (int) Math.round(data.getQuality())) : "";
                String displayName = data.getDisplayName() != null && !data.getDisplayName().isEmpty() ? data.getDisplayName() : (data.getAnimalType() != null && !data.getAnimalType().contains("/") ? data.getAnimalType() : "Animal");
                String resourceType = displayName; // подсказка — короткое имя
                Coord tileCoords = new Coord(data.getTileX(), data.getTileY());
                long gridId = data.getGridId() != null ? data.getGridId() : -1;
                Coord localTileCoords = (data.getLocalTileX() != null && data.getLocalTileY() != null) ? new Coord(data.getLocalTileX(), data.getLocalTileY()) : null;
                // Иконка: кэш → icon_path → iconconf → animal_type. Если не загрузилась, но есть iconPath/animalType — оставляем null,
                // чтобы при отрисовке сработала ленивая загрузка (Resource доступен на UI-потоке).
                BufferedImage icon = animalIconCache.get(data.getGobId());
                if (icon == null && data.getIconPath() != null && !data.getIconPath().isEmpty()) {
                    icon = nurgling.actions.ObjectTracker.loadIconFromResourcePath(data.getIconPath());
                    if (icon != null) animalIconCache.put(data.getGobId(), icon);
                }
                // Пробуем через Icon Settings (iconconf) — работает когда игра полностью загружена
                if (icon == null && data.getAnimalType() != null && data.getAnimalType().startsWith("gfx/kritter/") && gui != null) {
                    icon = nurgling.actions.ObjectTracker.loadIconFromIconConf(data.getAnimalType(), gui);
                    if (icon != null) animalIconCache.put(data.getGobId(), icon);
                }
                if (icon == null && data.getAnimalType() != null && data.getAnimalType().startsWith("gfx/kritter/")) {
                    icon = nurgling.actions.ObjectTracker.loadAnimalIconFromPath(data.getAnimalType(), data.getDisplayName(), gui);
                    if (icon != null) animalIconCache.put(data.getGobId(), icon);
                }
                boolean canLazyLoad = (data.getIconPath() != null && !data.getIconPath().isEmpty()) || (data.getAnimalType() != null && data.getAnimalType().startsWith("gfx/kritter/"));
                if (icon == null && !canLazyLoad) {
                    icon = defaultIcon;
                    if (icon == null) {
                        try { icon = Resource.loadsimg("gfx/invobjs/kritter"); } catch (Exception ignored) { }
                    }
                }
                Long killedAtMs = data.getKilledAt() != null ? data.getKilledAt().getTime() : null;
                String killedBy = data.getKilledBy();
                LabeledMinimapMark mark = new LabeledMinimapMark(locationId, label, resourceType, data.getSegmentId(), tileCoords, gridId, localTileCoords, icon, null, killedAtMs, killedBy, data.getIconPath(), data.getAnimalType());
                labeledMarks.put(locationId, mark);
                addMarkToIndexes(mark);
            }
            suppressReindex = false;
            reindex();
        } finally {
            suppressReindex = false;
            lock.writeLock().unlock();
        }
    }

    /**
     * Применяет маркеры животных с уже предзагруженными иконками.
     * Этот метод вызывается на UI-потоке, но не загружает иконки — они уже готовы.
     * Используется для устранения лагов UI при загрузке маркеров из БД.
     * 
     * @param preloadedMarkers список маркеров с предзагруженными иконками
     */
    public void mergeAnimalMarkersFromDbPreloaded(List<AnimalMarkerSyncService.PreloadedAnimalMarker> preloadedMarkers) {
        if (preloadedMarkers == null) return;
        lock.writeLock().lock();
        try {
            suppressReindex = true;
            // Удаляем старые маркеры животных
            List<String> toRemove = new ArrayList<>();
            for (String locationId : labeledMarks.keySet()) {
                if (locationId.startsWith("animal_")) toRemove.add(locationId);
            }
            for (String locationId : toRemove) {
                removeMarkFromIndexes(locationId);
            }
            
            // Добавляем новые маркеры с уже загруженными иконками
            for (AnimalMarkerSyncService.PreloadedAnimalMarker pm : preloadedMarkers) {
                LabeledMinimapMark mark = new LabeledMinimapMark(
                    pm.locationId, pm.label, pm.resourceType, pm.segmentId, pm.tileCoords,
                    pm.gridId, pm.localTileCoords, pm.icon, null, pm.killedAtMs, pm.killedBy, 
                    pm.iconPath, pm.animalType);
                labeledMarks.put(pm.locationId, mark);
                addMarkToIndexes(mark);
            }
            suppressReindex = false;
            reindex();
        } finally {
            suppressReindex = false;
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Получает иконку животного из кэша.
     * Используется AnimalMarkerSyncService для проверки кэша перед загрузкой.
     */
    public BufferedImage getAnimalIconFromCache(long gobId) {
        return animalIconCache.get(gobId);
    }
    
    /**
     * Добавляет иконку животного в кэш.
     * Используется AnimalMarkerSyncService после загрузки иконки в фоновом потоке.
     */
    public void cacheAnimalIcon(long gobId, BufferedImage icon) {
        if (icon != null) {
            animalIconCache.put(gobId, icon);
        }
    }

    /**
     * Обновляет иконку маркера животного (ленивая загрузка после перезахода).
     */
    public void updateAnimalMarkerIcon(String locationId, java.awt.image.BufferedImage icon) {
        if (locationId == null || !locationId.startsWith("animal_") || icon == null) return;
        lock.writeLock().lock();
        try {
            LabeledMinimapMark oldMark = labeledMarks.get(locationId);
            if (oldMark == null) return;
            LabeledMinimapMark newMark = new LabeledMinimapMark(
                locationId, oldMark.label, oldMark.resourceType, oldMark.segmentId, oldMark.tileCoords,
                oldMark.gridId, oldMark.localTileCoords, icon, oldMark.labelColor, oldMark.killedAtMs, oldMark.killedBy, null, null);
            labeledMarks.put(locationId, newMark);
            updateMarkInIndexes(oldMark, newMark);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void updateMarkInIndexes(LabeledMinimapMark oldMark, LabeledMinimapMark newMark) {
        List<LabeledMinimapMark> segmentList = segmentIndex.get(oldMark.segmentId);
        if (segmentList != null) {
            for (int i = 0; i < segmentList.size(); i++) {
                if (segmentList.get(i).getLocationId().equals(oldMark.getLocationId())) {
                    segmentList.set(i, newMark);
                    break;
                }
            }
        }
        List<LabeledMinimapMark> resourceList = resourceTypeIndex.get(oldMark.resourceType);
        if (resourceList != null) {
            for (int i = 0; i < resourceList.size(); i++) {
                if (resourceList.get(i).getLocationId().equals(oldMark.getLocationId())) {
                    resourceList.set(i, newMark);
                    break;
                }
            }
        }
        if (!suppressReindex) {
            reindex();
        }
    }

    /**
     * Обновляет подпись маркера животного (например, после инспекции туши — добавляем качество "Fox q72").
     * Не сохраняет в файл (маркеры животных только из БД).
     */
    public void updateAnimalMarkerLabel(String locationId, String newLabel) {
        if (locationId == null || !locationId.startsWith("animal_")) return;
        lock.writeLock().lock();
        try {
            LabeledMinimapMark oldMark = labeledMarks.get(locationId);
            if (oldMark == null) return;
            LabeledMinimapMark newMark = new LabeledMinimapMark(
                locationId, newLabel, oldMark.resourceType, oldMark.segmentId, oldMark.tileCoords,
                oldMark.gridId, oldMark.localTileCoords, oldMark.iconImage, oldMark.labelColor, oldMark.killedAtMs, oldMark.killedBy, oldMark.iconPath, oldMark.animalType);
            labeledMarks.put(locationId, newMark);
            updateMarkInIndexes(oldMark, newMark);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get a labeled mark by location ID.
     */
    public LabeledMinimapMark getMark(String locationId) {
        lock.readLock().lock();
        try {
            return labeledMarks.get(locationId);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get all labeled marks for a segment (for map rendering).
     * The returned list is immutable and safe to iterate without copying.
     */
    public List<LabeledMinimapMark> getMarksForSegment(long segmentId) {
        List<LabeledMinimapMark> marks = segIndex.get(segmentId);
        return (marks == null) ? Collections.emptyList() : marks;
    }

    /**
     * Get all labeled marks.
     */
    public Collection<LabeledMinimapMark> getAllMarks() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(labeledMarks.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Remove a labeled mark by location ID.
     */
    public boolean removeMark(String locationId) {
        lock.writeLock().lock();
        try {
            if (locationId != null && locationId.startsWith("animal_")) {
                try {
                    long gobId = Long.parseLong(locationId.substring("animal_".length()));
                    animalIconCache.remove(gobId);
                    // Удаляем из БД при любом удалении маркера животного (карта, окно поиска и т.д.)
                    if (gui != null) gui.deleteAnimalMarkerFromDb(gobId);
                } catch (NumberFormatException ignored) { }
            }
            boolean removed = labeledMarks.containsKey(locationId);
            if (removed) {
                removeMarkFromIndexes(locationId);
                scheduleSave(); // Сохраняем асинхронно
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove a labeled mark object.
     */
    public boolean removeMark(LabeledMinimapMark mark) {
        if (mark == null) return false;
        return removeMark(mark.getLocationId());
    }

    /**
     * Find a mark at given segment and tile coordinates.
     * Оптимизировано с использованием индекса по сегменту.
     */
    public LabeledMinimapMark findMarkAt(long segmentId, Coord tileCoords, int radiusTiles) {
        lock.readLock().lock();
        try {
            // Используем индекс по сегменту для быстрого поиска
            List<LabeledMinimapMark> segmentMarks = segmentIndex.get(segmentId);
            if (segmentMarks != null) {
                for (LabeledMinimapMark mark : segmentMarks) {
                    if (mark.isNear(segmentId, tileCoords, radiusTiles)) {
                        return mark;
                    }
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Найти маркер животного в данной точке (для обновления качества по позиции туши).
     */
    public LabeledMinimapMark findAnimalMarkerAt(long segmentId, Coord tileCoords, int radiusTiles) {
        lock.readLock().lock();
        try {
            List<LabeledMinimapMark> segmentMarks = segmentIndex.get(segmentId);
            if (segmentMarks != null) {
                for (LabeledMinimapMark mark : segmentMarks) {
                    if (mark.getLocationId() != null && mark.getLocationId().startsWith("animal_") && mark.isNear(segmentId, tileCoords, radiusTiles)) {
                        return mark;
                    }
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Load labeled marks from JSON.
     */
    private void loadLabeledMarks() {
        lock.writeLock().lock();
        try {
            labeledMarks.clear();
            resourceTypeIndex.clear();
            segmentIndex.clear();
            String content = NFileUtils.readWithBackupFallback(dataFile);
            if (content != null && !content.isEmpty()) {
                try {
                    JSONObject main = new JSONObject(content);
                    content = null; // free large string early
                    JSONObject icons = main.optJSONObject("icons");
                    if (icons != null) {
                        for (String type : icons.keySet()) {
                            LabeledMinimapMark.registerIcon(type,
                                LabeledMinimapMark.decodeIcon(icons.optString(type, null)));
                        }
                    }
                    JSONArray array = main.getJSONArray("labeledMarks");
                    int total = array.length();
                    System.out.println("LabeledMarks: loading " + total + " marks (lazy icons)...");
                    long t0 = System.currentTimeMillis();
                    suppressReindex = true;
                    for (int i = 0; i < total; i++) {
                        if (Thread.interrupted()) {
                            System.out.println("LabeledMarks: loading interrupted at " + i + "/" + total);
                            return;
                        }
                        try {
                            LabeledMinimapMark mark = new LabeledMinimapMark(array.getJSONObject(i));
                            labeledMarks.put(mark.getLocationId(), mark);
                            addMarkToIndexes(mark);
                        } catch (Exception e) {
                            System.err.println("Failed to parse labeled mark: " + e.getMessage());
                        }
                    }
                    suppressReindex = false;
                    System.out.println("LabeledMarks: loaded " + total + " marks in " + (System.currentTimeMillis() - t0) + "ms");
                } catch (Exception e) {
                    System.err.println("Failed to parse labeled marks JSON: " + e.getMessage());
                }
            }
            reindex();
        } finally {
            suppressReindex = false;
            lock.writeLock().unlock();
        }
    }

    /**
     * Get all labeled marks for a specific resource type.
     * Оптимизировано с использованием индекса - O(1) вместо O(n).
     */
    public List<LabeledMinimapMark> getMarksByResourceType(String resourceType) {
        lock.readLock().lock();
        try {
            // Используем индекс для мгновенного получения маркеров нужного типа
            List<LabeledMinimapMark> indexed = resourceTypeIndex.get(resourceType);
            if (indexed != null) {
                // Возвращаем копию списка, чтобы избежать проблем с concurrent модификациями
                return new ArrayList<>(indexed);
            }
            return new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Remove all marks for a specific resource type.
     * Оптимизировано с использованием индекса.
     */
    public int removeMarksByResourceType(String resourceType) {
        lock.writeLock().lock();
        try {
            // Используем индекс для быстрого получения всех маркеров нужного типа
            List<LabeledMinimapMark> marksToRemove = resourceTypeIndex.get(resourceType);
            if (marksToRemove == null || marksToRemove.isEmpty()) {
                return 0;
            }
            
            int removed = 0;
            // Создаем копию списка, чтобы избежать concurrent modification
            List<LabeledMinimapMark> copy = new ArrayList<>(marksToRemove);
            suppressReindex = true;
            for (LabeledMinimapMark mark : copy) {
                removeMarkFromIndexes(mark.getLocationId());
                removed++;
            }
            suppressReindex = false;
            reindex();
            
            if (removed > 0) {
                scheduleSave(); // Сохраняем асинхронно
            }
            return removed;
        } finally {
            suppressReindex = false;
            lock.writeLock().unlock();
        }
    }

    /**
     * Get marks filtered by quality threshold (for Quarryartz marks with "q" prefix in label).
     * Оптимизировано с использованием индекса.
     */
    public List<LabeledMinimapMark> getQuarryartzMarksAboveThreshold(double threshold) {
        lock.readLock().lock();
        try {
            List<LabeledMinimapMark> result = new ArrayList<>();
            // Используем индекс для быстрого получения только Quarryartz маркеров
            List<LabeledMinimapMark> quarryartzMarks = resourceTypeIndex.get("Quarryartz");
            if (quarryartzMarks != null) {
                for (LabeledMinimapMark mark : quarryartzMarks) {
                    // Parse quality from label (format: "q101", "q95", etc.)
                    try {
                        if (mark.label != null && mark.label.startsWith("q")) {
                            String qStr = mark.label.substring(1).trim();
                            double quality = Double.parseDouble(qStr);
                            if (quality > threshold) {
                                result.add(mark);
                            }
                        }
                    } catch (Exception e) {
                        // Ignore parsing errors
                    }
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Dispose the service and cleanup resources.
     */
    public void dispose() {
        try {
            saveExecutor.shutdown();
            if (!saveExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        synchronized (writeLock) {
            shutdown = true;
            writer = null;
            writeLock.notifyAll();
        }
        /* Blocks on fileLock until any in-flight background write finishes, so the
         * final state always lands last. */
        writeSnapshot(snapshot());
        lock.writeLock().lock();
        try {
            labeledMarks.clear();
            resourceTypeIndex.clear();
            segmentIndex.clear();
            segIndex = Collections.emptyMap();
        } finally {
            lock.writeLock().unlock();
        }
    }
}

