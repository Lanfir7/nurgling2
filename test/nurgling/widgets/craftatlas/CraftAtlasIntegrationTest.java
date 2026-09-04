package nurgling.widgets.craftatlas;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftAtlasIntegrationTest {
    @Test
    void gameUiOwnsOneAtlasAndCraftWindowCompletesBridge() throws Exception {
        String game = read("src/haven/GameUI.java");
        String ngame = read("src/nurgling/NGameUI.java");
        assertTrue(game.contains("public CraftAtlasWindow craftAtlas"));
        assertTrue(game.contains("KeyBinding.get(\"craft-atlas\""));
        assertTrue(game.contains("craftAtlas = new CraftAtlasWindow(menu)"));
        assertTrue(game.contains("wndstate(craftAtlas)"));
        assertTrue(ngame.contains("craftAtlas.onCraftWindowOpened()"));
        assertTrue(ngame.indexOf("craftAtlas.onCraftWindowOpened()") > ngame.indexOf("craftwnd.add(child)"));
    }

    @Test
    void atlasDetailsAndFooterWireTheResourcePlannerToCollection() throws Exception {
        String details = read("src/nurgling/widgets/craftatlas/CraftAtlasDetails.java");
        String window = read("src/nurgling/widgets/craftatlas/CraftAtlasWindow.java");

        assertTrue(details.contains("new CraftAtlasMaterialSource()"));
        assertTrue(details.contains("CraftAtlasMaterialPlanner.plan("));
        assertTrue(details.contains("new CraftAtlasIngredientSelector("));
        assertTrue(window.contains("new CraftAtlasResourceCollector("));
        assertTrue(window.contains("details.refreshMaterialsAsync("));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
