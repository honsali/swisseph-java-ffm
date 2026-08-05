package org.swisseph.ffm;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SwissEphIntegrationTest {
    private static final String NATIVE_TEST_LIBRARY = "swisseph.integration.library";
    private static final String NATIVE_TEST_EPHEMERIS = "swisseph.integration.ephemeris";

    @Test
    void callsSwissEphemerisThroughFfm() {
        String libraryPath = System.getProperty(NATIVE_TEST_LIBRARY);
        assumeTrue(libraryPath != null && !libraryPath.isBlank(),
                () -> "Set -D" + NATIVE_TEST_LIBRARY + "=<native-library> to run this test");

        try (SwissEph swe = SwissEph.load(Path.of(libraryPath))) {
            assertEquals(SwissEph.EXPECTED_NATIVE_VERSION, swe.version());

            String ephemerisPath = System.getProperty(NATIVE_TEST_EPHEMERIS);
            if (ephemerisPath != null && !ephemerisPath.isBlank()) {
                swe.setEphemerisPath(Path.of(ephemerisPath));
            }

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

            PlanetaryPhenomena moonPhenomena = swe.phenomenaUt(
                    j2000, CelestialBody.MOON, CalculationFlag.MOSHIER_EPHEMERIS);
            assertTrue(moonPhenomena.phaseAngle() >= 0.0);
            assertTrue(moonPhenomena.illuminatedFraction() >= 0.0
                    && moonPhenomena.illuminatedFraction() <= 1.0);
            assertTrue(moonPhenomena.apparentDiameter() > 0.0);
            assertTrue(Double.isFinite(moonPhenomena.apparentMagnitude()));

            if (ephemerisPath != null && !ephemerisPath.isBlank()) {
                FixedStarPosition sirius = swe.fixedStarUt(
                        j2000, "Sirius", CalculationFlag.MOSHIER_EPHEMERIS);
                assertTrue(!sirius.name().isBlank());
                assertTrue(Double.isFinite(sirius.position().longitude()));
            }

            assertTrue(swe.deltaT(j2000) > 0.0);

            HouseCusps houses = swe.housesEx(
                    j2000, 0.0, 0.0, HouseSystem.PLACIDUS);
            assertEquals(13, houses.cusps().length);
            assertTrue(houses.cusp(1) >= 0.0 && houses.cusp(1) < 360.0);

            GeographicPosition greenwich = new GeographicPosition(0.0, 0.0, 0.0);
            HorizontalCoordinates horizontal = swe.azimuthAltitude(
                    j2000,
                    HorizontalCoordinateType.ECLIPTIC,
                    greenwich,
                    0.0,
                    15.0,
                    sun.longitude(),
                    sun.latitude(),
                    sun.distance());
            assertTrue(Double.isFinite(horizontal.azimuth()));
            assertTrue(Double.isFinite(horizontal.trueAltitude()));

            RiseTransitResult sunrise = swe.riseTransit(
                    j2000,
                    CelestialBody.SUN.id(),
                    CalculationFlag.MOSHIER_EPHEMERIS.value(),
                    RiseTransitFlag.RISE.value(),
                    greenwich,
                    0.0,
                    15.0);
            assertTrue(sunrise.found());
            assertTrue(sunrise.julianDayUt() >= j2000);

            int moshier = CalculationFlag.MOSHIER_EPHEMERIS.value();
            EclipseResult solarGlobal = swe.solarEclipseWhenGlobal(j2000, moshier, 0, false);
            assertTrue(solarGlobal.time(0) > j2000);
            EclipseResult solarWhere = swe.solarEclipseWhere(solarGlobal.time(0), moshier);
            GeographicPosition solarMaximum = new GeographicPosition(
                    solarWhere.longitude(), solarWhere.latitude(), 0.0);
            assertTrue(swe.solarEclipseHow(solarGlobal.time(0), moshier, solarMaximum).flags() > 0);
            assertTrue(swe.solarEclipseWhenLocal(j2000, moshier, solarMaximum, false).time(0) > j2000);

            EclipseResult lunarGlobal = swe.lunarEclipseWhen(j2000, moshier, 0, false);
            assertTrue(lunarGlobal.time(0) > j2000);
            assertTrue(swe.lunarEclipseHow(lunarGlobal.time(0), moshier, greenwich).flags() > 0);
            assertTrue(swe.lunarEclipseWhenLocal(j2000, moshier, greenwich, false).time(0) > j2000);
        }
    }
}
