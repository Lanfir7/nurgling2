package nurgling;

import nurgling.db.SimpleConnectionPool;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Database pool manager that provides thread-safe connection pooling.
 * Each database task should borrow a connection, use it, and return it.
 */
public class DBPoolManager {
    private final ThreadPoolExecutor executorService;
    private final BlockingQueue<Runnable> taskQueue;
    private SimpleConnectionPool connectionPool;
    private volatile boolean initialized = false;

    // Ограничение очереди задач
    private static final int MAX_QUEUE_SIZE = 50;
    
    // Pool sizes: PostgreSQL can handle multiple concurrent connections,
    // SQLite should use 1 (doesn't support concurrent writes)
    private static final int POSTGRES_POOL_SIZE = 5;
    private static final int SQLITE_POOL_SIZE = 1;

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
        
        // Initialize connection pool
        initializePool();
    }

    /**
     * Initialize the connection pool based on database type.
     */
    private synchronized void initializePool() {
        if (initialized) {
            return;
        }

        if (!(Boolean) NConfig.get(NConfig.Key.ndbenable)) {
            return;
        }

        boolean isPostgres = (Boolean) NConfig.get(NConfig.Key.postgres);
        int poolSize = isPostgres ? POSTGRES_POOL_SIZE : SQLITE_POOL_SIZE;

        connectionPool = new SimpleConnectionPool(poolSize);
        initialized = true;

        // Run migrations on first connection
        runMigrations();
    }

    /**
     * Run database migrations using a borrowed connection.
     */
    private void runMigrations() {
        Connection conn = null;
        try {
            conn = connectionPool.borrowConnection();
            if (conn != null) {
                DBMigrationManager migrationManager = new DBMigrationManager(conn);
                migrationManager.runMigrations();
                conn.commit();
            }
        } catch (SQLException e) {
            System.err.println("Failed to run database migrations: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignore) {
                }
            }
        } finally {
            if (conn != null) {
                connectionPool.returnConnection(conn);
            }
        }
    }

    /**
     * Borrow a connection from the pool.
     * The caller MUST return the connection using returnConnection() when done.
     *
     * @return A database connection, or null if unavailable
     */
    public Connection getConnection() {
        if (!initialized || connectionPool == null) {
            return null;
        }
        return connectionPool.borrowConnection();
    }

    /**
     * Return a connection to the pool.
     * Always call this in a finally block after using a connection.
     *
     * @param conn The connection to return
     */
    public void returnConnection(Connection conn) {
        if (connectionPool != null && conn != null) {
            connectionPool.returnConnection(conn);
        }
    }

    /**
     * Check if the database is ready to accept connections.
     *
     * @return true if database is initialized and connections are available
     */
    public boolean isConnectionReady() {
        return initialized && connectionPool != null && connectionPool.isReady();
    }

    /**
     * Submit a task for execution.
     */
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

    /**
     * Reconnect to the database (recreate the pool).
     */
    public synchronized void reconnect() {
        if (connectionPool != null) {
            connectionPool.shutdown();
        }
        initialized = false;
        connectionPool = null;
        initializePool();
    }

    /**
     * Shutdown the pool manager and release all resources.
     */
    public synchronized void shutdown() {
        executorService.shutdown();
        if (connectionPool != null) {
            connectionPool.shutdown();
            connectionPool = null;
        }
        initialized = false;
    }
}
