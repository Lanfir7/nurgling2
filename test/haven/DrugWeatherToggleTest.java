package haven;

import nurgling.NConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DrugWeatherToggleTest {
    private NConfig previous;

    @BeforeEach void config() {
        previous = NConfig.current;
        NConfig.current = new NConfig();
    }

    @AfterEach void restoreConfig() {
        NConfig.current = previous;
    }

    private static class Effect implements Glob.Weather {
        final String name;
        Effect(String name) { this.name = name; }
    }

    private static class FixtureGlob extends Glob {
        final List<String> constructed = new ArrayList<>();
        FixtureGlob() { super(null); }
        @Override Glob.Weather instantiateWeather(Indir<Resource> res, Object[] args) {
            String name = res.get().name;
            constructed.add(name);
            return new Effect(name);
        }
    }

    private static Indir<Resource> resource(String name) {
        Resource res = new Resource.Virtual(null, name, 0);
        return () -> res;
    }

    @SuppressWarnings("unchecked")
    private static Map<Indir<Resource>, Object> entries(Glob glob) throws Exception {
        Field map = Glob.class.getDeclaredField("wmap");
        map.setAccessible(true);
        return (Map<Indir<Resource>, Object>) map.get(glob);
    }

    @Test void allFourExactDrugsHideCachedAndPendingThenReturnWithoutRecreation() throws Exception {
        FixtureGlob glob = new FixtureGlob();
        Map<Indir<Resource>, Object> map = entries(glob);
        String[] drugs = {"gfx/fx/shroomed", "gfx/fx/dragon", "gfx/fx/lucy", "gfx/fx/bottle"};
        List<Effect> cached = new ArrayList<>();
        for (int i = 0; i < drugs.length; i++) {
            Indir<Resource> key = resource(drugs[i]);
            if (i % 2 == 0) {
                Effect effect = new Effect(drugs[i]);
                cached.add(effect);
                map.put(key, effect);
            } else {
                map.put(key, new Object[]{i});
            }
        }
        Effect lookalike = new Effect("gfx/fx/dragonfly");
        map.put(resource(lookalike.name), lookalike);
        map.put(resource("gfx/fx/snow"), new Effect("gfx/fx/snow"));
        map.put(resource("gfx/fx/clouds"), new Effect("gfx/fx/clouds"));

        assertEquals(3, glob.weather().size(), "the default setting hides exact drug effects before factory work");
        assertTrue(glob.constructed.isEmpty());
        NConfig.set(NConfig.Key.disableDrugEffects, false);
        Collection<Glob.Weather> visible = glob.weather();
        assertEquals(7, visible.size());
        assertTrue(visible.containsAll(cached), "cached effects are reused when enabled");
        assertEquals(2, glob.constructed.size(), "only the two raw effects need factory creation");
        NConfig.set(NConfig.Key.disableDrugEffects, true);
        assertEquals(3, glob.weather().size());
        assertTrue(map.values().containsAll(cached), "filtering does not delete server weather state");
        NConfig.set(NConfig.Key.disableDrugEffects, false);
        assertEquals(7, glob.weather().size());
        assertEquals(2, glob.constructed.size(), "reenabling uses the same instances");
    }

    @Test void loadingIsDeferredOnlyWhenDrugClassificationIsNeeded() throws Exception {
        FixtureGlob glob = new FixtureGlob();
        AtomicInteger attempts = new AtomicInteger();
        Indir<Resource> loading = () -> {
            attempts.incrementAndGet();
            throw new Loading("weather resource pending");
        };
        Effect cached = new Effect("unresolved");
        Map<Indir<Resource>, Object> map = entries(glob);
        map.put(loading, cached);
        assertTrue(glob.weather().isEmpty());
        assertEquals(1, attempts.get());
        map.put(loading, new Object[]{1});
        assertTrue(glob.weather().isEmpty());
        assertEquals(2, attempts.get());
        assertTrue(glob.constructed.isEmpty());

        NConfig.set(NConfig.Key.disableDrugEffects, false);
        map.put(loading, cached);
        assertSame(cached, glob.weather().iterator().next());
        assertEquals(2, attempts.get(), "without either filter cached weather needs no resource lookup");
        map.put(loading, new Object[]{1});
        assertTrue(glob.weather().isEmpty(), "the pre-existing factory Loading retry stays pending");
        assertEquals(3, attempts.get());
    }

    @Test void rainAndDrugTogglesAreIndependentAndFlatWorldIsNotChanged() throws Exception {
        FixtureGlob glob = new FixtureGlob();
        Map<Indir<Resource>, Object> map = entries(glob);
        for (String name : new String[]{"gfx/fx/rain", "gfx/fx/wet", "sfx/ambient/weather/wsound",
                "gfx/fx/snow", "gfx/fx/clouds", "gfx/fx/dragon", "gfx/fx/dragons"})
            map.put(resource(name), new Effect(name));
        Object originalFlat = NConfig.get(NConfig.Key.flatsurface);
        assertEquals(6, glob.weather().size());
        glob.toggleRain();
        assertEquals(3, glob.weather().size(), "snow, clouds and a similarly named effect remain");
        NConfig.set(NConfig.Key.disableDrugEffects, false);
        assertEquals(4, glob.weather().size(), "reenabling drugs must not reenable rain or its ambient sound");
        glob.toggleRain();
        assertEquals(7, glob.weather().size());
        assertEquals(originalFlat, NConfig.get(NConfig.Key.flatsurface));
        for (boolean flat : new boolean[]{true, false}) {
            NConfig.set(NConfig.Key.flatsurface, flat);
            NConfig.set(NConfig.Key.disableDrugEffects, true);
            assertEquals(6, glob.weather().size());
            assertEquals(flat, NConfig.get(NConfig.Key.flatsurface));
            assertEquals(true, NConfig.get(NConfig.Key.disableDrugEffects));
            NConfig.set(NConfig.Key.disableDrugEffects, false);
            assertEquals(7, glob.weather().size());
            assertEquals(flat, NConfig.get(NConfig.Key.flatsurface));
            assertEquals(false, NConfig.get(NConfig.Key.disableDrugEffects));
        }
    }
}
