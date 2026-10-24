package nurgling;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import nurgling.conf.NCombatData;
import nurgling.conf.NCooldown;

final class CombatSchoolUi {
    enum Category {
        ALL("char.fight.category.all", Icon.ALL),
        ATTACKS("char.fight.category.attacks", Icon.ATTACK),
        DEFENCES("char.fight.category.defences", Icon.DEFENCE),
        MANEUVERS("char.fight.category.maneuvers", Icon.MANEUVER),
        MOVES("char.fight.category.moves", Icon.MOVE),
        OTHER("char.fight.category.other", Icon.OTHER);

        static {
            Map<Category, Set<String>> classified = classify(NCombatData.ATTACKS.keySet(),
                    NCombatData.MANEUVERS, NCooldown.data.keySet(),
                    NCooldown.fixeddata.keySet(), NCooldown.vardata.keySet());
            for (Category category : values())
                category.names.addAll(classified.get(category));
        }

        final String labelKey;
        final Icon icon;
        final Set<String> names;

        Category(String labelKey, Icon icon, String... names) {
            this.labelKey = labelKey;
            this.icon = icon;
            this.names = new HashSet<>(Arrays.asList(names));
        }

        boolean matches(String resourceName) {
            if(this == ALL)
                return true;
            if(resourceName == null)
                return false;
            if(this == OTHER)
                return !listed(resourceName);
            return names.contains(resourceName);
        }

        private static boolean listed(String resourceName) {
            for(Category category : values()) {
                if(category != ALL && category != OTHER && category.names.contains(resourceName))
                    return true;
            }
            return false;
        }
    }

    enum Icon {ALL, ATTACK, DEFENCE, MANEUVER, MOVE, OTHER}

    /** Derive filters from the same move definitions as the combat HUD. */
    static Map<Category, Set<String>> classify(Collection<String> attacks,
            Collection<String> maneuvers, Collection<String> cooldowns,
            Collection<String> fixedCooldowns, Collection<String> variableCooldowns) {
        Map<Category, Set<String>> result = new EnumMap<>(Category.class);
        for (Category category : Category.values())
            result.put(category, new HashSet<>());
        Set<String> attackSet = result.get(Category.ATTACKS);
        for (String name : attacks)
            attackSet.add("paginae/atk/" + name);
        attackSet.addAll(Arrays.asList("paginae/atk/flex", "paginae/atk/oppknock", "paginae/atk/stealthunder"));
        result.get(Category.MANEUVERS).addAll(maneuvers);
        Set<String> moves = result.get(Category.MOVES);
        moves.addAll(variableCooldowns);
        moves.add("paginae/atk/oppknock");
        Set<String> known = new HashSet<>(cooldowns);
        known.addAll(fixedCooldowns);
        known.addAll(variableCooldowns);
        Set<String> defences = result.get(Category.DEFENCES);
        for (String name : known)
            if (!attackSet.contains(name) && !result.get(Category.MANEUVERS).contains(name)
                    && !moves.contains(name))
                defences.add(name);
        defences.addAll(Arrays.asList("paginae/atk/flex", "paginae/atk/dash"));
        return result;
    }

    private CombatSchoolUi() {}

    static boolean canIncrease(int current, int available) {
        return current < available;
    }

    static String usedText(String pattern, int used, int maximum) {
        return String.format(pattern, used, maximum);
    }
}
