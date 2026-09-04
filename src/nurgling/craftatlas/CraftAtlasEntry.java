package nurgling.craftatlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable recipe data used by the Craft Atlas UI. */
public final class CraftAtlasEntry {
    public enum Availability { OPEN, UNAVAILABLE_NOW, CHECKING, REFERENCE_ONLY }
    public enum RequirementKind { STATION, TOOL, SKILL, DISCOVERY }

    public final String recipeResource;
    public final String displayName;
    public final String outputResource;
    public final Availability availability;
    public final List<InputSlot> inputs;
    public final List<Requirement> requirements;
    public final List<Bonus> bonuses;
    public final List<String> categories;
    public final String description;

    private CraftAtlasEntry(Builder b) {
        recipeResource = required(b.recipeResource, "recipeResource");
        displayName = b.displayName == null || b.displayName.trim().isEmpty() ? recipeResource : b.displayName;
        outputResource = b.outputResource;
        availability = b.availability;
        inputs = immutable(b.inputs);
        requirements = immutable(b.requirements);
        bonuses = immutable(b.bonuses);
        categories = immutable(b.categories);
        description = b.description;
    }

    public static Builder builder(String recipeResource, String displayName) {
        return new Builder(recipeResource, displayName);
    }

    private static String required(String value, String name) {
        if(value == null || value.trim().isEmpty())
            throw new IllegalArgumentException(name + " must not be empty");
        return value;
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public static final class Builder {
        private final String recipeResource;
        private final String displayName;
        private String outputResource;
        private Availability availability = Availability.CHECKING;
        private final List<InputSlot> inputs = new ArrayList<>();
        private final List<Requirement> requirements = new ArrayList<>();
        private final List<Bonus> bonuses = new ArrayList<>();
        private final List<String> categories = new ArrayList<>();
        private String description;

        private Builder(String recipeResource, String displayName) {
            this.recipeResource = recipeResource;
            this.displayName = displayName;
        }

        public Builder output(String resource) { outputResource = resource; return this; }
        public Builder availability(Availability value) { availability = value == null ? Availability.CHECKING : value; return this; }
        public Builder input(InputSlot value) { if(value != null) inputs.add(value); return this; }
        public Builder requirement(Requirement value) { if(value != null) requirements.add(value); return this; }
        public Builder bonus(Bonus value) { if(value != null) bonuses.add(value); return this; }
        public Builder category(String value) { if(value != null && !value.trim().isEmpty()) categories.add(value); return this; }
        public Builder description(String value) { description = value; return this; }
        public CraftAtlasEntry build() { return new CraftAtlasEntry(this); }
    }

    public static final class InputSlot {
        public final int quantity;
        public final boolean optional;
        public final List<IngredientOption> options;

        public InputSlot(int quantity, boolean optional, List<IngredientOption> options) {
            if(quantity < 1) throw new IllegalArgumentException("quantity must be positive");
            if(options == null || options.isEmpty()) throw new IllegalArgumentException("input options must not be empty");
            this.quantity = quantity;
            this.optional = optional;
            this.options = immutable(options);
        }
    }

    public static final class IngredientOption {
        public final String resource;
        public final String name;

        public IngredientOption(String resource, String name) {
            this.resource = resource;
            this.name = name == null || name.trim().isEmpty() ? resource : name;
        }
    }

    public static final class Requirement {
        public final RequirementKind kind;
        public final String resource;
        public final String name;
        public final String description;

        public Requirement(RequirementKind kind, String resource, String name, String description) {
            if(kind == null) throw new IllegalArgumentException("kind must not be null");
            this.kind = kind;
            this.resource = resource;
            this.name = name == null || name.trim().isEmpty() ? resource : name;
            this.description = description;
        }
    }

    public static final class Bonus {
        public final String attributeResource;
        public final String name;
        public final Double value;

        public Bonus(String attributeResource, String name, Double value) {
            this.attributeResource = attributeResource;
            this.name = name == null || name.trim().isEmpty() ? attributeResource : name;
            this.value = value;
        }
    }
}
