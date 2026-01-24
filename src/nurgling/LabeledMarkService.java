package nurgling;

import haven.*;
import nurgling.profiles.ConfigFactory;
import nurgling.profiles.ProfileAwareService;
import nurgling.widgets.LabeledMinimapMark;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;
import java.awt.image.BufferedImage;

/**
 * Service for managing labeled minimap marks (water/soil quality marks from Checker bots).
 * Supports persistence and world-specific profiles via ProfileAwareService.
 */
public class LabeledMarkService implements ProfileAwareService {
    private final Map<String, LabeledMinimapMark> labeledMarks = new ConcurrentHashMap<>();
    // Индексы для быстрого поиска маркеров
    private final Map<String, List<LabeledMinimapMark>> resourceTypeIndex = new ConcurrentHashMap<>();
    private final Map<Long, List<LabeledMinimapMark>> segmentIndex = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private String dataFile;
    private final NGameUI gui;
    private String genus;
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LabeledMarkService-Save");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean saveScheduled = false;
    
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
        lock.writeLock().lock();
        try {
            saveLabeledMarks();
        } finally {
            lock.writeLock().unlock();
        }
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
            for (PendingMark mark : batch) {
                processMarkInternal(mark);
            }
            // Сохраняем один раз для всего батча
            scheduleSave();
        } finally {
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
    public void addLabeledMark(String label, String resourceType, long segmentId, 
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
            LabeledMinimapMark mark = new LabeledMinimapMark(label, resourceType, segmentId, tileCoords, iconImage);
            labeledMarks.put(mark.getLocationId(), mark);
            addMarkToIndexes(mark);
            
            // Сохраняем асинхронно, чтобы избежать пролога
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
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Планирует асинхронное сохранение (чтобы избежать пролога при установке маркера)
     * Оптимизировано: использует readLock для быстрого снимка, затем пишет без блокировки
     */
    private void scheduleSave() {
        if (!saveScheduled) {
            saveScheduled = true;
            saveExecutor.submit(() -> {
                try {
                    Thread.sleep(500); // Увеличенная задержка для лучшего батчинга
                    saveLabeledMarksOptimized();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // Игнорируем ошибки сохранения
                } finally {
                    saveScheduled = false;
                }
            });
        }
    }
    
    /**
     * Оптимизированное сохранение: делает быстрый снимок с readLock, затем пишет без блокировки
     */
    private void saveLabeledMarksOptimized() {
        // Быстро копируем данные с readLock (не блокирует write операции)
        List<LabeledMinimapMark> snapshot;
        lock.readLock().lock();
        try {
            snapshot = new ArrayList<>(labeledMarks.values());
        } finally {
            lock.readLock().unlock();
        }
        
        // Записываем в файл БЕЗ блокировки
        try {
            JSONObject main = new JSONObject();
            JSONArray jMarks = new JSONArray();
            for (LabeledMinimapMark mark : snapshot) {
                jMarks.put(mark.toJson());
            }
            main.put("labeledMarks", jMarks);
            main.put("version", 1);
            main.put("lastSaved", java.time.Instant.now().toString());

            try (FileWriter writer = new FileWriter(dataFile, StandardCharsets.UTF_8)) {
                writer.write(main.toString(2));
            }
        } catch (IOException e) {
            System.err.println("Failed to save labeled marks: " + e.getMessage());
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
     * Оптимизировано с использованием индекса - O(1) вместо O(n).
     */
    public List<LabeledMinimapMark> getMarksForSegment(long segmentId) {
        lock.readLock().lock();
        try {
            // Используем индекс для мгновенного получения маркеров нужного сегмента
            List<LabeledMinimapMark> indexed = segmentIndex.get(segmentId);
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
     * Load labeled marks from JSON.
     */
    private void loadLabeledMarks() {
        lock.writeLock().lock();
        try {
            labeledMarks.clear();
            resourceTypeIndex.clear();
            segmentIndex.clear();
            
            File file = new File(dataFile);
            if (file.exists()) {
                StringBuilder contentBuilder = new StringBuilder();
                try (Stream<String> stream = Files.lines(Paths.get(dataFile), StandardCharsets.UTF_8)) {
                    stream.forEach(s -> contentBuilder.append(s).append("\n"));
                } catch (IOException e) {
                    System.err.println("Failed to load labeled marks: " + e.getMessage());
                    return;
                }

                // Сохраняем строку один раз, чтобы избежать множественных вызовов toString()
                String content = contentBuilder.toString();
                // Проверяем длину вместо trim() для избежания OutOfMemoryError на больших файлах
                if (content.length() > 0) {
                    try {
                        JSONObject main = new JSONObject(content);
                        JSONArray array = main.getJSONArray("labeledMarks");
                        for (int i = 0; i < array.length(); i++) {
                            try {
                                LabeledMinimapMark mark = new LabeledMinimapMark(array.getJSONObject(i));
                                labeledMarks.put(mark.getLocationId(), mark);
                                addMarkToIndexes(mark);
                            } catch (Exception e) {
                                System.err.println("Failed to parse labeled mark: " + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to parse labeled marks JSON: " + e.getMessage());
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Save labeled marks to JSON.
     */
    private void saveLabeledMarks() {
        // Called within write lock - don't lock again
        try {
            JSONObject main = new JSONObject();
            JSONArray jMarks = new JSONArray();
            for (LabeledMinimapMark mark : labeledMarks.values()) {
                jMarks.put(mark.toJson());
            }
            main.put("labeledMarks", jMarks);
            main.put("version", 1);
            main.put("lastSaved", java.time.Instant.now().toString());

            try (FileWriter writer = new FileWriter(dataFile, StandardCharsets.UTF_8)) {
                writer.write(main.toString(2)); // Pretty print with indent
            }
        } catch (IOException e) {
            System.err.println("Failed to save labeled marks: " + e.getMessage());
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
            for (LabeledMinimapMark mark : copy) {
                removeMarkFromIndexes(mark.getLocationId());
                removed++;
            }
            
            if (removed > 0) {
                scheduleSave(); // Сохраняем асинхронно
            }
            return removed;
        } finally {
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
        // Ждем завершения всех сохранений перед закрытием
        try {
            saveExecutor.shutdown();
            if (!saveExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        lock.writeLock().lock();
        try {
            saveLabeledMarks();
        } finally {
            lock.writeLock().unlock();
        }
    }
}

