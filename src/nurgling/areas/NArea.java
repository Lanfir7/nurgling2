package nurgling.areas;

import haven.*;
import static haven.MCache.cmaps;
import nurgling.*;
import nurgling.actions.PathFinder;
import nurgling.tools.*;
import org.json.*;

import java.awt.*;
import java.util.*;
import java.util.List;

public class NArea
{
    public long gid = Long.MIN_VALUE;
    public String path = "";
    public boolean hide = false;
    
    // Поля для синхронизации с сервером
    public String uuid = null; // UUID зоны для синхронизации (генерируется при первом сохранении)
    public String zoneSync = null; // Идентификатор мира/сервера для разделения зон
    public long lastUpdated = 0; // Timestamp последнего обновления (в миллисекундах)
    public boolean synced = false; // Флаг синхронизации с сервером



    public static class Specialisation
    {
        public String name;
        public String subtype = null;

        public Specialisation(String name, String subtype) {
            this.name = name;
            this.subtype = subtype;
        }

        public Specialisation(String name) {
            this.name = name;
        }
    }


    private static class TestedArea
    {
        NArea area;
        double th;

        public TestedArea(NArea area, double th) {
            this.area = area;
            this.th = th;
        }
    }

    static Comparator<TestedArea> ta_comp = new Comparator<TestedArea>(){
        @Override
        public int compare(TestedArea o1, TestedArea o2)
        {
            return Double.compare(o1.th, o2.th);
        }
    };

    boolean containIn(String name)
    {
        for (int i = 0; i < jin.length(); i++)
        {
            JSONObject item = (JSONObject) jin.get(i);
            String itemName = item.getString("name");
            
            // Прямое совпадение
            if(itemName.equals(name))
                return true;
            
            // Проверка категорий: если в зоне указана категория, проверяем входит ли предмет в эту категорию
            if(item.has("isCategory") && item.getBoolean("isCategory")) {
                // Если ищем саму категорию (например "Board"), и в зоне указана эта категория
                if(itemName.equals(name)) {
                    return true;
                }
                // Или если ищем конкретный предмет, который входит в эту категорию
                if(isItemInCategory(name, itemName)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Проверяет, входит ли предмет в категорию
     */
    private boolean isItemInCategory(String itemName, String categoryName) {
        ArrayList<org.json.JSONObject> categoryItems = nurgling.tools.VSpec.categories.get(categoryName);
        if(categoryItems != null) {
            for(org.json.JSONObject categoryItem : categoryItems) {
                if(categoryItem.getString("name").equals(itemName)) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean containIn(NAlias name)
    {
        for (int i = 0; i < jin.length(); i++)
        {
            JSONObject item = (JSONObject) jin.get(i);
            String itemName = item.getString("name");
            
            // Прямое совпадение
            if(NParser.eqDefName(itemName, name))
                return true;
            
            // Проверка категорий: если в зоне указана категория, проверяем входит ли предмет в эту категорию
            if(item.has("isCategory") && item.getBoolean("isCategory")) {
                // Для NAlias нужно проверить все предметы в категории
                ArrayList<org.json.JSONObject> categoryItems = nurgling.tools.VSpec.categories.get(itemName);
                if(categoryItems != null) {
                    for(org.json.JSONObject categoryItem : categoryItems) {
                        if(NParser.eqDefName(categoryItem.getString("name"), name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }


    boolean containOut(String name, double th)
    {
        // ВАЖНО: Проверяем что jout не null
        if (jout == null) {
            return false;
        }
        
        for (int i = 0; i < jout.length(); i++) {
            try {
                JSONObject output = jout.getJSONObject(i);
                String outputName = output.getString("name");
                
                // Прямое совпадение
                if (outputName.equals(name)) {
                    if (output.has("th")) {
                        Object thObj = output.get("th");
                        int areaTh = (thObj instanceof Number) ? ((Number) thObj).intValue() : -1;
                        // ВАЖНО: Зона принимает предмет, если качество предмета >= порога зоны
                        // Если порог не указан (areaTh == -1), зона принимает все
                        if (areaTh == -1 || th >= areaTh) {
                            return true;
                        }
                    } else {
                        // Порог не указан - зона принимает все
                        return true;
                    }
                }
                
                // Проверка категорий: если в зоне указана категория, проверяем входит ли предмет в эту категорию
                if(output.has("isCategory") && output.getBoolean("isCategory")) {
                    if(isItemInCategory(name, outputName)) {
                        if (output.has("th")) {
                            Object thObj = output.get("th");
                            int areaTh = (thObj instanceof Number) ? ((Number) thObj).intValue() : -1;
                            if (areaTh == -1 || th >= areaTh) {
                                return true;
                            }
                        } else {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("NArea.containOut: Error checking output " + i + " for zone " + id + ": " + e.getMessage());
            }
        }
        return false;
    }

    private boolean containOut(NAlias name, double th)
    {
        // ВАЖНО: Проверяем что jout не null
        if (jout == null) {
            return false;
        }
        
        for (int i = 0; i < jout.length(); i++) {
            try {
                JSONObject output = jout.getJSONObject(i);
                String outputName = output.getString("name");
                if (NParser.checkName(outputName, name)) {
                    if (output.has("th")) {
                        Object thObj = output.get("th");
                        int areaTh = (thObj instanceof Number) ? ((Number) thObj).intValue() : -1;
                        // ВАЖНО: Зона принимает предмет, если качество предмета >= порога зоны
                        // Если порог не указан (areaTh == -1), зона принимает все
                        if (areaTh == -1 || th >= areaTh) {
                            return true;
                        }
                    } else {
                        // Порог не указан - зона принимает все
                        return true;
                    }
                }
            } catch (Exception e) {
                System.err.println("NArea.containOut: Error checking output " + i + " for zone " + id + ": " + e.getMessage());
            }
        }
        return false;
    }

    public boolean containOut(String name)
    {
        // ВАЖНО: Проверяем что jout не null
        if (jout == null) {
            return false;
        }
        
        for (int i = 0; i < jout.length(); i++) {
            try {
                JSONObject output = jout.getJSONObject(i);
                String outputName = output.getString("name");
                
                // Прямое совпадение
                if (outputName.equals(name)) {
                    return true;
                }
                
                // Проверка категорий: если в зоне указана категория, проверяем входит ли предмет в эту категорию
                if(output.has("isCategory") && output.getBoolean("isCategory")) {
                    if(isItemInCategory(name, outputName)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                System.err.println("NArea.containOut: Error checking output " + i + " for zone " + id + ": " + e.getMessage());
            }
        }
        return false;
    }

    private boolean containOut(NAlias name)
    {
        for (int i = 0; i < jout.length(); i++) {
            if (NParser.checkName((String) ((JSONObject) jout.get(i)).get("name"),name))
                return true;
        }
        return false;
    }

    public static class VArea
    {
        public Area area;
        public boolean isVis = false;

        public VArea(Area area)
        {
            this.area = area;
        }


    }

    public static class Space
    {
        private final int max = 100;
        private final int min = 0;

        public HashMap<Long,VArea> space = new HashMap<>();
        public Space()
        {}

        public Space(Coord sc, Coord ec)
        {
            Coord begin = new Coord(Math.min(sc.x, ec.x), Math.min(sc.y, ec.y));
            Coord end = new Coord(Math.max(sc.x, ec.x), Math.max(sc.y, ec.y));
            Coord bd = begin.div(cmaps);
            Coord ed = end.div(cmaps);
            Coord bm = begin.mod(cmaps);
            Coord em = end.mod(cmaps).add(1,1);
            
            // ВАЖНО: Синхронизируемся с grids для потокобезопасности
            Map<Coord, MCache.Grid> grids = NUtils.getGameUI().map.glob.map.grids;
            synchronized(grids) {
                if (bd.equals(ed.x,ed.y))
                {
                    MCache.Grid grid = grids.get(bd);
                    if (grid != null) {
                        space.put(grid.id, new VArea(new Area(bm, em)));
                    }
                }
                else
                {
                    if (bd.x != ed.x && bd.y != ed.y)
                    {
                        MCache.Grid grid = grids.get(bd);
                        if (grid != null) space.put(grid.id, new VArea(new Area(bm, new Coord(max,max))));
                        grid = grids.get(new Coord(bd.x, ed.y));
                        if (grid != null) space.put(grid.id, new VArea(new Area(new Coord(bm.x, min), new Coord(max, em.y))));
                        grid = grids.get(new Coord(ed.x, bd.y));
                        if (grid != null) space.put(grid.id, new VArea(new Area(new Coord(min, bm.y), new Coord(em.x, max))));
                        grid = grids.get(ed);
                        if (grid != null) space.put(grid.id, new VArea(new Area(new Coord(min, min), em)));
                    }
                    else if (bd.x != ed.x)
                    {
                        MCache.Grid grid = grids.get(bd);
                        if (grid != null) space.put(grid.id, new VArea(new Area(bm, new Coord(max, em.y))));
                        grid = grids.get(new Coord(ed.x, bd.y));
                        if (grid != null) space.put(grid.id, new VArea(new Area(new Coord(min, bm.y), em)));
                    }
                    else
                    {
                        MCache.Grid grid = grids.get(bd);
                        if (grid != null) space.put(grid.id, new VArea(new Area(bm, new Coord(em.x, max))));
                        grid = grids.get(new Coord(bd.x, ed.y));
                        if (grid != null) space.put(grid.id, new VArea(new Area(new Coord(bm.x, min), em)));
                    }
                }
            }
        }
    }

    public NArea(String name)
    {
        this.name = name;
        this.color = generateRandomColor();
    }
    
    private static Color generateRandomColor()
    {
        Random random = new Random();
        // Генерируем яркие цвета (100-255) для лучшей видимости
        int r = 100 + random.nextInt(156);
        int g = 100 + random.nextInt(156);
        int b = 100 + random.nextInt(156);
        // Альфа-канал для прозрачности (56 как в оригинале)
        int a = 90;
        return new Color(r, g, b, a);
    }

    /**
     * Update this area's fields from another area (for sync without replacing object reference).
     * Also copies the uuid/presence metadata when present.
     */
    public void updateFrom(NArea other) {
        this.name = other.name;
        this.path = other.path;
        this.hide = other.hide;
        this.color = other.color;
        this.space = other.space;
        this.version = other.version;
        this.grids_id.clear();
        this.grids_id.addAll(other.grids_id);
        this.jin = other.jin;
        this.jout = other.jout;
        this.jspec = other.jspec;
        this.spec.clear();
        this.spec.addAll(other.spec);
        // Copy sync metadata if the incoming area carries it.
        if (other.uuid != null) this.uuid = other.uuid;
        this.lastTouchedBy = other.lastTouchedBy;
        this.lastTouchedAt = other.lastTouchedAt;
        // Don't copy lastLocalChange - keep our own timestamp
        // After sync, our local state matches server state for all groups.
        this.baselineVersion = other.version;
        this.baselineSnapshot = AreaSnapshot.of(this);
        this.dirtyGroups.clear();
    }

    /**
     * Mark a field group as locally dirty. Called from the touch sites that
     * already update lastLocalChange.
     */
    public synchronized void markDirty(AreaFieldGroup group) {
        if (group != null) {
            dirtyGroups.add(group);
        }
        this.lastLocalChange = System.currentTimeMillis();
    }

    /**
     * Snapshot the current state as the baseline (what we last knew the
     * server to hold). Clears the dirty-group set.
     */
    public synchronized void captureBaseline() {
        this.baselineSnapshot = AreaSnapshot.of(this);
        this.baselineVersion = this.version;
        this.dirtyGroups.clear();
    }

    public NArea(JSONObject obj)
    {
        this.name = (String) obj.get("name");
        this.id = (Integer) obj.get("id");
        if(obj.has("uuid")) {
            this.uuid = obj.getString("uuid");
        }
        if(obj.has("path"))
        {
            this.path = obj.getString("path");
        }
        else if(obj.has("dir"))
        {
            this.path = "/" + obj.getString("path");
        }
        if(obj.has("hide")) {
            this.hide = obj.getBoolean("hide");
        }
        if(obj.has("color"))
        {
            JSONObject color = (JSONObject) obj.get("color");
            if (color != null)
            {
                this.color = new Color((Integer) color.get("r"), (Integer) color.get("g"), (Integer) color.get("b"), (Integer) color.get("a"));
            }
        }
        space = new Space();
        JSONArray jareas = (JSONArray) obj.get("space");
        for (int i = 0; i < jareas.length(); i++)
        {
            JSONObject jarea = (JSONObject) jareas.get(i);
            space.space.put((Long) jarea.get("id"), new VArea(new Area(new Coord((Integer) jarea.get("begin_x"), (Integer) jarea.get("begin_y")), new Coord((Integer) jarea.get("end_x"), (Integer) jarea.get("end_y")))));
            grids_id.add((Long)jarea.get("id"));
        }
        if(obj.has("in"))
        {
            jin = (JSONArray) obj.get("in");
        }
        if(obj.has("out"))
        {
            jout = (JSONArray) obj.get("out");
        }
        if(obj.has("spec"))
        {
            jspec = (JSONArray) obj.get("spec");
            for(int i = 0 ; i < jspec.length(); i++) {

                String name = (String) ((JSONObject) jspec.get(i)).get("name");
                if (((JSONObject) jspec.get(i)).has("subtype")) {
                    spec.add(new Specialisation(name, (String) ((JSONObject) jspec.get(i)).get("subtype")));
                }
                else
                {
                    spec.add(new Specialisation(name));
                }
            }
        }
        
        // Загружаем version
        if(obj.has("version")) {
            this.version = obj.getInt("version");
        }
        
        // Загружаем поля синхронизации (если есть)
        if(obj.has("uuid")) {
            this.uuid = obj.getString("uuid");
        }
        if(obj.has("zone_sync")) {
            this.zoneSync = obj.getString("zone_sync");
        }
        if(obj.has("last_updated")) {
            try {
                // Может быть ISO8601 строка или timestamp в миллисекундах
                Object lastUpdatedObj = obj.get("last_updated");
                if (lastUpdatedObj instanceof String) {
                    // ISO8601 формат: "2025-01-01T12:00:00Z"
                    String isoStr = (String) lastUpdatedObj;
                    // Простой парсинг ISO8601 (можно улучшить)
                    java.time.Instant instant = java.time.Instant.parse(isoStr);
                    this.lastUpdated = instant.toEpochMilli();
                } else if (lastUpdatedObj instanceof Long) {
                    this.lastUpdated = (Long) lastUpdatedObj;
                } else if (lastUpdatedObj instanceof Integer) {
                    this.lastUpdated = ((Integer) lastUpdatedObj).longValue();
                }
            } catch (Exception e) {
                // Игнорируем ошибки парсинга
                this.lastUpdated = System.currentTimeMillis();
            }
        }
    }
    public Space space;
    public String name;
    public int id;
    public int version = 1;  // Version for sync - incremented on each update
    public long lastLocalChange = 0;  // Timestamp of last local change (to prevent sync overwrite)
    public Color color = new Color(194,194,65,56);
    public final ArrayList<Long> grids_id = new ArrayList<>();

    // Sync metadata - Phase 1/2/3/5 area-sync refactor
    public int baselineVersion = 0;            // last server version we synced from (Phase 1)
    public AreaSnapshot baselineSnapshot = null; // last server state we synced from (Phase 2)
    public final java.util.EnumSet<AreaFieldGroup> dirtyGroups = java.util.EnumSet.noneOf(AreaFieldGroup.class);
    public String lastTouchedBy = null;        // last editor name from server (Phase 5)
    public long lastTouchedAt = 0;             // server timestamp of last edit (Phase 5)

    public ArrayList<Specialisation> spec = new ArrayList<>();
    public boolean inWork = false;

    /**
     * Приводит {@link #grids_id} в соответствие с ключами {@link #space}.
     * ВАЖНО: grids_id используется в рендеринге оверлея (MCache#getnolcut),
     * поэтому при замене/мердже space обязательно синхронизировать список,
     * иначе возможны NPE при построении меша (space для grid_id отсутствует).
     */
    public void syncGridIdsFromSpace() {
        grids_id.clear();
        if (space == null || space.space == null || space.space.isEmpty()) {
            return;
        }
        ArrayList<Long> ids = new ArrayList<>(space.space.keySet());
        Collections.sort(ids);
        grids_id.addAll(ids);
    }

    public Area getArea()
    {
        if (NUtils.getGameUI() == null || NUtils.getGameUI().map == null) {
            return null;
        }
        Coord begin = null;
        Coord end = null;
        for (Long id : space.space.keySet())
        {
            MCache.Grid grid = NUtils.getGameUI().map.glob.map.findGrid(id);
            if(grid!=null)
            {
                Area area = space.space.get(id).area;
                Coord b = area.ul.add(grid.ul);
                Coord e = area.br.add(grid.ul);
                begin = (begin != null) ? new Coord(Math.min(begin.x, b.x), Math.min(begin.y, b.y)) : b;
                end = (end != null) ? new Coord(Math.max(end.x, e.x), Math.max(end.y, e.y)) : e;
            }
        }
        return new Area(begin,end);
    }

    /**
     * Получает координаты зоны для создания overlay (без проверки hide)
     * Используется в createAreaLabel() чтобы всегда создавать overlay
     */
    private Pair<Coord2d,Coord2d> getRCAreaForOverlay()
    {
        if(isVisible())
        {
            Coord begin = null;
            Coord end = null;

            for (Long id : space.space.keySet())
            {
                MCache.Grid grid = NUtils.getGameUI().map.glob.map.findGrid(id);
                if(grid==null)
                    return null;
                Area area = space.space.get(id).area;
                Coord b = area.ul.add(grid.ul);
                Coord e = area.br.add(grid.ul);
                begin = (begin != null) ? new Coord(Math.min(begin.x, b.x), Math.min(begin.y, b.y)) : b;
                end = (end != null) ? new Coord(Math.max(end.x, e.x), Math.max(end.y, e.y)) : e;
            }
            if (begin != null) {
                if (NUtils.player()!=null && begin.mul(MCache.tilesz).dist(NUtils.player().rc) > 1000 && end.mul(MCache.tilesz).dist(NUtils.player().rc) > 1000) {
                    return null;
                }
                return new Pair<Coord2d, Coord2d>(begin.mul(MCache.tilesz), end.sub(1, 1).mul(MCache.tilesz).add(MCache.tilesz));
            }
        }
        return null;
    }

    
    /**
     * Получает координаты зоны без проверки hide (для создания overlay)
     * Используется в createAreaLabel() чтобы всегда создавать overlay, даже для скрытых зон
     */
    public Pair<Coord2d,Coord2d> getRawRCArea()
    {
        if(isVisible())
        {
            Coord begin = null;
            Coord end = null;

            for (Long id : space.space.keySet())
            {
                MCache.Grid grid = NUtils.getGameUI().map.glob.map.findGrid(id);
                if(grid==null) // НЕ проверяем hide здесь
                    return null;
                Area area = space.space.get(id).area;
                Coord b = area.ul.add(grid.ul);
                Coord e = area.br.add(grid.ul);
                begin = (begin != null) ? new Coord(Math.min(begin.x, b.x), Math.min(begin.y, b.y)) : b;
                end = (end != null) ? new Coord(Math.max(end.x, e.x), Math.max(end.y, e.y)) : e;
            }
            if (begin != null) {
                if (NUtils.player()!=null && begin.mul(MCache.tilesz).dist(NUtils.player().rc) > 1000 && end.mul(MCache.tilesz).dist(NUtils.player().rc) > 1000) {
                    return null;
                }
                return new Pair<Coord2d, Coord2d>(begin.mul(MCache.tilesz), end.sub(1, 1).mul(MCache.tilesz).add(MCache.tilesz));
            }
        }
        return null;
    }

    public Pair<Coord2d,Coord2d> getRCArea()
    {
        return getRCArea(true);
    }

    // respectHide=false computes the geometry even for hidden areas; used to place
    // the name label so disabled areas can still show a (grayed-out) label.
    public Pair<Coord2d,Coord2d> getRCArea(boolean respectHide)
    {
        Pair<Coord2d,Coord2d> live = getLoadedRCArea(respectHide);
        if (live != null)
            return live;
        return getRCAreaFromStoredData();
    }

    /**
     * Geometry from grids currently loaded on this map only. No ChunkNav fallback —
     * labels must not appear in a house/mine using outdoor coordinates.
     */
    public Pair<Coord2d,Coord2d> getLoadedRCArea(boolean respectHide)
    {
        if(isVisible())
        {
            Coord begin = null;
            Coord end = null;

            for (Long id : space.space.keySet())
            {
                MCache.Grid grid = NUtils.getGameUI().map.glob.map.findGrid(id);
                if(grid==null || (respectHide && hide))
                    return null;
                Area area = space.space.get(id).area;
                Coord b = area.ul.add(grid.ul);
                Coord e = area.br.add(grid.ul);
                begin = (begin != null) ? new Coord(Math.min(begin.x, b.x), Math.min(begin.y, b.y)) : b;
                end = (end != null) ? new Coord(Math.max(end.x, e.x), Math.max(end.y, e.y)) : e;
            }
            if (begin != null) {
                if (NUtils.player()!=null && begin.mul(MCache.tilesz).dist(NUtils.player().rc) > 1000 && end.mul(MCache.tilesz).dist(NUtils.player().rc) > 1000) {
                    return null;
                }
                return new Pair<Coord2d, Coord2d>(begin.mul(MCache.tilesz), end.sub(1, 1).mul(MCache.tilesz).add(MCache.tilesz));
            }
        }
        return null;
    }
    
    /**
     * Получает координаты зоны из сохраненных данных ChunkNav, даже когда зона не видна.
     * Использует worldTileOrigin из записанных чанков для расчета мировых координат.
     */
    public Pair<Coord2d, Coord2d> getRCAreaFromStoredData() {
        if (space == null || space.space == null || space.space.isEmpty()) {
            return null;
        }
        
        try {
            // Получаем ChunkNavManager
            if (NUtils.getGameUI() == null || NUtils.getGameUI().map == null) {
                return null;
            }
            
            if (!(NUtils.getGameUI().map instanceof nurgling.NMapView)) {
                return null;
            }
            
            nurgling.NMapView mapView = (nurgling.NMapView) NUtils.getGameUI().map;
            nurgling.navigation.ChunkNavManager chunkNav = mapView.getChunkNavManager();
            if (chunkNav == null || !chunkNav.isInitialized()) {
                return null;
            }
            
            nurgling.navigation.ChunkNavGraph graph = chunkNav.getGraph();
            if (graph == null) {
                return null;
            }
            
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            int foundChunks = 0;
            
            for (java.util.Map.Entry<Long, VArea> entry : space.space.entrySet()) {
                long gridId = entry.getKey();
                VArea varea = entry.getValue();
                
                if (varea == null || varea.area == null) {
                    continue;
                }
                
                // Пытаемся получить worldTileOrigin из сохраненных данных чанка
                nurgling.navigation.ChunkNavData chunk = graph.getChunk(gridId);
                if (chunk == null || chunk.worldTileOrigin == null) {
                    continue;
                }
                
                // Вычисляем мировые координаты тайлов
                Coord ul = chunk.worldTileOrigin.add(varea.area.ul);
                Coord br = chunk.worldTileOrigin.add(varea.area.br);
                
                minX = Math.min(minX, ul.x);
                minY = Math.min(minY, ul.y);
                maxX = Math.max(maxX, br.x);
                maxY = Math.max(maxY, br.y);
                foundChunks++;
            }
            
            if (foundChunks == 0) {
                return null;
            }
            
            // Конвертируем координаты тайлов в мировые координаты
            Coord2d begin = new Coord(minX, minY).mul(MCache.tilesz);
            Coord2d end = new Coord(maxX - 1, maxY - 1).mul(MCache.tilesz).add(MCache.tilesz);
            
            return new Pair<>(begin, end);
            
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if a position is inside this area
     * @param pos Position in world coordinates (RC)
     * @return true if position is inside the area
     */
    public boolean checkHit(Coord2d pos) {
        Pair<Coord2d, Coord2d> rcArea = getRCArea();
        if (rcArea == null || pos == null) {
            return false;
        }
        Coord2d begin = rcArea.a;
        Coord2d end = rcArea.b;
        return pos.x >= begin.x && pos.x <= end.x && pos.y >= begin.y && pos.y <= end.y;
    }

    public void tick(double dt)
    {
        NGameUI gui = NUtils.getGameUI();
        if(gui != null && gui.map != null && gui.map instanceof NMapView && !inWork)
        {
            NMapView mapView = (NMapView) gui.map;
            // Only add overlay if it doesn't exist and this area is registered in the current session's map
            if(mapView.nols.get(id) == null && NUtils.getArea(id) != null)
            {
                mapView.addCustomOverlay(id);
            }
        }
    }

    public boolean isVisible() {
        for (Long id : space.space.keySet()) {
            synchronized (NUtils.getGameUI().map.glob.map.grids) {
                for (MCache.Grid g : NUtils.getGameUI().map.glob.map.grids.values()) {
                    if (g.id == id)
                        return true;
                }
            }
        }
        return false;
    }

    public JSONObject toJson()
    {
        JSONObject res = new JSONObject();
        res.put("name", name);
        res.put("id", id);
        if (uuid != null) res.put("uuid", uuid);
        res.put("path", path);
        JSONObject jcolor = new JSONObject();
        jcolor.put("r", color.getRed());
        jcolor.put("g", color.getGreen());
        jcolor.put("b", color.getBlue());
        jcolor.put("a", color.getAlpha());
        res.put("color", jcolor);
        JSONArray jspaces = new JSONArray();
        for(long id : space.space.keySet())
        {
            JSONObject jspace = new JSONObject();
            jspace.put("id", id);
            jspace.put("begin_x", space.space.get(id).area.ul.x);
            jspace.put("begin_y", space.space.get(id).area.ul.y);
            jspace.put("end_x", space.space.get(id).area.br.x);
            jspace.put("end_y", space.space.get(id).area.br.y);
            jspaces.put(jspace);
        }
        res.put("space",jspaces);
        res.put("in",jin);
        res.put("out",jout);
        res.put("hide",hide);
        JSONArray jspec = new JSONArray();
        for(Specialisation s: spec)
        {
            JSONObject obj = new JSONObject();
            obj.put("name", s.name);
            if(s.subtype!=null)
                obj.put("subtype", s.subtype);
            jspec.put(obj);
        }
        res.put("spec",jspec);
        res.put("version", version);
        this.jspec = jspec;
        
        // Добавляем поля синхронизации (если они установлены)
        if (uuid != null) {
            res.put("uuid", uuid);
        }
        if (zoneSync != null) {
            res.put("zone_sync", zoneSync);
        }
        if (lastUpdated > 0) {
            // Сохраняем как ISO8601 строку для совместимости с сервером
            java.time.Instant instant = java.time.Instant.ofEpochMilli(lastUpdated);
            res.put("last_updated", instant.toString());
        }
        
        return res;
    }
    
    public JSONArray jin = new JSONArray();
    public JSONArray jspec = new JSONArray();
    public JSONArray jout = new JSONArray();

    public static class Ingredient
    {
        public static enum Type
        {
            BARTER,
            CONTAINER,
            BARREL, PILE
        }

        public Type type;

        String name;


        public int th = -1;
        public Ingredient(Type type, String name)
        {
            this.type = type;
            this.name = name;
        }

        public Ingredient(Type type, String name, int th)
        {
            this(type,name);
            this.th = th;
        }
    }

    public Ingredient getInput(String name)
    {
        for (int i = 0; i < jin.length(); i++)
        {
            JSONObject obj = (JSONObject)jin.get(i);
            String itemName = obj.getString("name");
            
            // Прямое совпадение
            if(itemName.equals(name))
            {
                NArea.Ingredient.Type type = (obj.has("type")) ?
                        type = NArea.Ingredient.Type.valueOf((String) obj.get("type")) :
                        Ingredient.Type.CONTAINER;
                return new Ingredient(type,name);
            }
            
            // Проверка категорий: если в зоне указана категория, проверяем входит ли предмет в эту категорию
            if(obj.has("isCategory") && obj.getBoolean("isCategory")) {
                // Если ищем категорию и в зоне сохранена та же категория - совпадение
                if(itemName.equals(name) && (name.equals("Board") || name.equals("Block of Wood"))) {
                    NArea.Ingredient.Type type = (obj.has("type")) ?
                            type = NArea.Ingredient.Type.valueOf((String) obj.get("type")) :
                            Ingredient.Type.CONTAINER;
                    return new Ingredient(type, itemName);
                }
                // Если в зоне категория, а ищем конкретный предмет - проверяем входит ли он в категорию
                if(isItemInCategory(name, itemName)) {
                    NArea.Ingredient.Type type = (obj.has("type")) ?
                            type = NArea.Ingredient.Type.valueOf((String) obj.get("type")) :
                            Ingredient.Type.CONTAINER;
                    return new Ingredient(type, itemName); // Возвращаем категорию, а не конкретный предмет
                }
            }
        }
        return null;
    }

    public Ingredient getOutput(String name) {
        // ВАЖНО: Проверяем что jout не null
        if (jout == null) {
            return null;
        }
        
        for (int i = 0; i < jout.length(); i++)
        {
            JSONObject obj = (JSONObject)jout.get(i);
            String itemName = obj.getString("name");
            
            // Прямое совпадение
            if(itemName.equals(name))
            {
                NArea.Ingredient.Type type = (obj.has("type")) ?
                        type = NArea.Ingredient.Type.valueOf((String) obj.get("type")) :
                        Ingredient.Type.CONTAINER;
                if(obj.has("th"))
                {
                    Object thObj = obj.get("th");
                    int th = (thObj instanceof Number) ? ((Number) thObj).intValue() : -1;
                    return new Ingredient(type,name, th);
                }
                return new Ingredient(type,name);
            }
            
            // Проверка категорий: если в зоне указана категория, проверяем входит ли предмет в эту категорию
            if(obj.has("isCategory") && obj.getBoolean("isCategory")) {
                if(isItemInCategory(name, itemName)) {
                    NArea.Ingredient.Type type = (obj.has("type")) ?
                            type = NArea.Ingredient.Type.valueOf((String) obj.get("type")) :
                            Ingredient.Type.CONTAINER;
                    if(obj.has("th"))
                    {
                        Object thObj = obj.get("th");
                        int th = (thObj instanceof Number) ? ((Number) thObj).intValue() : -1;
                        return new Ingredient(type, itemName, th); // Возвращаем категорию, а не конкретный предмет
                    }
                    return new Ingredient(type, itemName);
                }
            }
        }
        return null;
    }



    public ArrayList<Coord2d> getTiles(NAlias name){
        ArrayList<Coord2d> tiles = new ArrayList<>();
        Pair<Coord2d,Coord2d> range = getRCArea();
        Coord2d pos = new Coord2d(range.a.x,range.a.y);
        while ( pos.x < range.b.x ) {
            while ( pos.y < range.b.y ) {
                Coord pltc = ( new Coord2d ( pos.x / MCache.tilesz.x, pos.y / MCache.tilesz.y ) ).floor ();
                Resource res_beg = NUtils.getGameUI().ui.sess.glob.map.tilesetr ( NUtils.getGameUI().ui.sess.glob.map.gettile ( pltc ) );
                if ( NParser.checkName ( res_beg.name, name ) ) {
                    tiles.add(new Coord2d(pos.x, pos.y));
                }
                pos.y += MCache.tilesz.y;
            }
            pos.y = range.a.y;
            pos.x += MCache.tilesz.x;
        }
        return tiles;
    }

    public Coord3f getCenter3f() {
        Pair<Coord2d,Coord2d> rcArea = getRCArea();
        if(rcArea!=null)
        {
            Coord2d center = getCenter2d();
            if(center!=null)
                return NUtils.getGameUI().map.glob.map.getzp(center);
        }
        return null;
    }

    public Coord2d getCenter2d() {
        Pair<Coord2d,Coord2d> rcArea = getRCArea();
        if(rcArea!=null)
        {
            return  (rcArea.b.sub(rcArea.a)).div(2).add(rcArea.a);
        }
        return null;
    }

    public double getDistance(Coord2d myrc) {
        double distance = Double.MAX_VALUE;
        Pair<Coord2d,Coord2d> rcArea = getRCArea();
        if(myrc==null)
            return distance;
        if(rcArea!=null)
        {
            distance = Math.min(myrc.dist(rcArea.a),distance);
            distance = Math.min(myrc.dist(rcArea.b),distance);
            distance = Math.min(myrc.dist(Coord2d.of(rcArea.a.x,rcArea.b.y)),distance);
            distance = Math.min(myrc.dist(Coord2d.of(rcArea.b.x,rcArea.a.y)),distance);
        }
        return distance;
    }

}
