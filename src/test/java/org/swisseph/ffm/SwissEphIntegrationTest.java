package org.swisseph.ffm;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SwissEphIntegrationTest {
    private static final String NATIVE_TEST_LIBRARY = "swisseph.integration.library";

    @Test
    void callsSwissEphemerisThroughFfm() {
        String libraryPath = System.getProperty(NATIVE_TEST_LIBRARY);
        assumeTrue(libraryPath != null && !libraryPath.isBlank(),
                () -> "Set -D" + NATIVE_TEST_LIBRARY + "=<native-library> to run this test");

        try (SwissEph swe = SwissEph.load(Path.of(libraryPath))) {
            assertEquals(SwissEph.EXPECTED_NATIVE_VERSION, swe.version());

            double j2000 = swe.julianDay(2000, 1, 1, 12.0, CalendarType.GREGORIAN);
            assertEquals(2_451_545.0, j2000, 1.0e-9);
            assertEquals(new CivilDate(2000, 1, 1, 12.0),
                    swe.reverseJulianDay(j2000, CalendarType.GREGORIAN));

            EphemerisPosition sun = swe.calculateUt(
                    j2000,
                    CelestialBody.SUN,
                    CalculationFlag.MOSHIER_EPHEMERIS,
                    CalculationFlag.SPEED);

            assertTrue(sun.longitude() >= 0.0 && sun.longitude() < 360.0);
            assertTrue((sun.returnedFlags() & CalculationFlag.MOSHIER_EPHEMERIS.value()) != 0);
            assertTrue((sun.returnedFlags() & CalculationFlag.SPEED.value()) != 0);
        }
    }
}
