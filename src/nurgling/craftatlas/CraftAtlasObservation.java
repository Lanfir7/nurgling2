package nurgling.craftatlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete recipe payload captured from a normal Make window. */
public final class CraftAtlasObservation {
    public final String recipeResource;
    public final String displayName;
    public final List<Item> inputs;
    public final List<Item> outputs;
    public final List<RequirementResource> requirements;
    public final List<BonusResource> bonuses;

    public CraftAtlasObservation(String recipeResource, String displayName, List<Item> inputs, List<Item> outputs,
                                 List<RequirementResource> requirements, List<BonusResource> bonuses) {
        if(recipeResource == null || recipeResource.trim().isEmpty())
            throw new IllegalArgumentException("recipeResource must not be empty");
        this.recipeResource = recipeResource;
        this.displayName = displayName == null || displayName.trim().isEmpty() ? recipeResource : displayName;
        this.inputs = copy(inputs);
        this.outputs = copy(outputs);
        this.requirements = copy(requirements);
        this.bonuses = copy(bonuses);
    }

    private static <T> List<T> copy(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null ? Collections.<T>emptyList() : values));
    }

    public static final class Item {
        public final String resource, name;
        public final int quantity;
        public final boolean optional;
        public Item(String resource, String name, int quantity, boolean optional) {
            this.resource = resource;
            this.name = name == null || name.isEmpty() ? resource : name;
            this.quantity = Math.max(1, quantity);
            this.optional = optional;
        }
    }

    public static final class RequirementResource {
        public final String resource, name;
        public RequirementResource(String resource, String name) {
            this.resource = resource;
            this.name = name == null || name.isEmpty() ? resource : name;
        }
    }

    public static final class BonusResource {
        public final String resource, name;
        public final Double value;
        public BonusResource(String resource, String name, Double value) {
            this.resource = resource;
            this.name = name == null || name.isEmpty() ? resource : name;
            this.value = value;
        }
    }
}
