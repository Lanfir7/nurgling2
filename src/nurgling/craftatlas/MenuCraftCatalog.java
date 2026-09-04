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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Merges current menu availability with complete Make-window observations. */
public final class MenuCraftCatalog {
    public static final class PageRecord {
        public final String resource, name;
        public final List<String> categories;
        public final List<CraftAtlasEntry.Bonus> bonuses;
        public PageRecord(String resource, String name) {
            this(resource, name, Collections.<String>emptyList(), Collections.<CraftAtlasEntry.Bonus>emptyList());
        }
        public PageRecord(String resource, String name, List<String> categories, List<CraftAtlasEntry.Bonus> bonuses) {
            this.resource = resource;
            this.name = name;
            this.categories = categories;
            this.bonuses = bonuses;
        }
    }

    private final MenuGrid menu;
    private final CraftAtlasObservationStore store;
    public MenuCraftCatalog(MenuGrid menu, CraftAtlasObservationStore store) { this.menu = menu; this.store = store; }

    public CraftAtlasSnapshot rebuild() {
        List<PageRecord> pages = new ArrayList<>();
        if(menu != null) for(MenuGrid.Pagina page : menu.recipeSnapshot()) {
            try { pages.add(readPage(page)); }
            catch(Loading ignored) { }
        }
        return fromRecords(menu == null ? 0 : menu.pagseq, pages,
                store == null ? Collections.<String, CraftAtlasObservation>emptyMap() : store.all());
    }

    public static CraftAtlasSnapshot fromRecords(long revision, List<PageRecord> pages,
                                                  Map<String, CraftAtlasObservation> observations) {
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
        return CraftAtlasSnapshot.of(revision, result.values());
    }

    private static CraftAtlasEntry build(PageRecord page, CraftAtlasEntry.Availability availability,
                                         CraftAtlasObservation observation) {
        CraftAtlasEntry.Builder builder = CraftAtlasEntry.builder(page.resource,
                observation != null ? observation.displayName : page.name).availability(availability);
        if(observation != null) {
            if(!observation.outputs.isEmpty()) builder.output(observation.outputs.get(0).resource);
            for(CraftAtlasObservation.Item item : observation.inputs)
                builder.input(new CraftAtlasEntry.InputSlot(item.quantity, item.optional,
                        Collections.singletonList(new CraftAtlasEntry.IngredientOption(item.resource, item.name))));
            CraftRequirementClassifier classifier = new CraftRequirementClassifier(Collections.<String, CraftAtlasEntry.RequirementKind>emptyMap());
            for(CraftAtlasObservation.RequirementResource requirement : observation.requirements)
                builder.requirement(classifier.classify(requirement.resource, requirement.name));
            for(CraftAtlasObservation.BonusResource bonus : observation.bonuses)
                builder.bonus(new CraftAtlasEntry.Bonus(bonus.resource, bonus.name, bonus.value));
        }
        for(String category : page.categories) builder.category(category);
        for(CraftAtlasEntry.Bonus bonus : page.bonuses) builder.bonus(bonus);
        return builder.build();
    }

    private static PageRecord readPage(MenuGrid.Pagina page) {
        List<String> categories = new ArrayList<>();
        LinkedHashMap<String, CraftAtlasEntry.Bonus> bonuses = new LinkedHashMap<>();
        for(ItemInfo info : page.button().info()) {
            if(info instanceof Slotted) {
                categories.add("gildings");
                for(ItemInfo child : ((Slotted)info).sub) {
                    if(!(child instanceof AttrMod)) continue;
                    for(Entry entry : ((AttrMod)child).tab) if(entry instanceof Mod) {
                        Mod mod = (Mod)entry;
                        String resource = mod.attr instanceof resattr ? ((resattr)mod.attr).res.name : "attribute:" + mod.attr.name();
                        bonuses.put(resource, new CraftAtlasEntry.Bonus(resource, mod.attr.name(), mod.mod));
                    }
                }
            }
            if(info instanceof FoodInfo) {
                categories.add("foods");
                FoodInfo food = (FoodInfo)info;
                bonuses.put("food:energy", new CraftAtlasEntry.Bonus("food:energy", "Energy", food.end * 100));
                bonuses.put("food:hunger", new CraftAtlasEntry.Bonus("food:hunger", "Hunger", food.glut * 1000));
                for(FoodInfo.Event event : food.evs) {
                    String key = "food:" + CraftAtlasSearch.normalize(event.ev.nm);
                    bonuses.put(key, new CraftAtlasEntry.Bonus(key, event.ev.nm, event.a));
                }
            }
        }
        return new PageRecord(page.res().name, page.button().name(), categories, new ArrayList<>(bonuses.values()));
    }
}
