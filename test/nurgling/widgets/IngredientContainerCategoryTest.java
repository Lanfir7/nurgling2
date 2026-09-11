package nurgling.widgets;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientContainerCategoryTest {

    @Test
    void categoryForItemMapsBlocksAndBoards() {
        assertEquals("Block of Wood", IngredientContainer.categoryForItem("Block of Oak"));
        assertEquals("Board", IngredientContainer.categoryForItem("Board of Pine"));
        assertNull(IngredientContainer.categoryForItem("Block of Wood"));
        assertNull(IngredientContainer.categoryForItem("Board"));
        assertNull(IngredientContainer.categoryForItem("Branch"));
        assertNull(IngredientContainer.categoryForItem(null));
    }

    @Test
    void categoryJsonUsesStaticIconPath() {
        JSONObject oak = new JSONObject();
        oak.put("name", "Block of Oak");
        JSONObject category = IngredientContainer.categoryJsonForItem("Block of Oak", oak);
        assertEquals("Block of Wood", category.getString("name"));
        assertEquals("Block of Oak", category.getString("originalName"));
        assertTrue(category.getBoolean("isCategory"));
        assertEquals("gfx/invobjs/wblock-oak", category.getString("static"));
        assertEquals("Block of Oak", oak.getString("name"));
    }

    @Test
    void categoryIconFallbackNames() {
        assertEquals("gfx/invobjs/wblock-oak", IngredientContainer.categoryIconPath("Block of Wood"));
        assertEquals("gfx/invobjs/board-oak", IngredientContainer.categoryIconPath("Board"));
        assertNull(IngredientContainer.categoryIconPath("Branch"));
        assertTrue(IngredientContainer.isCategoryEntry(categoryEntry("Block of Wood")));
        assertFalse(IngredientContainer.isCategoryEntry(new JSONObject().put("name", "Block of Oak")));
    }

    private static JSONObject categoryEntry(String name) {
        JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("isCategory", true);
        return obj;
    }
}
