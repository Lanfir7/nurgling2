package nurgling;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class CombatSchoolUi {
    enum Category {
        ALL("char.fight.category.all", Icon.ALL),
        ATTACKS("char.fight.category.attacks", Icon.ATTACK),
        DEFENCES("char.fight.category.defences", Icon.DEFENCE,
                "paginae/atk/regain", "paginae/atk/dash", "paginae/atk/zigzag",
                "paginae/atk/yieldground", "paginae/atk/watchmoves", "paginae/atk/sidestep",
                "paginae/atk/qdodge", "paginae/atk/jump", "paginae/atk/fdodge",
                "paginae/atk/artevade", "paginae/atk/flex"),
        MANEUVERS("char.fight.category.maneuvers", Icon.MANEUVER,
                "paginae/atk/toarms", "paginae/atk/shield", "paginae/atk/parry",
                "paginae/atk/oakstance", "paginae/atk/dorg", "paginae/atk/chinup",
                "paginae/atk/bloodlust", "paginae/atk/combmed"),
        MOVES("char.fight.category.moves", Icon.MOVE,
                "paginae/atk/think", "paginae/atk/takeaim", "paginae/atk/dash",
                "paginae/atk/oppknock"),
        OTHER("char.fight.category.other", Icon.OTHER);

        static {
            ATTACKS.names.addAll(Arrays.asList(
                    "paginae/atk/pow", "paginae/atk/lefthook", "paginae/atk/lowblow",
                    "paginae/atk/oppknock", "paginae/atk/ripapart", "paginae/atk/fullcircle",
                    "paginae/atk/cleave", "paginae/atk/barrage", "paginae/atk/sideswipe",
                    "paginae/atk/sting", "paginae/atk/sos", "paginae/atk/knockteeth",
                    "paginae/atk/kick", "paginae/atk/haymaker", "paginae/atk/chop",
                    "paginae/atk/gojug", "paginae/atk/uppercut", "paginae/atk/punchboth",
                    "paginae/atk/stealthunder", "paginae/atk/ravenbite", "paginae/atk/takedown",
                    "paginae/atk/flex"));
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

    private CombatSchoolUi() {}

    static boolean canIncrease(int current, int available) {
        return current < available;
    }

    static String usedText(String pattern, int used, int maximum) {
        return String.format(pattern, used, maximum);
    }
}
