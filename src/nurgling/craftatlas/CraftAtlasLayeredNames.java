package nurgling.craftatlas;

import nurgling.tools.VSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Resolves dual-icon craft ingredients (raw meat, poultry, hides) to VSpec item names. */
public final class CraftAtlasLayeredNames {
    public static final class Identity {
        public final String resource, name;

        public Identity(String resource, String name) {
            this.resource = resource;
            this.name = name == null || name.isEmpty() ? resource : name;
        }
    }

    private CraftAtlasLayeredNames() {}

    public static Identity resolve(String fallbackResource, String fallbackName, Collection<String> layers) {
        List<String> paths = copy(layers);
        if(paths.size() >= 2) {
            String name = VSpec.nameForLayers(paths);
            return new Identity(String.join("+", paths), name != null ? name : fallbackName);
        }
        if(paths.size() == 1)
            return new Identity(paths.get(0), fallbackName);
        return new Identity(fallbackResource, fallbackName);
    }

    public static String nameForResource(String resource) {
        if(resource == null || !resource.contains("+")) return null;
        return VSpec.nameForLayers(splitComposite(resource));
    }

    static List<String> splitComposite(String resource) {
        List<String> paths = new ArrayList<>();
        if(resource == null || resource.isEmpty()) return paths;
        for(String part : resource.split("\\+"))
            if(part != null && !part.isEmpty()) paths.add(part);
        return paths;
    }

    private static List<String> copy(Collection<String> layers) {
        List<String> paths = new ArrayList<>();
        if(layers == null) return paths;
        for(String layer : layers)
            if(layer != null && !layer.isEmpty()) paths.add(layer);
        return paths;
    }
}
