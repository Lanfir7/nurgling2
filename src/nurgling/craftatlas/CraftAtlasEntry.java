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
    public final Gilding gilding;
    public final Curiosity curiosity;
    public final List<AttributeRef> qualityModifiers;
    public final List<String> equipmentSlots;
    public final List<String> categories;
    public final String description;
    public final boolean inputsObserved;

    private CraftAtlasEntry(Builder b) {
        recipeResource = required(b.recipeResource, "recipeResource");
        displayName = b.displayName == null || b.displayName.trim().isEmpty() ? recipeResource : b.displayName;
        outputResource = b.outputResource;
        availability = b.availability;
        inputs = immutable(b.inputs);
        requirements = immutable(b.requirements);
        bonuses = immutable(b.bonuses);
        gilding = b.gilding;
        curiosity = b.curiosity;
        qualityModifiers = immutable(b.qualityModifiers);
        equipmentSlots = immutable(b.equipmentSlots);
        categories = immutable(b.categories);
        description = b.description;
        inputsObserved = b.inputsObserved;
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
        private Gilding gilding;
        private Curiosity curiosity;
        private final List<AttributeRef> qualityModifiers = new ArrayList<>();
        private final List<String> equipmentSlots = new ArrayList<>();
        private final List<String> categories = new ArrayList<>();
        private String description;
        private boolean inputsObserved;

        private Builder(String recipeResource, String displayName) {
            this.recipeResource = recipeResource;
            this.displayName = displayName;
        }

        public Builder output(String resource) { outputResource = resource; return this; }
        public Builder availability(Availability value) { availability = value == null ? Availability.CHECKING : value; return this; }
        public Builder input(InputSlot value) { if(value != null) inputs.add(value); return this; }
        public Builder requirement(Requirement value) { if(value != null) requirements.add(value); return this; }
        public Builder bonus(Bonus value) { if(value != null) bonuses.add(value); return this; }
        public Builder gilding(Gilding value) { gilding = value; return this; }
        public Builder curiosity(Curiosity value) { curiosity = value; return this; }
        public Builder qualityModifier(AttributeRef value) { if(value != null) qualityModifiers.add(value); return this; }
        public Builder equipmentSlot(String value) { if(value != null && !value.trim().isEmpty()) equipmentSlots.add(value); return this; }
        public Builder category(String value) { if(value != null && !value.trim().isEmpty()) categories.add(value); return this; }
        public Builder description(String value) { description = value; return this; }
        public Builder inputsObserved(boolean value) { inputsObserved = value; return this; }
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

    public static final class AttributeRef {
        public final String resource, name;

        public AttributeRef(String resource, String name) {
            this.resource = resource;
            this.name = name == null || name.trim().isEmpty() ? resource : name;
        }
    }

    public static final class Gilding {
        public final double pmin, pmax;
        public final List<AttributeRef> attributes;

        public Gilding(double pmin, double pmax, List<AttributeRef> attributes) {
            this.pmin = Math.max(0, Math.min(1, pmin));
            this.pmax = Math.max(this.pmin, Math.min(1, pmax));
            this.attributes = immutable(attributes == null ? Collections.<AttributeRef>emptyList() : attributes);
        }
    }

    /** Baseline study values from the item tooltip/wiki. Study time is stored in real minutes. */
    public static final class Curiosity {
        public final int learningPoints;
        public final int studyMinutes;
        public final int mentalWeight;

        public Curiosity(int learningPoints, int studyMinutes, int mentalWeight) {
            this.learningPoints = Math.max(0, learningPoints);
            this.studyMinutes = Math.max(0, studyMinutes);
            this.mentalWeight = Math.max(0, mentalWeight);
        }

        public double studyHours() { return studyMinutes / 60.0; }
        public int learningPoints(double quality) {
            return (int)Math.round(learningPoints * Math.sqrt(Math.max(1.0, quality) / 10.0));
        }
        public double lpPerHour() {
            return studyMinutes <= 0 ? Double.NaN : learningPoints * 60.0 / studyMinutes;
        }
        public double lpPerHour(double quality) {
            return studyMinutes <= 0 ? Double.NaN : learningPoints(quality) * 60.0 / studyMinutes;
        }
        public double lpPerHourPerWeight() {
            return mentalWeight <= 0 ? Double.NaN : lpPerHour() / mentalWeight;
        }
        public double lpPerHourPerWeight(double quality) {
            return mentalWeight <= 0 ? Double.NaN : lpPerHour(quality) / mentalWeight;
        }
    }
}
