package nurgling.tools;

import nurgling.widgets.Specialisation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuelZonesTest {
    @Test
    void registersEveryStationAndKeepsUnknownNamesOutOfTheRegistry() {
        assertEquals(9, FuelZones.all.size());
        for (Specialisation.SpecName station : Arrays.asList(
                Specialisation.SpecName.fuelSmelter, Specialisation.SpecName.fuelSteelbox,
                Specialisation.SpecName.fuelFforge, Specialisation.SpecName.fuelKiln,
                Specialisation.SpecName.fuelOven, Specialisation.SpecName.fuelCauldron,
                Specialisation.SpecName.fuelFireplace, Specialisation.SpecName.fuelCrucible,
                Specialisation.SpecName.fuelTarkiln))
            assertTrue(FuelZones.of(station) != null, station.toString());
        assertNull(FuelZones.of(Specialisation.SpecName.fuel));
    }

    @Test
    void stationZonesUseTheSameFuelChoicesIncludingBoards() {
        for (FuelZones.Zone zone : FuelZones.all) {
            assertEquals(SpecialisationData.data.get("fuel"),
                    SpecialisationData.data.get(zone.spec.toString()));
            assertTrue(SpecialisationData.data.get(zone.spec.toString()).contains("Board"));
        }
    }

    @Test
    void descriptionsMakeTheSpecificAndGenericFallbackClear() {
        assertEquals("Fuel: Kiln (Branch)",
                FuelZones.describe(Specialisation.SpecName.fuelKiln, "Branch"));
        assertEquals("Fuel (Coal)", FuelZones.describe(null, "Coal"));
    }

    @Test
    void resolutionPriorityIsSpecificLocalThenGlobalThenGenericLocalThenGlobal() {
        assertEquals("station-local", FuelZones.firstAvailable("station-local", "station-global", "fuel-local", "fuel-global"));
        assertEquals("station-global", FuelZones.firstAvailable(null, "station-global", "fuel-local", "fuel-global"));
        assertEquals("fuel-local", FuelZones.firstAvailable(null, null, "fuel-local", "fuel-global"));
        assertEquals("fuel-global", FuelZones.firstAvailable(null, null, null, "fuel-global"));
    }
}
