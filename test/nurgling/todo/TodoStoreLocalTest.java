package nurgling.todo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TodoStoreLocalTest {
    @TempDir Path temp;

    @Test
    void personalAndSharedLocalTasksSurviveRestart() {
        String file = temp.resolve("todo.nurgling.json").toString();
        TodoStore first = new TodoStore(file, "world", () -> "Player", line -> {}, () -> TodoStore.Mode.LOCAL);
        first.tick();
        TodoItem shared = first.addItem(0, "Repair racks");
        TodoItem personal = first.addItem(TodoList.PERSONAL, "Study curiosities");
        assertNotNull(shared);
        assertNotNull(personal);
        first.setDone(shared.id, true);
        first.flushFile();

        TodoStore reopened = new TodoStore(file, "world", () -> "Player", line -> {}, () -> TodoStore.Mode.LOCAL);
        reopened.tick();
        assertEquals("Repair racks", reopened.view().items.get(shared.id).title);
        assertTrue(reopened.view().items.get(shared.id).done);
        assertEquals("Study curiosities", reopened.view().items.get(personal.id).title);
        assertFalse(reopened.view().items.get(personal.id).done);
    }
}
