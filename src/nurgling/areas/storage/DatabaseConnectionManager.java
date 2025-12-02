package nurgling.areas.storage;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Интерфейс для менеджеров соединений с БД
 */
public interface DatabaseConnectionManager {
    /**
     * Получает соединение с БД
     */
    Connection getConnection() throws SQLException;
    
    /**
     * Проверяет доступность БД
     */
    boolean isAvailable();
}

