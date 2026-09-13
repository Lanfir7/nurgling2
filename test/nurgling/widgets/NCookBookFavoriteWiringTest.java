package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless source check: production favourite stars enqueue via asyncManagerPort,
 * not synchronous managerPort / FavoriteRecipeManager.toggleFavorite.
 */
class NCookBookFavoriteWiringTest {
    @Test
    void productionSourceUsesAsyncManagerPort() throws Exception {
        String src = read("src/nurgling/widgets/NCookBook.java");
        assertTrue(src.contains("asyncManagerPort"), src);
        assertFalse(src.contains("managerPort(favoriteManager)"), src);
        assertTrue(src.contains("dbReady()"), src);
        assertTrue(src.contains("ensureFavorites()"), src);
    }

    @Test
    void ensureFavoritesWiresAsyncPortNotSynchronousManagerPort() throws Exception {
        String src = read("src/nurgling/widgets/NCookBook.java");
        int start = src.indexOf("private void ensureFavorites()");
        int end = src.indexOf("private void reload()");
        assertTrue(start >= 0 && end > start, src);
        String ensure = src.substring(start, end);
        assertTrue(ensure.contains("favoriteManager = new FavoriteRecipeManager(db)"), ensure);
        assertTrue(ensure.contains("CookbookModel.asyncManagerPort(favoriteManager, db)"), ensure);
        assertFalse(ensure.contains("CookbookModel.managerPort("), ensure);
        assertFalse(ensure.contains("toggleFavorite("), ensure);
        assertFalse(ensure.contains("addFavorite("), ensure);
        assertFalse(ensure.contains("removeFavorite("), ensure);
    }

    @Test
    void toggleFavoriteKeepsGuardEnsureAndModelPath() throws Exception {
        String src = read("src/nurgling/widgets/NCookBook.java");
        int start = src.indexOf("public void toggleFavorite(CookbookRow row)");
        int end = src.indexOf("public void rowMenu(");
        assertTrue(start >= 0 && end > start, src);
        String toggle = src.substring(start, end);
        assertTrue(toggle.contains("if(!dbReady())"), toggle);
        assertTrue(toggle.contains("ensureFavorites();"), toggle);
        assertTrue(toggle.contains("model.toggleFavorite(row);"), toggle);
        assertFalse(toggle.contains("favoriteManager.toggleFavorite"), toggle);
        assertFalse(toggle.contains("CookbookModel.managerPort("), toggle);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
