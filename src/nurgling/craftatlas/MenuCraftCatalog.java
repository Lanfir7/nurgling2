package nurgling.craftatlas;

import haven.Loading;
import haven.ItemInfo;
import haven.MenuGrid;
import haven.Resource;
import haven.res.ui.tt.attrmod.AttrMod;
import haven.res.ui.tt.attrmod.Entry;
import haven.res.ui.tt.attrmod.Mod;
import haven.res.ui.tt.attrmod.resattr;
import haven.res.ui.tt.slot.Slotted;
import haven.resutil.FoodInfo;
import haven.resutil.Curiosity;
import nurgling.iteminfo.NCuriosity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Field;

/** Merges current menu availability with complete Make-window observations. */
public final class MenuCraftCatalog {
    public static final class PageRecord {
        public final String resource, name;
        public final List<String> categories;
        public final List<CraftAtlasEntry.Bonus> bonuses;
        public final CraftAtlasEntry.Gilding gilding;
        public final CraftAtlasEntry.Curiosity curiosity;
        public final List<CraftAtlasEntry.AttributeRef> qualityModifiers;
        public PageRecord(String resource, String name) {
            this(resource, name, Collections.<String>emptyList(), Collections.<CraftAtlasEntry.Bonus>emptyList());
        }
        public PageRecord(String resource, String name, List<String> categories, List<CraftAtlasEntry.Bonus> bonuses) {
            this(resource, name, categories, bonuses, null, Collections.<CraftAtlasEntry.AttributeRef>emptyList());
        }
        public PageRecord(String resource, String name, List<String> categories, List<CraftAtlasEntry.Bonus> bonuses,
                          CraftAtlasEntry.Gilding gilding, List<CraftAtlasEntry.AttributeRef> qualityModifiers) {
            this(resource, name, categories, bonuses, gilding, qualityModifiers, null);
        }
        public PageRecord(String resource, String name, List<String> categories, List<CraftAtlasEntry.Bonus> bonuses,
                          CraftAtlasEntry.Gilding gilding, List<CraftAtlasEntry.AttributeRef> qualityModifiers,
                          CraftAtlasEntry.Curiosity curiosity) {
            this.resource = resource;
            this.name = name;
            this.categories = categories;
            this.bonuses = bonuses;
            this.gilding = gilding;
            this.qualityModifiers = qualityModifiers;
            this.curiosity = curiosity;
        }
    }

    private final MenuGrid menu;
    private final CraftAtlasObservationStore store;
    private final List<CraftAtlasEntry> references;
    public MenuCraftCatalog(MenuGrid menu, CraftAtlasObservationStore store) {
        this(menu, store, WikiReferenceCatalog.loadBundled());
    }
    MenuCraftCatalog(MenuGrid menu, CraftAtlasObservationStore store, List<CraftAtlasEntry> references) {
        this.menu = menu;
        this.store = store;
        this.references = references == null ? Collections.<CraftAtlasEntry>emptyList() : references;
    }

    public CraftAtlasSnapshot rebuild() {
        List<PageRecord> pages = new ArrayList<>();
        if(menu != null) for(MenuGrid.Pagina page : menu.recipeSnapshot()) {
            try { pages.add(readPage(page)); }
            catch(Loading ignored) { }
        }
        return fromRecords(menu == null ? 0 : menu.pagseq, pages,
                store == null ? Collections.<String, CraftAtlasObservation>emptyMap() : store.all(), references);
    }

    public static CraftAtlasSnapshot fromRecords(long revision, List<PageRecord> pages,
                                                  Map<String, CraftAtlasObservation> observations) {
        return fromRecords(revision, pages, observations, Collections.<CraftAtlasEntry>emptyList());
    }

    public static CraftAtlasSnapshot fromRecords(long revision, List<PageRecord> pages,
                                                  Map<String, CraftAtlasObservation> observations,
                                                  List<CraftAtlasEntry> references) {
        LinkedHashMap<String, PageRecord> open = new LinkedHashMap<>();
        if(pages != null) for(PageRecord page : pages) if(page != null && page.resource != null) open.put(page.resource, page);
        LinkedHashMap<String, CraftAtlasEntry> result = new LinkedHashMap<>();
        for(PageRecord page : open.values())
            result.put(page.resource, build(page, CraftAtlasEntry.Availability.OPEN,
                    observations == null ? null : observations.get(page.resource)));
        if(observations != null) for(CraftAtlasObservation observation : observations.values())
            if(!result.containsKey(observation.recipeResource))
                result.put(observation.recipeResource, build(new PageRecord(observation.recipeResource, observation.displayName),
                        CraftAtlasEntry.Availability.UNAVAILABLE_NOW, observation));
        mergeReferences(result, references);
        return CraftAtlasSnapshot.of(revision, result.values());
    }

    private static void mergeReferences(LinkedHashMap<String, CraftAtlasEntry> result,
                                        List<CraftAtlasEntry> references) {
        if(references == null || references.isEmpty()) return;
        Map<String, String> byName = new LinkedHashMap<>();
        for(CraftAtlasEntry entry : result.values())
            byName.put(CraftAtlasSearch.normalize(entry.displayName), entry.recipeResource);
        for(CraftAtlasEntry reference : references) {
            String name = CraftAtlasSearch.normalize(reference.displayName);
            String liveResource = byName.get(name);
            if(liveResource == null) {
                result.put(reference.recipeResource, reference);
                byName.put(name, reference.recipeResource);
            } else {
                result.put(liveResource, merge(result.get(liveResource), reference));
            }
        }
    }

    private static CraftAtlasEntry merge(CraftAtlasEntry live, CraftAtlasEntry reference) {
        CraftAtlasEntry.Builder builder = CraftAtlasEntry.builder(live.recipeResource, live.displayName)
                .availability(live.availability)
                .inputsObserved(live.inputsObserved)
                .output(live.outputResource == null ? reference.outputResource : live.outputResource)
                .description(live.description == null ? reference.description : live.description);
        builder.gilding(live.gilding == null ? reference.gilding : live.gilding);
        builder.curiosity(live.curiosity == null ? reference.curiosity : live.curiosity);
        List<String> equipmentSlots = live.equipmentSlots.isEmpty() ? reference.equipmentSlots : live.equipmentSlots;
        for(String slot : equipmentSlots) builder.equipmentSlot(slot);
        List<CraftAtlasEntry.InputSlot> inputs = live.inputs.isEmpty() ? reference.inputs : live.inputs;
        for(CraftAtlasEntry.InputSlot input : inputs) builder.input(input);

        Set<String> requirementKeys = new LinkedHashSet<>();
        for(CraftAtlasEntry.Requirement requirement : live.requirements) {
            builder.requirement(requirement);
            requirementKeys.add(CraftAtlasSearch.normalize(requirement.name));
        }
        for(CraftAtlasEntry.Requirement requirement : reference.requirements)
            if(requirementKeys.add(CraftAtlasSearch.normalize(requirement.name)))
                builder.requirement(requirement);

        Set<String> bonusKeys = new LinkedHashSet<>();
        for(CraftAtlasEntry.Bonus bonus : live.bonuses) {
            builder.bonus(bonus);
            bonusKeys.add(bonusKey(bonus));
        }
        for(CraftAtlasEntry.Bonus bonus : reference.bonuses)
            if(bonusKeys.add(bonusKey(bonus))) builder.bonus(bonus);

        Set<String> qualityKeys = new LinkedHashSet<>();
        for(CraftAtlasEntry.AttributeRef attribute : live.qualityModifiers) {
            builder.qualityModifier(attribute);
            qualityKeys.add(attribute.resource == null ? CraftAtlasSearch.normalize(attribute.name) : attribute.resource);
        }
        for(CraftAtlasEntry.AttributeRef attribute : reference.qualityModifiers) {
            String key = attribute.resource == null ? CraftAtlasSearch.normalize(attribute.name) : attribute.resource;
            if(qualityKeys.add(key)) builder.qualityModifier(attribute);
        }

        Set<String> categories = new LinkedHashSet<>(live.categories);
        categories.addAll(reference.categories);
        for(String category : categories) builder.category(category);
        return builder.build();
    }

    private static CraftAtlasEntry build(PageRecord page, CraftAtlasEntry.Availability availability,
                                         CraftAtlasObservation observation) {
        CraftAtlasEntry.Builder builder = CraftAtlasEntry.builder(page.resource,
                observation != null ? observation.displayName : page.name).availability(availability)
                .inputsObserved(observation != null && !observation.inputs.isEmpty());
        Set<String> qualityKeys = new LinkedHashSet<>();
        LinkedHashMap<String, CraftAtlasEntry.Bonus> bonuses = new LinkedHashMap<>();
        for(CraftAtlasEntry.Bonus bonus : page.bonuses) bonuses.put(bonusKey(bonus), bonus);
        if(observation != null) {
            if(!observation.outputs.isEmpty()) builder.output(observation.outputs.get(0).resource);
            for(CraftAtlasObservation.Item item : observation.inputs)
                builder.input(new CraftAtlasEntry.InputSlot(item.quantity, item.optional,
                        Collections.singletonList(new CraftAtlasEntry.IngredientOption(item.resource, item.name))));
            CraftRequirementClassifier classifier = new CraftRequirementClassifier(Collections.<String, CraftAtlasEntry.RequirementKind>emptyMap());
            for(CraftAtlasObservation.RequirementResource requirement : observation.requirements)
                builder.requirement(classifier.classify(requirement.resource, requirement.name));
            for(CraftAtlasObservation.BonusResource bonus : observation.bonuses) {
                CraftAtlasEntry.Bonus value = new CraftAtlasEntry.Bonus(bonus.resource, bonus.name, bonus.value);
                bonuses.putIfAbsent(bonusKey(value), value);
            }
            for(CraftAtlasObservation.AttributeResource attribute : observation.qualityModifiers) {
                CraftAtlasEntry.AttributeRef value = CraftAtlasAttributes.ref(attribute.resource, attribute.name);
                if(qualityKeys.add(attributeKey(value))) builder.qualityModifier(value);
            }
        }
        builder.gilding(page.gilding);
        builder.curiosity(page.curiosity);
        for(String category : page.categories) builder.category(category);
        for(CraftAtlasEntry.Bonus bonus : bonuses.values()) builder.bonus(bonus);
        for(CraftAtlasEntry.AttributeRef attribute : page.qualityModifiers)
            if(qualityKeys.add(attributeKey(attribute))) builder.qualityModifier(attribute);
        return builder.build();
    }

    private static String attributeKey(CraftAtlasEntry.AttributeRef attribute) {
        return attribute.resource == null ? CraftAtlasSearch.normalize(attribute.name) : attribute.resource;
    }

    private static String bonusKey(CraftAtlasEntry.Bonus bonus) {
        String name = CraftAtlasAttributes.baseName(bonus.name);
        String resource = CraftAtlasAttributes.resource(name, bonus.attributeResource);
        return resource == null ? CraftAtlasSearch.normalize(name) : resource;
    }

    private static PageRecord readPage(MenuGrid.Pagina page) {
        List<String> categories = new ArrayList<>();
        LinkedHashMap<String, CraftAtlasEntry.Bonus> bonuses = new LinkedHashMap<>();
        LinkedHashMap<String, CraftAtlasEntry.AttributeRef> qualityModifiers = new LinkedHashMap<>();
        CraftAtlasEntry.Gilding gilding = null;
        CraftAtlasEntry.Curiosity curiosity = null;
        for(ItemInfo info : page.button().info()) {
            if(info instanceof Slotted) {
                categories.add("gildings");
                Slotted slotted = (Slotted)info;
                List<CraftAtlasEntry.AttributeRef> attributes = new ArrayList<>();
                for(Resource resource : slotted.attrs)
                    attributes.add(CraftAtlasAttributes.ref(resource.name, resourceName(resource)));
                gilding = new CraftAtlasEntry.Gilding(slotted.pmin, slotted.pmax, attributes);
                for(ItemInfo child : slotted.sub) {
                    if(!(child instanceof AttrMod)) continue;
                    for(Entry entry : ((AttrMod)child).tab) if(entry instanceof Mod) {
                        Mod mod = (Mod)entry;
                        String resource = mod.attr instanceof resattr ? ((resattr)mod.attr).res.name : "attribute:" + mod.attr.name();
                        bonuses.put(resource, new CraftAtlasEntry.Bonus(resource, mod.attr.name(), mod.mod));
                    }
                }
            }
            if("Skills".equals(info.getClass().getSimpleName())) {
                for(Resource resource : skillResources(info)) {
                    CraftAtlasEntry.AttributeRef attribute = CraftAtlasAttributes.ref(resource.name, resourceName(resource));
                    qualityModifiers.put(attribute.resource, attribute);
                }
            }
            if(info instanceof FoodInfo) {
                categories.add("foods");
                FoodInfo food = (FoodInfo)info;
                bonuses.put("food:energy", new CraftAtlasEntry.Bonus("food:energy", "Energy", food.end * 100));
                bonuses.put("food:hunger", new CraftAtlasEntry.Bonus("food:hunger", "Hunger", food.glut * 1000));
                for(FoodInfo.Event event : food.evs) {
                    String name = CraftAtlasAttributes.baseName(event.ev.nm);
                    String key = "food:" + CraftAtlasSearch.normalize(name);
                    String resource = CraftAtlasAttributes.resource(name, key);
                    bonuses.put(resource, new CraftAtlasEntry.Bonus(resource, name, event.a));
                }
            }
            if(info instanceof Curiosity) {
                categories.add("curiosities");
                Curiosity value = (Curiosity)info;
                int realMinutes = Math.max(1, Math.round(value.time / NCuriosity.server_ratio / 60f));
                curiosity = new CraftAtlasEntry.Curiosity(value.exp, realMinutes, value.mw);
            }
        }
        return new PageRecord(page.res().name, page.button().name(), categories, new ArrayList<>(bonuses.values()),
                gilding, new ArrayList<>(qualityModifiers.values()), curiosity);
    }

    private static Resource[] skillResources(ItemInfo info) {
        try {
            Field field = info.getClass().getDeclaredField("skills");
            field.setAccessible(true);
            Object value = field.get(info);
            return value instanceof Resource[] ? (Resource[])value : new Resource[0];
        } catch(ReflectiveOperationException ignored) {
            return new Resource[0];
        }
    }

    private static String resourceName(Resource resource) {
        Resource.Tooltip tooltip = resource.layer(Resource.tooltip);
        if(tooltip != null && tooltip.text() != null && !tooltip.text().trim().isEmpty()) return tooltip.text();
        int slash = resource.name.lastIndexOf('/');
        return slash < 0 ? resource.name : resource.name.substring(slash + 1);
    }
}
