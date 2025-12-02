package nurgling.areas.db;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nurgling.areas.storage.DatabaseConnectionManager;

/**
 * Менеджер пула соединений для БД зон.
 * Использует отдельную SQLite БД "Areas.db" в корневой папке игры.
 */
public class AreasDBPoolManager implements DatabaseConnectionManager {
    private final ExecutorService executorService;
    private Connection connection = null;
    private final String dbPath;
    
    public AreasDBPoolManager() {
        this.executorService = Executors.newFixedThreadPool(1);
        // Получаем корневую папку игры (откуда запускается)
        String gameRoot = System.getProperty("user.dir");
        if (gameRoot == null || gameRoot.isEmpty()) {
            // Fallback на текущую директорию
            gameRoot = new File(".").getAbsolutePath();
        }
        // Путь к БД зон в корне игры
        this.dbPath = new File(gameRoot, "Areas.db").getAbsolutePath();
        System.out.println("AreasDBPoolManager: Initializing database at: " + dbPath);
        updateConnection();
    }
    
    /**
     * Получает путь к БД зон
     */
    public String getDbPath() {
        return dbPath;
    }
    
    private synchronized void updateConnection() {
        try {
            // Закрываем предыдущее соединение, если оно есть
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            
            // Создаем директорию для БД, если её нет
            File dbFile = new File(dbPath);
            File dbDir = dbFile.getParentFile();
            if (dbDir != null && !dbDir.exists()) {
                try {
                    Files.createDirectories(Paths.get(dbDir.getAbsolutePath()));
                } catch (Exception e) {
                    System.err.println("AreasDBPoolManager: Failed to create database directory: " + e.getMessage());
                }
            }
            
            // Создаем БД, если её нет
            if (!dbFile.exists()) {
                try {
                    dbFile.createNewFile();
                    System.out.println("AreasDBPoolManager: Created new database file: " + dbPath);
                } catch (Exception e) {
                    System.err.println("AreasDBPoolManager: Failed to create database file: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // SQLite соединение с настройками для лучшей параллельной работы
            String url = "jdbc:sqlite:" + dbPath;
            connection = DriverManager.getConnection(url);
            
            // Настройки SQLite для лучшей параллельной работы
            try (java.sql.Statement stmt = connection.createStatement()) {
                // Включаем WAL mode для лучшей параллельной работы
                stmt.execute("PRAGMA journal_mode=WAL");
                // Увеличиваем таймаут для ожидания блокировки
                stmt.execute("PRAGMA busy_timeout=5000");
                // Оптимизируем синхронизацию
                stmt.execute("PRAGMA synchronous=NORMAL");
            } catch (SQLException pragmaEx) {
                // Игнорируем ошибки PRAGMA (могут быть на старых версиях SQLite)
                System.err.println("AreasDBPoolManager: Warning: Failed to set SQLite PRAGMA settings: " + pragmaEx.getMessage());
            }
            
            connection.setAutoCommit(false);
            
            // Запускаем миграции после установки соединения
            // Для БД зон всегда используется SQLite, поэтому передаем false для isPostgres
            try {
                AreasDBMigrationManager migrationManager = new AreasDBMigrationManager(connection);
                migrationManager.runMigrations();
            } catch (SQLException migrationEx) {
                System.err.println("AreasDBPoolManager: Failed to run database migrations: " + migrationEx.getMessage());
                migrationEx.printStackTrace();
                // Не закрываем соединение, миграции могли частично выполниться
            }
        } catch (SQLException e) {
            System.err.println("AreasDBPoolManager: Failed to connect to database: " + e.getMessage());
            e.printStackTrace();
            connection = null;
        }
    }
    
    public synchronized void reconnect() {
        updateConnection();
    }
    
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            updateConnection();
        }
        return connection;
    }
    
    public synchronized boolean isAvailable() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
    
    public synchronized void shutdown() {
        executorService.shutdown();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("AreasDBPoolManager: Error closing connection: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

