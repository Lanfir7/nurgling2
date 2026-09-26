package nurgling;

import haven.*;
import haven.res.lib.vmat.AttrMats;
import haven.res.lib.vmat.Materials;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Names of server-selected variable materials assigned to a gob. */
public final class NInspectMaterials {
    private NInspectMaterials() {}

    public static List<String> names(Gob gob) {
        if(gob == null)
            return Collections.emptyList();
        Map<Integer, Resource> sources = sources(gob);
        if(sources.isEmpty())
            return Collections.emptyList();

        Set<String> names = new LinkedHashSet<>();
        for(Map.Entry<Integer, Resource> entry : sources.entrySet()) {
            Resource res = entry.getValue();
            if(res == null)
                continue;
            Resource.Tooltip tooltip = res.layer(Resource.tooltip);
            String name = (tooltip != null) ? tooltip.text() : NUtils.prettyResName(res.name).toString();
            if(name != null && !name.trim().isEmpty())
                names.add(name.trim());
        }
        return new ArrayList<>(names);
    }

    static Map<Integer, Resource> sources(Gob gob) {
        Map<Integer, Resource> sources = new TreeMap<>();
        AttrMats active = gob.getattr(AttrMats.class);
        if(active != null) {
            for(Map.Entry<Integer, Material> entry : active.mats.entrySet()) {
                Material material = entry.getValue();
                if(material instanceof Material.ResMaterial)
                    sources.put(entry.getKey(), ((Material.ResMaterial)material).res);
            }
            if(!sources.isEmpty())
                return sources;
        }
        Materials legacy = gob.getattr(Materials.class);
        if(legacy != null)
            sources.putAll(legacy.sources);
        return sources;
    }

}
