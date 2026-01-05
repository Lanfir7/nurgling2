package nurgling.tools;

import haven.Coord;
import haven.Gob;

import java.util.HashMap;

public class StockpileUtils {
    public static HashMap<String, Coord> itemMaxSize = new HashMap<>();
    static {
        itemMaxSize.put("gfx/terobjs/stockpile-hide", new Coord(2,2));
        itemMaxSize.put("gfx/terobjs/stockpile-fish", new Coord(2,3));
        itemMaxSize.put("gfx/terobjs/stockpile-bone", new Coord(3,2));
        itemMaxSize.put("gfx/terobjs/stockpile-board", new Coord(4,1));
        itemMaxSize.put("gfx/terobjs/stockpile-block", new Coord(1,2));
        itemMaxSize.put("gfx/terobjs/stockpile-brick", new Coord(2,1)); // Кирпичи 2x1
        itemMaxSize.put("gfx/terobjs/stockpile-coal", new Coord(1,1));  // Уголь 1x1
        itemMaxSize.put("gfx/terobjs/stockpile-ore", new Coord(1,1));   // Руда 1x1
        itemMaxSize.put("gfx/terobjs/stockpile-wblock", new Coord(1,2)); // Деревянные блоки 1x2
        itemMaxSize.put("gfx/terobjs/stockpile-leaf", new Coord(1,1));  // Листья 1x1
        itemMaxSize.put("gfx/terobjs/stockpile-sand", new Coord(1,1));  // Песок 1x1
        itemMaxSize.put("gfx/terobjs/stockpile-soil", new Coord(1,1));  // Земля 1x1
        itemMaxSize.put("gfx/terobjs/stockpile-bar", new Coord(2,1));   // Слитки 2x1
        itemMaxSize.put("gfx/terobjs/stockpile-cloth", new Coord(2,2)); // Ткань 2x2
        itemMaxSize.put("gfx/terobjs/stockpile-straw", new Coord(1,1)); // Солома 1x1
    }

    public static HashMap<String, String> defaultItems = new HashMap<>();
    static {
        defaultItems.put("gfx/terobjs/stockpile-hide", "Bear Hide");
        defaultItems.put("gfx/terobjs/stockpile-fish", "Pike");
        defaultItems.put("gfx/terobjs/stockpile-bone", "Bone Material");
        defaultItems.put("gfx/terobjs/stockpile-ore", "Cassiterite");
    }


//    public static String getDefaultItem(Gob pile) {
//        if(pile.ngob.name!=null)
//        {
//            return defaultItems.get(pile.ngob.name);
//        }
//        return null;
//    }
}
