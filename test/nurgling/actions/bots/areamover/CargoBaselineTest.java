package nurgling.actions.bots.areamover;

import haven.GItem;
import haven.WItem;
import nurgling.ClientResourceFixture;
import nurgling.NConfig;
import nurgling.NGItem;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CargoBaselineTest {
    @Test void originalItemRemainsProtectedWhenItsWrapperChanges() throws Exception {
        NConfig.getGlobalInstance();
        try(ClientResourceFixture fixture = new ClientResourceFixture()) {
            GItem original = allocate(NGItem.class);
            WItem initial = wrapper(original);
            WItem rewrapped = wrapper(original);
            WItem picked = wrapper(allocate(NGItem.class));
            Cargo cargo = new Cargo(List.of(initial));

            assertEquals(List.of(picked), cargo.carriedItems(List.of(rewrapped, picked)));
            assertEquals(List.of(picked), cargo.carriedItems(List.of(picked)));
        }
    }

    @Test void eachNewStackLeafIsCargoWhileEveryOriginalLeafIsProtected() throws Exception {
        NConfig.getGlobalInstance();
        try(ClientResourceFixture fixture = new ClientResourceFixture()) {
            WItem first = wrapper(allocate(NGItem.class));
            WItem second = wrapper(allocate(NGItem.class));
            WItem addedOne = wrapper(allocate(NGItem.class));
            WItem addedTwo = wrapper(allocate(NGItem.class));
            Cargo cargo = new Cargo(List.of(first, second));

            assertEquals(List.of(addedOne, addedTwo), cargo.carriedItems(List.of(first, addedOne, second, addedTwo)));
            assertEquals(List.of(), cargo.carriedItems(List.of(first, second)));
        }
    }

    private static WItem wrapper(GItem item) throws Exception {
        WItem widget = allocate(WItem.class);
        Field field = WItem.class.getDeclaredField("item");
        field.setAccessible(true);
        field.set(widget, item);
        return widget;
    }

    @Test void recreatedPersonalLeafKeepsEveryAmbiguousSameNameItem() throws Exception {
        NConfig.getGlobalInstance();
        try(ClientResourceFixture fixture = new ClientResourceFixture()) {
            Cargo cargo = new Cargo(List.of(named("Stone", 100f)));
            WItem newLow = named("Stone", 10f);
            WItem recreated = named("Stone", 100f);
            assertEquals(List.of(), cargo.carriedItems(List.of(newLow, recreated)));
        }
    }

    @Test void existingPersonalIdentityDoesNotReserveAnotherNewLeaf() throws Exception {
        NConfig.getGlobalInstance();
        try(ClientResourceFixture fixture = new ClientResourceFixture()) {
            WItem personal = named("Stone", 100f);
            WItem added = named("Stone", 100f);
            Cargo cargo = new Cargo(List.of(personal));
            assertEquals(List.of(added), cargo.carriedItems(List.of(added, personal)));
        }
    }

    @Test void delayedQualityStillReservesPersonalSameNameQuantity() throws Exception {
        NConfig.getGlobalInstance();
        try(ClientResourceFixture fixture = new ClientResourceFixture()) {
            Cargo cargo = new Cargo(List.of(named("Stone", null), named("Stone", 100f)));
            WItem first = named("Stone", 10f);
            WItem second = named("Stone", 100f);
            WItem third = named("Stone", 20f);
            assertEquals(List.of(), cargo.carriedItems(List.of(first, second, third)));
        }
    }

    @Test void unresolvedPersonalQualityCannotUnloadTheRecreatedLeaf() throws Exception {
        NConfig.getGlobalInstance();
        try(ClientResourceFixture fixture = new ClientResourceFixture()) {
            Cargo cargo = new Cargo(List.of(named("Stone", 10f)));
            WItem fresh = named("Stone", 20f);
            WItem pendingPersonal = named("Stone", null);
            assertEquals(List.of(), cargo.carriedItems(List.of(fresh, pendingPersonal)));
        }
    }

    @Test void sameQualityFreshCargoCannotConsumeARecreatedPersonalReservation() throws Exception {
        NConfig.getGlobalInstance();
        try(ClientResourceFixture fixture = new ClientResourceFixture()) {
            Cargo cargo = new Cargo(List.of(named("Stone", 10f)));
            WItem fresh = named("Stone", 10f);
            WItem pendingPersonal = named("Stone", null);
            WItem unrelated = named("Branch", 10f);
            assertEquals(List.of(unrelated), cargo.carriedItems(List.of(fresh, pendingPersonal, unrelated)));
        }
    }

    private static WItem named(String name, Float quality) throws Exception {
        NGItem item = allocate(NGItem.class);
        Field field = NGItem.class.getDeclaredField("name");
        field.setAccessible(true);
        field.set(item, name);
        item.quality = quality;
        return wrapper(item);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe)field.get(null)).allocateInstance(type));
    }
}
