package nurgling.widgets.craftatlas;

import haven.Coord;
import haven.Disposable;
import haven.GOut;
import haven.Loading;
import haven.Resource;
import haven.Tex;
import haven.TexI;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Small texture cache backed by live game resources and the offline wiki icon sheets. */
final class CraftAtlasIconCache implements Disposable {
    private final Map<String, Tex> textures = new HashMap<>();
    private final Set<String> misses = new HashSet<>();
    private final Function<String, BufferedImage> gameLoader;
    private final Function<String, BufferedImage> wikiLoader;

    CraftAtlasIconCache() {
        this(CraftAtlasIconCache::gameImage, WikiIcons::image);
    }

    CraftAtlasIconCache(Function<String, BufferedImage> gameLoader,
                        Function<String, BufferedImage> wikiLoader) {
        this.gameLoader = gameLoader;
        this.wikiLoader = wikiLoader;
    }

    Tex recipe(String outputResource, String recipeResource, String name) {
        Tex icon = icon(outputResource, name);
        if(icon == null) icon = icon(recipeResource, name);
        return icon;
    }

    Tex icon(String resource, String name) {
        String key = (resource == null ? "" : resource) + '\n' + (name == null ? "" : name);
        Tex cached = textures.get(key);
        if(cached != null || misses.contains(key)) return cached;
        BufferedImage image;
        try {
            image = loadImage(resource, name, gameLoader, wikiLoader);
        } catch(Loading pending) {
            return null;
        }
        if(image == null) { misses.add(key); return null; }
        image = trimTransparent(image);
        Tex texture = new TexI(image);
        textures.put(key, texture);
        return texture;
    }

    static BufferedImage loadImage(String resource, String name,
                                   Function<String, BufferedImage> gameLoader,
                                   Function<String, BufferedImage> wikiLoader) {
        try {
            BufferedImage image = gameLoader.apply(resource);
            if(image != null) return image;
        } catch(Loading pending) {
            BufferedImage fallback = wikiLoader.apply(name);
            if(fallback != null) return fallback;
            throw pending;
        }
        return wikiLoader.apply(name);
    }

    private static BufferedImage gameImage(String resource) {
        if(resource == null || !(resource.startsWith("gfx/") || resource.startsWith("paginae/"))) return null;
        try {
            Resource.Image image = Resource.remote().load(resource).get().layer(Resource.imgc);
            return image == null ? null : image.img;
        } catch(Loading pending) {
            throw pending;
        } catch(RuntimeException failed) {
            return null;
        }
    }

    static BufferedImage trimTransparent(BufferedImage image) {
        if(image == null) return null;
        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
        for(int y = 0; y < image.getHeight(); y++) {
            for(int x = 0; x < image.getWidth(); x++) {
                if(((image.getRGB(x, y) >>> 24) & 0xff) <= 8) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if(maxX < minX || maxY < minY ||
                (minX == 0 && minY == 0 && maxX == image.getWidth() - 1 && maxY == image.getHeight() - 1))
            return image;
        return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    static void draw(GOut g, Tex texture, Coord origin, int box) {
        if(texture == null) return;
        Coord source = texture.sz();
        int longest = Math.max(1, Math.max(source.x, source.y));
        int width = Math.max(1, box * source.x / longest);
        int height = Math.max(1, box * source.y / longest);
        g.image(texture, origin.add((box - width) / 2, (box - height) / 2), Coord.of(width, height));
    }

    @Override public void dispose() {
        for(Tex texture : textures.values()) texture.dispose();
        textures.clear();
        misses.clear();
    }

    private static final class WikiIcons {
        private static final JSONObject manifest = loadManifest();
        private static final Map<Integer, BufferedImage> sheets = new HashMap<>();

        static BufferedImage image(String name) {
            if(name == null || manifest == null) return null;
            JSONObject icons = manifest.optJSONObject("icons");
            JSONObject value = icons == null ? null : icons.optJSONObject(name);
            if(value == null) return null;
            int sheetIndex = value.getInt("sheet");
            BufferedImage sheet = sheet(sheetIndex);
            if(sheet == null) return null;
            int cell = manifest.optInt("cell", 64);
            return sheet.getSubimage(value.getInt("x"), value.getInt("y"), cell, cell);
        }

        private static BufferedImage sheet(int index) {
            if(sheets.containsKey(index)) return sheets.get(index);
            try {
                JSONArray names = manifest.getJSONArray("sheets");
                String path = "/nurgling/craftatlas/" + names.getString(index);
                try(InputStream input = CraftAtlasIconCache.class.getResourceAsStream(path)) {
                    BufferedImage image = input == null ? null : ImageIO.read(input);
                    sheets.put(index, image);
                    return image;
                }
            } catch(Exception failed) {
                sheets.put(index, null);
                return null;
            }
        }

        private static JSONObject loadManifest() {
            try(InputStream input = CraftAtlasIconCache.class.getResourceAsStream(
                    "/nurgling/craftatlas/wiki-icons.json")) {
                if(input == null) return null;
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                for(int read; (read = input.read(buffer)) >= 0;) output.write(buffer, 0, read);
                return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            } catch(Exception failed) {
                System.err.println("Unable to load Craft Atlas icons: " + failed.getMessage());
                return null;
            }
        }
    }
}
