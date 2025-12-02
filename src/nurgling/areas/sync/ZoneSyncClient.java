package nurgling.areas.sync;

import nurgling.areas.NArea;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * HTTP клиент для синхронизации зон с сервером.
 * Обрабатывает защиту от дублей и конфликтов.
 */
public class ZoneSyncClient {
    private String serverUrl;
    private String zoneSync; // Идентификатор мира/сервера
    private int connectTimeout = 10000; // 10 секунд
    private int readTimeout = 30000; // 30 секунд
    private int maxRetries = 3;
    private long retryDelayMs = 1000;
    
    // Смещение времени между клиентом и сервером (в миллисекундах)
    // Положительное значение означает, что сервер впереди клиента
    private Long timeOffset = null; // null означает, что смещение еще не вычислено
    
    public ZoneSyncClient(String serverUrl, String zoneSync) {
        this.serverUrl = serverUrl != null ? serverUrl.trim().replaceAll("/+$", "") : null;
        this.zoneSync = zoneSync;
    }
    
    /**
     * Проверяет доступность сервера
     */
    public boolean checkHealth() {
        if (serverUrl == null || serverUrl.isEmpty()) {
            return false;
        }
        
        try {
            URL url = URI.create(serverUrl + "/health").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            
            int responseCode = conn.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Синхронизирует время с сервером
     * Вычисляет смещение между временем клиента и сервера
     */
    public void syncTime() {
        if (serverUrl == null || serverUrl.isEmpty()) {
            return;
        }
        
        try {
            long clientTimeBefore = System.currentTimeMillis();
            URL url = URI.create(serverUrl + "/time").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JSONObject json = new JSONObject(response.toString());
                    if (json.has("server_time")) {
                        String serverTimeStr = json.getString("server_time");
                        Instant instant = Instant.parse(serverTimeStr);
                        long serverTime = instant.toEpochMilli();
                        
                        long clientTimeAfter = System.currentTimeMillis();
                        // Учитываем задержку сети - берем среднее время запроса
                        long networkDelay = (clientTimeAfter - clientTimeBefore) / 2;
                        long estimatedServerTime = serverTime + networkDelay;
                        
                        timeOffset = estimatedServerTime - clientTimeAfter;
                        System.out.println("ZoneSyncClient: Time synchronized. Offset: " + timeOffset + " ms " +
                                         "(server is " + (timeOffset > 0 ? "ahead" : "behind") + " by " + 
                                         Math.abs(timeOffset) + " ms, network delay: " + networkDelay + " ms)");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ZoneSyncClient: Failed to sync time with server: " + e.getMessage());
        }
    }
    
    /**
     * Отправляет зону на сервер (с защитой от дублей и конфликтов)
     * @return true если успешно отправлено, false если сервер новее (конфликт)
     */
    public boolean pushZone(NArea area) {
        if (serverUrl == null || serverUrl.isEmpty()) {
            return false;
        }
        
        // Генерируем UUID если его нет
        if (area.uuid == null || area.uuid.isEmpty()) {
            area.uuid = UUID.randomUUID().toString();
        }
        
        // Устанавливаем zone_sync если его нет
        if (area.zoneSync == null || area.zoneSync.isEmpty()) {
            area.zoneSync = this.zoneSync;
        }
        
        // Обновляем last_updated с учетом смещения времени сервера
        // Если смещение еще не вычислено, используем локальное время
        // Сервер все равно установит свое время и вернет его в ответе
        long clientTime = System.currentTimeMillis();
        if (timeOffset != null) {
            // Используем серверное время (приблизительно)
            area.lastUpdated = clientTime + timeOffset;
        } else {
            area.lastUpdated = clientTime;
        }
        
        JSONObject zoneJson = areaToServerJson(area);
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                URL url = URI.create(serverUrl + "/zones").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(connectTimeout);
                conn.setReadTimeout(readTimeout);
                
                // Отправляем данные
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] data = zoneJson.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(data);
                }
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200 || responseCode == 201) {
                    // Успешно отправлено
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        
                        // Парсим ответ для получения обновленного last_updated
                        JSONObject responseJson = new JSONObject(response.toString());
                        if (responseJson.has("last_updated")) {
                            // Сервер вернул обновленное время - используем его
                            String serverTimeStr = responseJson.getString("last_updated");
                            Instant instant = Instant.parse(serverTimeStr);
                            long serverTime = instant.toEpochMilli();
                            area.lastUpdated = serverTime;
                            
                            // Обновляем смещение времени
                            long currentClientTime = System.currentTimeMillis();
                            timeOffset = serverTime - currentClientTime;
                            System.out.println("ZoneSyncClient: Updated time offset: " + timeOffset + " ms (server is " + 
                                             (timeOffset > 0 ? "ahead" : "behind") + " by " + Math.abs(timeOffset) + " ms)");
                        } else if (responseJson.has("server_last_updated")) {
                            // Альтернативное поле для серверного времени
                            String serverTimeStr = responseJson.getString("server_last_updated");
                            Instant instant = Instant.parse(serverTimeStr);
                            long serverTime = instant.toEpochMilli();
                            area.lastUpdated = serverTime;
                            
                            // Обновляем смещение времени
                            long currentClientTime = System.currentTimeMillis();
                            timeOffset = serverTime - currentClientTime;
                        }
                    }
                    
                    area.synced = true;
                    return true;
                } else if (responseCode == 400) {
                    // Ошибка валидации - не повторяем
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder error = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            error.append(line);
                        }
                        System.err.println("ZoneSyncClient: Validation error: " + error.toString());
                    }
                    return false;
                } else {
                    // Другие ошибки - повторяем
                    if (attempt < maxRetries - 1) {
                        Thread.sleep(retryDelayMs * (attempt + 1));
                        continue;
                    }
                    System.err.println("ZoneSyncClient: Failed to push zone " + area.name + 
                                     " (response code: " + responseCode + ")");
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                if (attempt < maxRetries - 1) {
                    try {
                        Thread.sleep(retryDelayMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    continue;
                }
                System.err.println("ZoneSyncClient: Error pushing zone " + area.name + ": " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Получает зоны с сервера (только обновленные после указанной даты)
     */
    public List<NArea> pullZones(long updatedAfter) {
        if (serverUrl == null || serverUrl.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<NArea> zones = new ArrayList<>();
        
        try {
            String urlStr = serverUrl + "/zones?zone_sync=" + 
                           java.net.URLEncoder.encode(zoneSync, StandardCharsets.UTF_8.toString());
            
            if (updatedAfter > 0) {
                Instant instant = Instant.ofEpochMilli(updatedAfter);
                urlStr += "&updated_after=" + 
                         java.net.URLEncoder.encode(instant.toString(), StandardCharsets.UTF_8.toString());
            }
            
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JSONArray zonesJson = new JSONArray(response.toString());
                    for (int i = 0; i < zonesJson.length(); i++) {
                        JSONObject zoneJson = zonesJson.getJSONObject(i);
                        NArea area = serverJsonToArea(zoneJson);
                        if (area != null) {
                            zones.add(area);
                        }
                    }
                }
            } else {
                System.err.println("ZoneSyncClient: Failed to pull zones (response code: " + responseCode + ")");
            }
        } catch (Exception e) {
            System.err.println("ZoneSyncClient: Error pulling zones: " + e.getMessage());
            e.printStackTrace();
        }
        
        return zones;
    }
    
    /**
     * Удаляет зону на сервере (soft delete)
     */
    public boolean deleteZone(String uuid) {
        if (serverUrl == null || serverUrl.isEmpty() || uuid == null || uuid.isEmpty()) {
            return false;
        }
        
        try {
            String urlStr = serverUrl + "/zones/" + 
                          java.net.URLEncoder.encode(uuid, StandardCharsets.UTF_8.toString()) +
                          "?zone_sync=" + 
                          java.net.URLEncoder.encode(zoneSync, StandardCharsets.UTF_8.toString());
            
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            
            int responseCode = conn.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            System.err.println("ZoneSyncClient: Error deleting zone " + uuid + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Преобразует NArea в JSON для отправки на сервер
     */
    private JSONObject areaToServerJson(NArea area) {
        JSONObject json = new JSONObject();
        json.put("uuid", area.uuid);
        json.put("name", area.name);
        json.put("path", area.path != null ? area.path : "");
        
        // Color
        JSONObject color = new JSONObject();
        color.put("r", area.color.getRed());
        color.put("g", area.color.getGreen());
        color.put("b", area.color.getBlue());
        color.put("a", area.color.getAlpha());
        json.put("color", color);
        
        // Space
        JSONArray spaceArray = new JSONArray();
        if (area.space != null && area.space.space != null) {
            for (Long gridId : area.space.space.keySet()) {
                NArea.VArea vArea = area.space.space.get(gridId);
                if (vArea != null && vArea.area != null) {
                    JSONObject spaceObj = new JSONObject();
                    spaceObj.put("grid_id", gridId);
                    spaceObj.put("begin_x", vArea.area.ul.x);
                    spaceObj.put("begin_y", vArea.area.ul.y);
                    spaceObj.put("end_x", vArea.area.br.x);
                    spaceObj.put("end_y", vArea.area.br.y);
                    spaceArray.put(spaceObj);
                }
            }
        }
        json.put("space", spaceArray);
        
        // Spec
        JSONArray specArray = new JSONArray();
        if (area.spec != null) {
            for (NArea.Specialisation spec : area.spec) {
                JSONObject specObj = new JSONObject();
                specObj.put("name", spec.name);
                if (spec.subtype != null) {
                    specObj.put("subtype", spec.subtype);
                }
                specArray.put(specObj);
            }
        }
        json.put("spec", specArray);
        
        // Inputs
        json.put("in", area.jin != null ? area.jin : new JSONArray());
        
        // Outputs
        json.put("out", area.jout != null ? area.jout : new JSONArray());
        
        // Timestamp
        Instant instant = Instant.ofEpochMilli(area.lastUpdated);
        json.put("last_updated", instant.toString());
        
        // Deleted flag
        json.put("deleted", false);
        
        // Zone sync
        json.put("zone_sync", area.zoneSync);
        
        return json;
    }
    
    /**
     * Преобразует JSON с сервера в NArea
     */
    private NArea serverJsonToArea(JSONObject json) {
        try {
            NArea area = new NArea(json.getString("name"));
            
            // UUID
            if (json.has("uuid")) {
                area.uuid = json.getString("uuid");
            }
            
            // Zone sync
            if (json.has("zone_sync")) {
                area.zoneSync = json.getString("zone_sync");
            }
            
            // Last updated
            if (json.has("last_updated")) {
                String timeStr = json.getString("last_updated");
                Instant instant = Instant.parse(timeStr);
                area.lastUpdated = instant.toEpochMilli();
            }
            
            // Path
            if (json.has("path")) {
                area.path = json.getString("path");
            }
            
            // Color
            if (json.has("color")) {
                JSONObject color = json.getJSONObject("color");
                area.color = new java.awt.Color(
                    color.getInt("r"),
                    color.getInt("g"),
                    color.getInt("b"),
                    color.getInt("a")
                );
            }
            
            // Space
            if (json.has("space") && json.get("space") instanceof JSONArray) {
                // Парсим space из JSON (пока не используется, будет загружено из БД при необходимости)
                @SuppressWarnings("unused")
                JSONArray spaceArray = json.getJSONArray("space");
                if (area.space == null) {
                    area.space = new NArea.Space();
                }
                // Space будет загружено из БД при необходимости
                // Здесь мы только сохраняем данные для последующей загрузки
            }
            
            // Spec
            if (json.has("spec") && json.get("spec") instanceof JSONArray) {
                JSONArray specArray = json.getJSONArray("spec");
                area.spec = new ArrayList<>();
                for (int i = 0; i < specArray.length(); i++) {
                    JSONObject specObj = specArray.getJSONObject(i);
                    String name = specObj.getString("name");
                    String subtype = specObj.has("subtype") ? specObj.getString("subtype") : null;
                    area.spec.add(new NArea.Specialisation(name, subtype));
                }
            }
            
            // Inputs/Outputs
            if (json.has("in")) {
                area.jin = json.getJSONArray("in");
            }
            if (json.has("out")) {
                area.jout = json.getJSONArray("out");
            }
            
            area.synced = true;
            
            return area;
        } catch (Exception e) {
            System.err.println("ZoneSyncClient: Error parsing zone from server: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl != null ? serverUrl.trim().replaceAll("/+$", "") : null;
    }
    
    public void setZoneSync(String zoneSync) {
        this.zoneSync = zoneSync;
    }
}

