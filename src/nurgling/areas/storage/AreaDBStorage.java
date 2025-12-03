package nurgling.areas.storage;

import haven.Area;
import haven.Coord;
import nurgling.areas.NArea;
import org.json.JSONObject;

import java.sql.*;
import java.util.*;
import java.util.Objects;

/**
 * Реализация хранилища зон через БД
 */
public class AreaDBStorage implements AreaStorage {
    private final DatabaseConnectionManager poolManager;
    
    public AreaDBStorage(DatabaseConnectionManager poolManager) {
        this.poolManager = poolManager;
    }
    
    @Override
    public boolean isAvailable() {
        try {
            Connection conn = poolManager.getConnection();
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
    
    @Override
    public Map<Integer, NArea> loadAllAreas() throws StorageException {
        Map<Integer, NArea> areas = new HashMap<>();
        
        if (!isAvailable()) {
            throw new StorageException("Database connection is not available");
        }
        
        try {
            Connection conn = poolManager.getConnection();
            
            // Загружаем основные данные зон
            String sql = "SELECT id, global_id, name, path, color_r, color_g, color_b, color_a, " +
                        "hide, sync_status, sync_version, zone_sync, updated_at, last_sync_at FROM areas WHERE deleted = FALSE";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    int areaId = rs.getInt("id");
                    NArea area = new NArea(rs.getString("name"));
                    area.id = areaId;
                    area.path = rs.getString("path");
                    if (rs.wasNull()) area.path = "";
                    
                    // Загружаем UUID (global_id)
                    String globalId = rs.getString("global_id");
                    if (globalId != null && !globalId.isEmpty()) {
                        area.uuid = globalId;
                    }
                    
                    // Загружаем zone_sync
                    String zoneSync = rs.getString("zone_sync");
                    if (zoneSync != null && !zoneSync.isEmpty()) {
                        area.zoneSync = zoneSync;
                    }
                    
                    // Загружаем last_updated
                    java.sql.Timestamp updatedAt = rs.getTimestamp("updated_at");
                    if (updatedAt != null) {
                        area.lastUpdated = updatedAt.getTime();
                        
                    } else {
                        System.out.println("AreaDBStorage: WARNING - Zone " + area.id + " (" + area.name + ") has NULL updated_at");
                    }
                    
                    // Загружаем last_sync_at (если есть) - используется для синхронизации
                    java.sql.Timestamp lastSyncAt = rs.getTimestamp("last_sync_at");
                    if (lastSyncAt != null) {
                        // Устанавливаем флаг синхронизации
                        area.synced = true;
                    }
                    
                    int r = rs.getInt("color_r");
                    int g = rs.getInt("color_g");
                    int b = rs.getInt("color_b");
                    int a = rs.getInt("color_a");
                    area.color = new java.awt.Color(r, g, b, a);
                    
                    area.hide = rs.getBoolean("hide");
                    
                    // Инициализируем space перед загрузкой данных
                    if (area.space == null) {
                        area.space = new NArea.Space();
                    }
                    
                    // ВАЖНО: Загружаем пространственные данные (координаты зоны)
                    loadAreaSpaces(conn, area);
                    
                    // Загружаем входы
                    loadAreaInputs(conn, area);
                    
                    // Загружаем выходы
                    loadAreaOutputs(conn, area);
                    
                    // Загружаем специализации
                    loadAreaSpecialisations(conn, area);
                    
                    areas.put(areaId, area);
                }
            }
            
            conn.commit();
        } catch (SQLException e) {
            throw new StorageException("Failed to load areas from database", e);
        }
        
        return areas;
    }
    
    private void loadAreaSpaces(Connection conn, NArea area) throws SQLException {
        // Инициализируем space если оно null
        if (area.space == null) {
            area.space = new NArea.Space();
        }
        
        String sql = "SELECT grid_id, begin_x, begin_y, end_x, end_y FROM area_spaces WHERE area_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, area.id);
            try (ResultSet rs = stmt.executeQuery()) {
                int spaceCount = 0;
                while (rs.next()) {
                    long gridId = rs.getLong("grid_id");
                    Coord begin = new Coord(rs.getInt("begin_x"), rs.getInt("begin_y"));
                    Coord end = new Coord(rs.getInt("end_x"), rs.getInt("end_y"));
                    area.space.space.put(gridId, new NArea.VArea(new Area(begin, end)));
                    area.grids_id.add(gridId);
                    spaceCount++;
                }
                
            }
        }
    }
    
    private void loadAreaInputs(Connection conn, NArea area) throws SQLException {
        // Инициализируем jin если оно null
        if (area.jin == null) {
            area.jin = new org.json.JSONArray();
        }
        
        String sql = "SELECT name, type, icon_data FROM area_inputs WHERE area_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, area.id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JSONObject input = new JSONObject();
                    input.put("name", rs.getString("name"));
                    String type = rs.getString("type");
                    if (type != null) {
                        input.put("type", type);
                    }
                    // Загружаем данные об изображении если они есть
                    String iconData = rs.getString("icon_data");
                    if (iconData != null && !rs.wasNull() && !iconData.isEmpty()) {
                        try {
                            JSONObject iconJson = new JSONObject(iconData);
                            // Копируем поля из icon_data в основной объект
                            if (iconJson.has("layer")) {
                                input.put("layer", iconJson.get("layer"));
                            }
                            if (iconJson.has("static")) {
                                input.put("static", iconJson.get("static"));
                            }
                        } catch (Exception e) {
                            // Игнорируем ошибки парсинга icon_data
                        }
                    }
                    area.jin.put(input);
                }
            }
        }
    }
    
    private void loadAreaOutputs(Connection conn, NArea area) throws SQLException {
        // Инициализируем jout если оно null
        if (area.jout == null) {
            area.jout = new org.json.JSONArray();
        }
        
        String sql = "SELECT name, type, th, icon_data FROM area_outputs WHERE area_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, area.id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JSONObject output = new JSONObject();
                    output.put("name", rs.getString("name"));
                    String type = rs.getString("type");
                    if (type != null) {
                        output.put("type", type);
                    }
                    int th = rs.getInt("th");
                    if (!rs.wasNull() && th >= 0) {
                        output.put("th", th);
                    }
                    // Загружаем данные об изображении если они есть
                    String iconData = rs.getString("icon_data");
                    if (iconData != null && !rs.wasNull() && !iconData.isEmpty()) {
                        try {
                            JSONObject iconJson = new JSONObject(iconData);
                            // Копируем поля из icon_data в основной объект
                            if (iconJson.has("layer")) {
                                output.put("layer", iconJson.get("layer"));
                            }
                            if (iconJson.has("static")) {
                                output.put("static", iconJson.get("static"));
                            }
                        } catch (Exception e) {
                            // Игнорируем ошибки парсинга icon_data
                        }
                    }
                    area.jout.put(output);
                }
            }
        }
    }
    
    private void loadAreaSpecialisations(Connection conn, NArea area) throws SQLException {
        // Инициализируем spec если оно null
        if (area.spec == null) {
            area.spec = new ArrayList<>();
        }
        
        String sql = "SELECT name, subtype FROM area_specialisations WHERE area_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, area.id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String subtype = rs.getString("subtype");
                    if (subtype != null && !rs.wasNull()) {
                        area.spec.add(new NArea.Specialisation(name, subtype));
                    } else {
                        area.spec.add(new NArea.Specialisation(name));
                    }
                }
            }
        }
    }
    
    @Override
    public void saveArea(NArea area) throws StorageException {
        if (!isAvailable()) {
            throw new StorageException("Database connection is not available");
        }
        
        // Инициализируем space если оно null
        if (area.space == null) {
            area.space = new NArea.Space();
        }
        
        // Инициализируем другие поля если они null
        if (area.jin == null) {
            area.jin = new org.json.JSONArray();
        }
        if (area.jout == null) {
            area.jout = new org.json.JSONArray();
        }
        if (area.spec == null) {
            area.spec = new ArrayList<>();
        }
        
        // Генерируем UUID если его нет (для новых зон)
        if (area.uuid == null || area.uuid.isEmpty()) {
            area.uuid = java.util.UUID.randomUUID().toString();
        }
        
        // Устанавливаем zone_sync из настроек, если не установлен
        if (area.zoneSync == null || area.zoneSync.isEmpty()) {
            Object zoneSyncObj = nurgling.NConfig.get(nurgling.NConfig.Key.syncZoneSync);
            if (zoneSyncObj != null && zoneSyncObj instanceof String) {
                String zoneSync = ((String) zoneSyncObj).trim();
                if (!zoneSync.isEmpty()) {
                    area.zoneSync = zoneSync;
                }
            }
        }
        
        // Устанавливаем last_updated если не установлен
        if (area.lastUpdated == 0) {
            area.lastUpdated = System.currentTimeMillis();
        }
        
        int retries = 3;
        SQLException lastException = null;
        Connection conn = null;
        
        while (retries > 0) {
            try {
                // Получаем соединение и проверяем его валидность
                conn = poolManager.getConnection();
                if (conn == null || conn.isClosed()) {
                    throw new StorageException("Database connection is null or closed");
                }
                
                // Проверяем, существует ли зона (не удаленная)
                boolean exists = areaExists(conn, area.id);
                
                if (exists) {
                    updateArea(conn, area);
                } else {
                    // Проверяем, не была ли зона удалена
                    boolean wasDeleted = checkIfDeleted(conn, area.id);
                    if (wasDeleted) {
                        // Восстанавливаем удаленную зону
                        restoreArea(conn, area);
                    } else {
                        // Создаем новую зону
                        insertArea(conn, area);
                    }
                }
                
                conn.commit();
                return; // Успешно сохранено
            } catch (SQLException e) {
                lastException = e;
                String errorMsg = e.getMessage();
                String sqlState = e.getSQLState();
                
                // Логируем детали ошибки для диагностики
                if (retries == 3) { // Логируем только при первой попытке
                    System.err.println("AreaDBStorage: Error saving area " + area.id + 
                                     " - SQLState: " + sqlState + 
                                     ", Message: " + errorMsg);
                }
                
                // Откатываем транзакцию при ошибке
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        // Ignore rollback errors
                    }
                }
                
                // Если это блокировка БД, пробуем еще раз
                if (errorMsg != null && (errorMsg.contains("locked") || errorMsg.contains("BUSY") || 
                    errorMsg.contains("database is locked") || sqlState != null && sqlState.contains("BUSY"))) {
                    retries--;
                    if (retries > 0) {
                        // Увеличиваем задержку с каждой попыткой: 500ms, 1000ms, 2000ms
                        int delay = 500 * (4 - retries);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new StorageException("Interrupted while retrying save", e);
                        }
                        // Закрываем текущее соединение и получаем новое
                        if (conn != null) {
                            try {
                                conn.close();
                            } catch (SQLException closeEx) {
                                // Ignore
                            }
                        }
                        continue; // Пробуем еще раз
                    } else {
                        // Если все попытки исчерпаны, бросаем специальное исключение для блокировки
                        // которое будет обработано в AreaDBManager как не критичная ошибка
                        throw new StorageException("Database locked after retries (non-critical)", e);
                    }
                }
                
                // Если это не блокировка или закончились попытки
                throw new StorageException("Failed to save area to database: " + errorMsg, e);
            } catch (Exception e) {
                // Откатываем при любой другой ошибке
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        // Ignore
                    }
                }
                throw new StorageException("Unexpected error saving area: " + e.getMessage(), e);
            }
        }
        
        // Если все попытки исчерпаны
        if (lastException != null) {
            throw new StorageException("Failed to save area to database after " + retries + " retries", lastException);
        }
    }
    
    private boolean areaExists(Connection conn, int areaId) throws SQLException {
        String sql = "SELECT 1 FROM areas WHERE id = ? AND deleted = FALSE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, areaId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    private boolean checkIfDeleted(Connection conn, int areaId) throws SQLException {
        String sql = "SELECT 1 FROM areas WHERE id = ? AND deleted = TRUE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, areaId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    private void restoreArea(Connection conn, NArea area) throws SQLException {
        // Если у зоны нет UUID, генерируем его
        if (area.uuid == null || area.uuid.isEmpty()) {
            area.uuid = java.util.UUID.randomUUID().toString();
        }
        
        // Устанавливаем zone_sync из настроек, если не установлен
        if (area.zoneSync == null || area.zoneSync.isEmpty()) {
            Object zoneSyncObj = nurgling.NConfig.get(nurgling.NConfig.Key.syncZoneSync);
            if (zoneSyncObj != null && zoneSyncObj instanceof String) {
                String zoneSync = ((String) zoneSyncObj).trim();
                if (!zoneSync.isEmpty()) {
                    area.zoneSync = zoneSync;
                }
            }
        }
        
        // Восстанавливаем удаленную зону
        String sql = "UPDATE areas SET deleted = FALSE, global_id = ?, name = ?, path = ?, color_r = ?, color_g = ?, " +
                    "color_b = ?, color_a = ?, hide = ?, zone_sync = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, area.uuid != null && !area.uuid.isEmpty() ? area.uuid : null);
            stmt.setString(2, area.name);
            stmt.setString(3, area.path != null ? area.path : "");
            stmt.setInt(4, area.color.getRed());
            stmt.setInt(5, area.color.getGreen());
            stmt.setInt(6, area.color.getBlue());
            stmt.setInt(7, area.color.getAlpha());
            stmt.setBoolean(8, area.hide);
            stmt.setString(9, area.zoneSync != null && !area.zoneSync.isEmpty() ? area.zoneSync : null);
            stmt.setInt(10, area.id);
            stmt.executeUpdate();
        }
        
        // Удаляем старые связанные данные и сохраняем новые
        deleteAreaRelatedData(conn, area.id);
        saveAreaSpaces(conn, area);
        saveAreaInputs(conn, area);
        saveAreaOutputs(conn, area);
        saveAreaSpecialisations(conn, area);
    }
    
    private void insertArea(Connection conn, NArea area) throws SQLException {
        // Получаем следующий ID если не задан
        if (area.id <= 0) {
            area.id = getNextAreaId(conn);
        }
        
        // Устанавливаем zone_sync из настроек, если не установлен
        if (area.zoneSync == null || area.zoneSync.isEmpty()) {
            Object zoneSyncObj = nurgling.NConfig.get(nurgling.NConfig.Key.syncZoneSync);
            if (zoneSyncObj != null && zoneSyncObj instanceof String) {
                String zoneSync = ((String) zoneSyncObj).trim();
                if (!zoneSync.isEmpty()) {
                    area.zoneSync = zoneSync;
                }
            }
        }
        
        // Устанавливаем last_updated если не установлен
        if (area.lastUpdated == 0) {
            area.lastUpdated = System.currentTimeMillis();
        }
        
        // Используем оригинальное lastUpdated из зоны (если есть), иначе CURRENT_TIMESTAMP
        // Это важно для сохранения оригинальной даты при загрузке зон с сервера
        String sql;
        boolean useOriginalTimestamp = area.lastUpdated > 0 && area.synced;
        
        if (useOriginalTimestamp) {
            // Используем оригинальное время из сервера
            sql = "INSERT INTO areas (id, global_id, name, path, color_r, color_g, color_b, color_a, hide, " +
                  "sync_status, sync_version, zone_sync, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'local', 1, ?, ?)";
        } else {
            // Используем текущее время для новых локальных зон
            sql = "INSERT INTO areas (id, global_id, name, path, color_r, color_g, color_b, color_a, hide, " +
                  "sync_status, sync_version, zone_sync, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'local', 1, ?, CURRENT_TIMESTAMP)";
        }
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, area.id);
            stmt.setString(2, area.uuid != null && !area.uuid.isEmpty() ? area.uuid : null);
            stmt.setString(3, area.name);
            stmt.setString(4, area.path != null ? area.path : "");
            stmt.setInt(5, area.color.getRed());
            stmt.setInt(6, area.color.getGreen());
            stmt.setInt(7, area.color.getBlue());
            stmt.setInt(8, area.color.getAlpha());
            stmt.setBoolean(9, area.hide);
            stmt.setString(10, area.zoneSync != null && !area.zoneSync.isEmpty() ? area.zoneSync : null);
            if (useOriginalTimestamp) {
                // Устанавливаем оригинальное время из сервера
                java.sql.Timestamp timestamp = new java.sql.Timestamp(area.lastUpdated);
                stmt.setTimestamp(11, timestamp);
            }
            stmt.executeUpdate();
        }
        
        // Сохраняем связанные данные
        saveAreaSpaces(conn, area);
        saveAreaInputs(conn, area);
        saveAreaOutputs(conn, area);
        saveAreaSpecialisations(conn, area);
    }
    
    private void updateArea(Connection conn, NArea area) throws SQLException {
        // Загружаем текущую версию зоны из БД для сравнения
        NArea dbArea = loadAreaForComparison(conn, area.id);
        boolean hasChanges = dbArea == null || hasAreaChanged(dbArea, area);
        
        
        
        // Если у зоны нет UUID, генерируем его (для старых зон)
        if (area.uuid == null || area.uuid.isEmpty()) {
            // Проверяем, есть ли UUID в БД
            if (dbArea != null && dbArea.uuid != null && !dbArea.uuid.isEmpty()) {
                area.uuid = dbArea.uuid; // Используем существующий UUID из БД
            } else {
                area.uuid = java.util.UUID.randomUUID().toString(); // Генерируем новый
            }
        }
        
        // Устанавливаем zone_sync из настроек, если не установлен
        if (area.zoneSync == null || area.zoneSync.isEmpty()) {
            Object zoneSyncObj = nurgling.NConfig.get(nurgling.NConfig.Key.syncZoneSync);
            if (zoneSyncObj != null && zoneSyncObj instanceof String) {
                String zoneSync = ((String) zoneSyncObj).trim();
                if (!zoneSync.isEmpty()) {
                    area.zoneSync = zoneSync;
                }
            }
        }
        
        // Обновляем updated_at только если зона действительно изменилась
        // Если зона изменилась, обновляем lastUpdated в объекте
        if (hasChanges) {
            // ВАЖНО: Обновляем lastUpdated ПЕРЕД сохранением, чтобы синхронизация увидела изменение
            // Используем текущее время, чтобы гарантировать, что оно будет больше любого предыдущего значения
            area.lastUpdated = System.currentTimeMillis();
            System.out.println("AreaDBStorage: Zone " + area.id + " (" + area.name + ") changed, updating lastUpdated to: " + area.lastUpdated);
        }
        
        // Если зона синхронизирована с сервером и имеет оригинальное lastUpdated,
        // используем его вместо CURRENT_TIMESTAMP (только если зона не изменена)
        boolean useOriginalTimestamp = area.synced && area.lastUpdated > 0 && !hasChanges;
        
        String sql;
        if (hasChanges) {
            // Зона изменена локально - используем явное значение lastUpdated, которое мы установили
            // Это гарантирует, что в БД будет сохранено правильное время
            sql = "UPDATE areas SET global_id = ?, name = ?, path = ?, color_r = ?, color_g = ?, color_b = ?, " +
                  "color_a = ?, hide = ?, zone_sync = ?, updated_at = ? WHERE id = ?";
        } else if (useOriginalTimestamp) {
            // Зона синхронизирована с сервером и не изменена - сохраняем оригинальное время
            sql = "UPDATE areas SET global_id = ?, name = ?, path = ?, color_r = ?, color_g = ?, color_b = ?, " +
                  "color_a = ?, hide = ?, zone_sync = ?, updated_at = ? WHERE id = ?";
        } else {
            // Зона не изменена и не синхронизирована - не обновляем updated_at
            sql = "UPDATE areas SET global_id = ?, name = ?, path = ?, color_r = ?, color_g = ?, color_b = ?, " +
                  "color_a = ?, hide = ?, zone_sync = ? WHERE id = ?";
        }
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            stmt.setString(paramIndex++, area.uuid != null && !area.uuid.isEmpty() ? area.uuid : null);
            stmt.setString(paramIndex++, area.name);
            stmt.setString(paramIndex++, area.path != null ? area.path : "");
            stmt.setInt(paramIndex++, area.color.getRed());
            stmt.setInt(paramIndex++, area.color.getGreen());
            stmt.setInt(paramIndex++, area.color.getBlue());
            stmt.setInt(paramIndex++, area.color.getAlpha());
            stmt.setBoolean(paramIndex++, area.hide);
            stmt.setString(paramIndex++, area.zoneSync != null && !area.zoneSync.isEmpty() ? area.zoneSync : null);
            if (hasChanges) {
                // Устанавливаем явное значение lastUpdated, которое мы установили при изменении
                java.sql.Timestamp timestamp = new java.sql.Timestamp(area.lastUpdated);
                stmt.setTimestamp(paramIndex++, timestamp);
            } else if (useOriginalTimestamp) {
                // Устанавливаем оригинальное время из сервера
                java.sql.Timestamp timestamp = new java.sql.Timestamp(area.lastUpdated);
                stmt.setTimestamp(paramIndex++, timestamp);
            }
            stmt.setInt(paramIndex++, area.id);
            stmt.executeUpdate();
        }
        
        // Если зона изменилась, проверяем, что updated_at действительно обновился в БД
        if (hasChanges) {
            long savedLastUpdated = area.lastUpdated; // Сохраняем значение, которое мы установили
            System.out.println("AreaDBStorage: Zone " + area.id + " (" + area.name + ") - saved with lastUpdated: " + savedLastUpdated);
            
            // Проверяем, что updated_at действительно обновился в БД
            try (PreparedStatement checkStmt = conn.prepareStatement("SELECT updated_at FROM areas WHERE id = ?")) {
                checkStmt.setInt(1, area.id);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        java.sql.Timestamp updatedAt = rs.getTimestamp("updated_at");
                        if (updatedAt != null) {
                            long dbLastUpdated = updatedAt.getTime();
                            // Используем большее значение для надежности
                            area.lastUpdated = Math.max(savedLastUpdated, dbLastUpdated);
                            System.out.println("AreaDBStorage: Zone " + area.id + " (" + area.name + ") - verified: saved=" + savedLastUpdated + 
                                             ", db=" + dbLastUpdated + ", using=" + area.lastUpdated);
                        } else {
                            System.out.println("AreaDBStorage: Zone " + area.id + " (" + area.name + ") - WARNING: updated_at is NULL in DB!");
                        }
                    }
                }
            }
            
            // Удаляем старые связанные данные
            deleteAreaRelatedData(conn, area.id);
            
            // Сохраняем новые связанные данные
            saveAreaSpaces(conn, area);
            saveAreaInputs(conn, area);
            saveAreaOutputs(conn, area);
            saveAreaSpecialisations(conn, area);
        }
    }
    
    /**
     * Загружает зону из БД для сравнения (без полной загрузки связанных данных)
     */
    private NArea loadAreaForComparison(Connection conn, int areaId) throws SQLException {
        String sql = "SELECT name, path, color_r, color_g, color_b, color_a, hide FROM areas WHERE id = ? AND deleted = FALSE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, areaId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    NArea area = new NArea(rs.getString("name"));
                    area.id = areaId;
                    area.path = rs.getString("path");
                    if (rs.wasNull()) area.path = "";
                    
                    int r = rs.getInt("color_r");
                    int g = rs.getInt("color_g");
                    int b = rs.getInt("color_b");
                    int a = rs.getInt("color_a");
                    area.color = new java.awt.Color(r, g, b, a);
                    area.hide = rs.getBoolean("hide");
                    
                    // Инициализируем space перед загрузкой данных
                    if (area.space == null) {
                        area.space = new NArea.Space();
                    }
                    
                    // Инициализируем другие поля
                    if (area.jin == null) {
                        area.jin = new org.json.JSONArray();
                    }
                    if (area.jout == null) {
                        area.jout = new org.json.JSONArray();
                    }
                    if (area.spec == null) {
                        area.spec = new ArrayList<>();
                    }
                    
                    // Загружаем только основные связанные данные для сравнения
                    loadAreaSpaces(conn, area);
                    loadAreaInputs(conn, area);
                    loadAreaOutputs(conn, area);
                    loadAreaSpecialisations(conn, area);
                    
                    return area;
                }
            }
        }
        return null;
    }
    
    /**
     * Сравнивает две зоны и определяет, изменились ли они
     */
    private boolean hasAreaChanged(NArea dbArea, NArea newArea) {
        // Проверка на null
        if (dbArea == null || newArea == null) return true;
        
        // Сравниваем основные поля
        if (!Objects.equals(dbArea.name, newArea.name)) return true;
        if (!Objects.equals(dbArea.path, newArea.path)) return true;
        if (dbArea.color == null || newArea.color == null || 
            dbArea.color.getRGB() != newArea.color.getRGB()) return true;
        if (dbArea.hide != newArea.hide) return true;
        
        // Сравниваем пространственные данные
        if (dbArea.space == null || newArea.space == null) {
            if (dbArea.space != newArea.space) return true;
        } else {
            if (dbArea.space.space == null || newArea.space.space == null) {
                if (dbArea.space.space != newArea.space.space) return true;
            } else {
                if (dbArea.space.space.size() != newArea.space.space.size()) return true;
                for (Map.Entry<Long, NArea.VArea> entry : newArea.space.space.entrySet()) {
                    NArea.VArea dbVArea = dbArea.space.space.get(entry.getKey());
                    if (dbVArea == null) return true;
                    if (dbVArea.area == null || entry.getValue().area == null) {
                        if (dbVArea.area != entry.getValue().area) return true;
                    } else {
                        Area dbAreaObj = dbVArea.area;
                        Area newAreaObj = entry.getValue().area;
                        if (!dbAreaObj.ul.equals(newAreaObj.ul) || !dbAreaObj.br.equals(newAreaObj.br)) {
                            return true;
                        }
                    }
                }
            }
        }
        
        // Сравниваем входы
        if (dbArea.jin == null || newArea.jin == null) {
            if (dbArea.jin != newArea.jin) return true;
        } else if (!compareJSONArrays(dbArea.jin, newArea.jin)) {
            return true;
        }
        
        // Сравниваем выходы
        if (dbArea.jout == null || newArea.jout == null) {
            if (dbArea.jout != newArea.jout) return true;
        } else if (!compareJSONArrays(dbArea.jout, newArea.jout)) {
            return true;
        }
        
        // Сравниваем специализации
        if (dbArea.spec == null || newArea.spec == null) {
            if (dbArea.spec != newArea.spec) return true;
        } else {
            if (dbArea.spec.size() != newArea.spec.size()) return true;
            for (int i = 0; i < newArea.spec.size(); i++) {
                NArea.Specialisation newSpec = newArea.spec.get(i);
                NArea.Specialisation dbSpec = dbArea.spec.get(i);
                if (newSpec == null || dbSpec == null) {
                    if (newSpec != dbSpec) return true;
                } else {
                    if (!Objects.equals(newSpec.name, dbSpec.name) || 
                        !Objects.equals(newSpec.subtype, dbSpec.subtype)) {
                        return true;
                    }
                }
            }
        }
        
        return false; // Зона не изменилась
    }
    
    /**
     * Сравнивает два JSONArray
     */
    private boolean compareJSONArrays(org.json.JSONArray arr1, org.json.JSONArray arr2) {
        if (arr1 == null && arr2 == null) return true;
        if (arr1 == null || arr2 == null) return false;
        if (arr1.length() != arr2.length()) return false;
        
        // Создаем множества для сравнения (порядок не важен)
        Set<String> set1 = new HashSet<>();
        Set<String> set2 = new HashSet<>();
        
        try {
            for (int i = 0; i < arr1.length(); i++) {
                set1.add(arr1.get(i).toString());
            }
            for (int i = 0; i < arr2.length(); i++) {
                set2.add(arr2.get(i).toString());
            }
        } catch (Exception e) {
            // Если ошибка при сравнении, считаем что массивы разные
            return false;
        }
        
        return set1.equals(set2);
    }
    
    private int getNextAreaId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 as next_id FROM areas";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("next_id");
            }
        }
        return 1;
    }
    
    private void saveAreaSpaces(Connection conn, NArea area) throws SQLException {
        // Проверяем и инициализируем space
        if (area.space == null) {
            area.space = new NArea.Space();
        }
        if (area.space.space == null || area.space.space.isEmpty()) {
            return; // Нет пространственных данных для сохранения
        }
        
        String sql = "INSERT INTO area_spaces (area_id, grid_id, begin_x, begin_y, end_x, end_y) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Map.Entry<Long, NArea.VArea> entry : area.space.space.entrySet()) {
                if (entry.getValue() == null || entry.getValue().area == null) {
                    continue; // Пропускаем невалидные записи
                }
                long gridId = entry.getKey();
                Area spaceArea = entry.getValue().area;
                stmt.setInt(1, area.id);
                stmt.setLong(2, gridId);
                stmt.setInt(3, spaceArea.ul.x);
                stmt.setInt(4, spaceArea.ul.y);
                stmt.setInt(5, spaceArea.br.x);
                stmt.setInt(6, spaceArea.br.y);
                stmt.addBatch();
            }
            if (area.space.space.size() > 0) {
                stmt.executeBatch();
            }
        }
    }
    
    private void saveAreaInputs(Connection conn, NArea area) throws SQLException {
        if (area.jin == null || area.jin.length() == 0) {
            return; // Нет входов для сохранения
        }
        
        String sql = "INSERT INTO area_inputs (area_id, name, type, icon_data) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < area.jin.length(); i++) {
                try {
                    JSONObject input = area.jin.getJSONObject(i);
                    stmt.setInt(1, area.id);
                    stmt.setString(2, input.getString("name"));
                    if (input.has("type")) {
                        stmt.setString(3, input.getString("type"));
                    } else {
                        stmt.setNull(3, Types.VARCHAR);
                    }
                    // Сохраняем данные об изображении (layer или static)
                    String iconData = null;
                    if (input.has("layer") || input.has("static")) {
                        JSONObject iconJson = new JSONObject();
                        if (input.has("layer")) {
                            iconJson.put("layer", input.get("layer"));
                        }
                        if (input.has("static")) {
                            iconJson.put("static", input.get("static"));
                        }
                        iconData = iconJson.toString();
                    }
                    if (iconData != null) {
                        stmt.setString(4, iconData);
                    } else {
                        stmt.setNull(4, Types.VARCHAR);
                    }
                    stmt.addBatch();
                } catch (Exception e) {
                    // Пропускаем невалидные записи
                    continue;
                }
            }
            stmt.executeBatch();
        }
    }
    
    private void saveAreaOutputs(Connection conn, NArea area) throws SQLException {
        if (area.jout == null || area.jout.length() == 0) {
            return; // Нет выходов для сохранения
        }
        
        String sql = "INSERT INTO area_outputs (area_id, name, type, th, icon_data) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < area.jout.length(); i++) {
                try {
                    JSONObject output = area.jout.getJSONObject(i);
                    stmt.setInt(1, area.id);
                    stmt.setString(2, output.getString("name"));
                    if (output.has("type")) {
                        stmt.setString(3, output.getString("type"));
                    } else {
                        stmt.setNull(3, Types.VARCHAR);
                    }
                    if (output.has("th")) {
                        stmt.setInt(4, output.getInt("th"));
                    } else {
                        stmt.setNull(4, Types.INTEGER);
                    }
                    // Сохраняем данные об изображении (layer или static)
                    String iconData = null;
                    if (output.has("layer") || output.has("static")) {
                        JSONObject iconJson = new JSONObject();
                        if (output.has("layer")) {
                            iconJson.put("layer", output.get("layer"));
                        }
                        if (output.has("static")) {
                            iconJson.put("static", output.get("static"));
                        }
                        iconData = iconJson.toString();
                    }
                    if (iconData != null) {
                        stmt.setString(5, iconData);
                    } else {
                        stmt.setNull(5, Types.VARCHAR);
                    }
                    stmt.addBatch();
                } catch (Exception e) {
                    // Пропускаем невалидные записи
                    continue;
                }
            }
            stmt.executeBatch();
        }
    }
    
    private void saveAreaSpecialisations(Connection conn, NArea area) throws SQLException {
        if (area.spec == null || area.spec.isEmpty()) {
            return; // Нет специализаций для сохранения
        }
        
        String sql = "INSERT INTO area_specialisations (area_id, name, subtype) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (NArea.Specialisation spec : area.spec) {
                if (spec == null || spec.name == null) {
                    continue; // Пропускаем невалидные записи
                }
                stmt.setInt(1, area.id);
                stmt.setString(2, spec.name);
                if (spec.subtype != null) {
                    stmt.setString(3, spec.subtype);
                } else {
                    stmt.setNull(3, Types.VARCHAR);
                }
                stmt.addBatch();
            }
            if (!area.spec.isEmpty()) {
                stmt.executeBatch();
            }
        }
    }
    
    private void deleteAreaRelatedData(Connection conn, int areaId) throws SQLException {
        String[] tables = {"area_spaces", "area_inputs", "area_outputs", "area_specialisations"};
        for (String table : tables) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + table + " WHERE area_id = ?")) {
                stmt.setInt(1, areaId);
                stmt.executeUpdate();
            }
        }
    }
    
    @Override
    public void deleteArea(int areaId) throws StorageException {
        if (!isAvailable()) {
            throw new StorageException("Database connection is not available");
        }
        
        Connection conn = null;
        try {
            conn = poolManager.getConnection();
            
            // ВАЖНО: Обновляем lastUpdated перед удалением, чтобы синхронизация увидела изменение
            long currentTime = System.currentTimeMillis();
            java.sql.Timestamp timestamp = new java.sql.Timestamp(currentTime);
            
            String sql = "UPDATE areas SET deleted = TRUE, updated_at = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, timestamp);
                stmt.setInt(2, areaId);
                int rowsUpdated = stmt.executeUpdate();
                
                if (rowsUpdated > 0) {
                    System.out.println("AreaDBStorage: Zone " + areaId + " deleted, updated_at set to: " + currentTime);
                } else {
                    System.out.println("AreaDBStorage: WARNING - Zone " + areaId + " not found for deletion");
                }
            }
            conn.commit();
        } catch (SQLException e) {
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.rollback();
                }
            } catch (SQLException rollbackEx) {
                // Ignore
            }
            throw new StorageException("Failed to delete area from database", e);
        }
    }
    
    @Override
    public NArea getArea(int areaId) throws StorageException {
        if (!isAvailable()) {
            throw new StorageException("Database connection is not available");
        }
        
        try {
            Connection conn = poolManager.getConnection();
            // ВАЖНО: Загружаем также global_id (UUID) и zone_sync для синхронизации
            String sql = "SELECT id, global_id, name, path, color_r, color_g, color_b, color_a, hide, zone_sync, updated_at " +
                        "FROM areas WHERE id = ? AND deleted = FALSE";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, areaId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        NArea area = new NArea(rs.getString("name"));
                        area.id = areaId;
                        area.path = rs.getString("path");
                        if (rs.wasNull()) area.path = "";
                        
                        // Загружаем UUID (global_id)
                        String globalId = rs.getString("global_id");
                        if (globalId != null && !globalId.isEmpty()) {
                            area.uuid = globalId;
                        } else {
                            System.out.println("AreaDBStorage: WARNING - Zone " + areaId + " has no UUID in database");
                        }
                        
                        // Загружаем zone_sync
                        String zoneSync = rs.getString("zone_sync");
                        if (zoneSync != null && !zoneSync.isEmpty()) {
                            area.zoneSync = zoneSync;
                        }
                        
                        // Загружаем last_updated
                        java.sql.Timestamp updatedAt = rs.getTimestamp("updated_at");
                        if (updatedAt != null) {
                            area.lastUpdated = updatedAt.getTime();
                        }
                        
                        System.out.println("AreaDBStorage: Loaded zone " + areaId + " (" + area.name + ") - UUID: " + 
                                         (area.uuid != null ? area.uuid : "null") + ", zone_sync: " + 
                                         (area.zoneSync != null ? area.zoneSync : "null"));
                        
                        int r = rs.getInt("color_r");
                        int g = rs.getInt("color_g");
                        int b = rs.getInt("color_b");
                        int a = rs.getInt("color_a");
                        area.color = new java.awt.Color(r, g, b, a);
                        area.hide = rs.getBoolean("hide");
                        
                        // Инициализируем space перед загрузкой данных
                        if (area.space == null) {
                            area.space = new NArea.Space();
                        }
                        
                        loadAreaSpaces(conn, area);
                        loadAreaInputs(conn, area);
                        loadAreaOutputs(conn, area);
                        loadAreaSpecialisations(conn, area);
                        
                        return area;
                    }
                }
            }
            
            return null;
        } catch (SQLException e) {
            throw new StorageException("Failed to get area from database", e);
        }
    }
}

