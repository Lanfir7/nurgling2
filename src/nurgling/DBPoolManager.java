package nurgling;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DBPoolManager {
    private final ThreadPoolExecutor executorService;
    private final BlockingQueue<Runnable> taskQueue;
    private Connection connection = null;
    private boolean isPostgres;
    private String currentUrl;
    private String currentUser;
    private String currentPass;
    
    // Ограничение очереди задач
    private static final int MAX_QUEUE_SIZE = 50;

    public DBPoolManager(int poolSize) {
        // Создаем очередь с ограничением размера
        this.taskQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
        
        // Создаем ThreadPoolExecutor с кастомным RejectedExecutionHandler
        // который отменяет старые задачи записи при переполнении очереди
        this.executorService = new ThreadPoolExecutor(
            poolSize, poolSize,
            0L, TimeUnit.MILLISECONDS,
            taskQueue,
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "DBPoolManager-thread-" + threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            },
            new RejectedExecutionHandler() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                    // Определяем тип задачи по строковому представлению
                    String taskStr = r.toString();
                    boolean isReadTask = taskStr.contains("RecipeHashFetcher");
                    
                    System.err.println("DBPoolManager: Queue full (" + taskQueue.size() + "), rejected task (isRead: " + isReadTask + ")");
                    
                    // При переполнении очереди пытаемся удалить старые задачи записи
                    // чтобы освободить место для задач чтения
                    if (isReadTask) {
                        // Для задач чтения удаляем задачи записи из очереди
                        int removedCount = 0;
                        for (int i = 0; i < 5 && !taskQueue.isEmpty(); i++) {
                            Runnable removed = taskQueue.poll();
                            if (removed != null) {
                                removedCount++;
                            }
                        }
                        if (removedCount > 0) {
                            System.out.println("DBPoolManager: Removed " + removedCount + " write tasks to make room for RecipeHashFetcher");
                        }
                        // Пробуем добавить задачу чтения
                        try {
                            executor.execute(r);
                        } catch (RejectedExecutionException e) {
                            System.err.println("DBPoolManager: Still rejected after cleanup");
                        }
                    } else {
                        // Для задач записи просто пропускаем
                        System.out.println("DBPoolManager: Skipping write task due to full queue");
                    }
                }
            }
        );
        
        this.isPostgres = (Boolean) NConfig.get(NConfig.Key.postgres);
        updateConnection();
    }

    private synchronized void updateConnection() {
        try {
            // Закрываем предыдущее соединение, если оно есть
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            if ((Boolean) NConfig.get(NConfig.Key.ndbenable)) {
                if ((Boolean) NConfig.get(NConfig.Key.postgres)) {
                    // PostgreSQL соединение
                    currentUrl = "jdbc:postgresql://" + NConfig.get(NConfig.Key.serverNode) + "/nurgling_db?sql_mode=ANSI";
                    currentUser = (String) NConfig.get(NConfig.Key.serverUser);
                    currentPass = (String) NConfig.get(NConfig.Key.serverPass);
                    System.out.println("DBPoolManager: Connecting to PostgreSQL: " + currentUrl);
                    connection = DriverManager.getConnection(currentUrl, currentUser, currentPass);
                } else {
                    // SQLite соединение с настройками для лучшей параллельной работы
                    currentUrl = "jdbc:sqlite:" + NConfig.get(NConfig.Key.dbFilePath);
                    System.out.println("DBPoolManager: Connecting to SQLite cookbook database: " + currentUrl);
                    connection = DriverManager.getConnection(currentUrl);
                    
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
                        System.err.println("Warning: Failed to set SQLite PRAGMA settings: " + pragmaEx.getMessage());
                    }
                }
                connection.setAutoCommit(false);
                
                // Run migrations after establishing connection
                try {
                    DBMigrationManager migrationManager = new DBMigrationManager(connection);
                    migrationManager.runMigrations();
                } catch (SQLException migrationEx) {
                    System.err.println("Failed to run database migrations: " + migrationEx.getMessage());
                    migrationEx.printStackTrace();
                    // Don't close connection, migrations might have partially succeeded
                }
            }
        } catch (SQLException e) {
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

    public Future<?> submitTask(Runnable task) {
        String taskName = task.getClass().getSimpleName();
        int queueSize = taskQueue.size();
        System.out.println("DBPoolManager: Submitting task: " + taskName + " (queue size: " + queueSize + ")");
        
        // Если очередь почти полна и это задача записи, пропускаем её
        if (queueSize >= MAX_QUEUE_SIZE - 5 && taskName.contains("NGItemWriter")) {
            System.out.println("DBPoolManager: Queue almost full, skipping write task: " + taskName);
            return null;
        }
        
        return executorService.submit(() -> {
            try {
                System.out.println("DBPoolManager: Starting task: " + taskName);
                task.run();
                System.out.println("DBPoolManager: Completed task: " + taskName);
            } catch (Exception e) {
                // Проверяем, не было ли прерывания
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("DBPoolManager: Task " + taskName + " was interrupted");
                } else {
                    System.err.println("DBPoolManager: Error in task " + taskName + ": " + e.getMessage());
                    e.printStackTrace();
                }
            } finally {
                // Освобождаем ресурсы, если необходимо
            }
        });
    }

    public synchronized void shutdown() {
        executorService.shutdown();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}