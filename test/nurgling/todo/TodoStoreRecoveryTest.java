package nurgling.todo;

import org.junit.jupiter.api.Test;
import nurgling.db.service.TodoService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TodoStoreRecoveryTest {
    @Test
    void dbPendingWorkSurvivesSwitchToLocalAndRestart() throws Exception {
        Path file = Files.createTempFile("todo-recovery", ".json");
        try {
            AtomicReference<TodoStore.Mode> mode = new AtomicReference<>(TodoStore.Mode.DB);
            TodoStore store = store(file, mode);
            store.tick();
            assertTrue(store.addItem(0, "Pending shared task") != null);
            assertTrue(store.pendingCount() > 0);

            mode.set(TodoStore.Mode.LOCAL);
            store.tick();

            assertEquals(TodoStore.Mode.LOCAL, store.mode());
            assertEquals(0, store.pendingCount());
            assertTrue(hasTitle(store, "Pending shared task"));

            TodoStore restarted = store(file, new AtomicReference<>(TodoStore.Mode.LOCAL));
            restarted.tick();
            assertTrue(hasTitle(restarted, "Pending shared task"));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".bak"));
        }
    }

    @Test
    void permissionDeniedPersistsPendingWorkForLocalRecovery() throws Exception {
        Path file = Files.createTempFile("todo-permission", ".json");
        try {
            AtomicReference<TodoStore.Mode> mode = new AtomicReference<>(TodoStore.Mode.DB);
            TodoStore store = store(file, mode);
            store.tick();
            assertTrue(store.addItem(0, "Denied shared task") != null);

            store.syncOnce(new TodoService(null) {
                @Override
                public boolean canWrite() throws SQLException {
                    throw new SQLException("guest", "42501");
                }
            });

            assertTrue(store.readOnly());
            assertEquals(0, store.pendingCount());
            TodoStore recovered = store(file, new AtomicReference<>(TodoStore.Mode.LOCAL));
            recovered.tick();
            assertTrue(hasTitle(recovered, "Denied shared task"));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".bak"));
        }
    }

    @Test
    void failedRecoveryWriteKeepsPendingWorkAndDbState() throws Exception {
        Path directory = Files.createTempDirectory("todo-recovery-readonly");
        try {
            AtomicReference<TodoStore.Mode> mode = new AtomicReference<>(TodoStore.Mode.DB);
            TodoStore store = store(directory, mode);
            store.tick();
            assertTrue(store.addItem(0, "Do not lose this") != null);

            mode.set(TodoStore.Mode.LOCAL);
            store.tick();

            assertEquals(TodoStore.Mode.DB, store.mode());
            assertTrue(store.pendingCount() > 0);
            assertFalse(store.readOnly());
        } finally {
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void pendingDeleteBecomesAnExplicitRecoveryTask() throws Exception {
        Path file = Files.createTempFile("todo-delete-recovery", ".json");
        try {
            AtomicReference<TodoStore.Mode> mode = new AtomicReference<>(TodoStore.Mode.DB);
            TodoStore store = store(file, mode);
            store.tick();
            TodoItem item = store.addItem(0, "Old shared task");
            store.delete(item.id);

            mode.set(TodoStore.Mode.LOCAL);
            store.tick();

            assertTrue(hasTitle(store, "Delete requested: Old shared task"));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".bak"));
        }
    }

    private static TodoStore store(Path file, AtomicReference<TodoStore.Mode> mode) {
        return new TodoStore(file.toString(), "test", () -> "Tester", line -> { }, mode::get);
    }

    private static boolean hasTitle(TodoStore store, String title) {
        for (TodoItem item : store.view().items.values())
            if (title.equals(item.title))
                return true;
        return false;
    }
}
