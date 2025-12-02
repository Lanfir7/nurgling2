package nurgling.areas.storage;

import nurgling.areas.NArea;
import java.util.Map;

/**
 * Интерфейс для хранения зон.
 * Позволяет использовать разные реализации (БД, JSON, и т.д.)
 */
public interface AreaStorage {
    /**
     * Загружает все зоны из хранилища
     * @return Map с зонами (id -> NArea)
     * @throws StorageException при ошибке загрузки
     */
    Map<Integer, NArea> loadAllAreas() throws StorageException;
    
    /**
     * Сохраняет зону в хранилище
     * @param area зона для сохранения
     * @throws StorageException при ошибке сохранения
     */
    void saveArea(NArea area) throws StorageException;
    
    /**
     * Удаляет зону из хранилища
     * @param areaId ID зоны для удаления
     * @throws StorageException при ошибке удаления
     */
    void deleteArea(int areaId) throws StorageException;
    
    /**
     * Получает зону по ID
     * @param areaId ID зоны
     * @return зона или null если не найдена
     * @throws StorageException при ошибке
     */
    NArea getArea(int areaId) throws StorageException;
    
    /**
     * Проверяет доступность хранилища
     * @return true если хранилище доступно
     */
    boolean isAvailable();
    
    /**
     * Исключение для ошибок хранилища
     */
    class StorageException extends Exception {
        public StorageException(String message) {
            super(message);
        }
        
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

