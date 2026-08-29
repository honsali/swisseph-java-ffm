package org.swisseph.ffm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Argument checks.
 *
 * <p>Swiss Ephemeris is C code that does not defend itself: a {@code NaN}
 * Julian day makes its search loops run to their iteration cap and a bad
 * latitude produces silently wrong houses. Everything that reaches native
 * memory is checked first, and these tests pin those checks down.</p>
 */
class ValidationTest {

    @Test
    void geographicPositionsRejectNonFiniteAndOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new GeographicPosition(Double.NaN, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new GeographicPosition(Double.POSITIVE_INFINITY, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new GeographicPosition(0.0, Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new GeographicPosition(0.0, 0.0, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new GeographicPosition(180.1, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new GeographicPosition(-180.1, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new GeographicPosition(0.0, 90.1, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new GeographicPosition(0.0, -90.1, 0.0));
    }

    @Test
    void geographicPositionsAcceptTheirBoundaries() {
        assertEquals(180.0, new GeographicPosition(180.0, 90.0, 0.0).longitude());
        assertEquals(-90.0, new GeographicPosition(-180.0, -90.0, -430.0).latitude());
        assertEquals(0.0, GeographicPosition.of(0.0, 0.0).altitudeMeters());
    }

    @Test
    void atmosphericConditionsRejectImpossiblePhysics() {
        assertThrows(IllegalArgumentException.class,
                () -> new AtmosphericConditions(Double.NaN, 15.0));
        assertThrows(IllegalArgumentException.class,
                () -> new AtmosphericConditions(-1.0, 15.0));
        assertThrows(IllegalArgumentException.class,
                () -> new AtmosphericConditions(1013.25, Double.NaN));
        // Below absolute zero.
        assertThrows(IllegalArgumentException.class,
                () -> new AtmosphericConditions(1013.25, -273.15));
        assertThrows(IllegalArgumentException.class,
                () -> new AtmosphericConditions(1013.25, -300.0));
    }

    @Test
    void atmosphericConditionsKeepZeroPressureMeaningful() {
        // Swiss Ephemeris reads pressure 0 as "derive it from the observer altitude",
        // so it must stay a legal value rather than be rejected as out of range.
        assertTrue(AtmosphericConditions.FROM_ALTITUDE.derivesPressureFromAltitude());
        assertFalse(AtmosphericConditions.STANDARD.derivesPressureFromAltitude());
        assertEquals(1013.25, AtmosphericConditions.STANDARD.pressureMillibar());
    }

    @Test
    void calendarRecordsRejectImpossibleFields() {
        assertThrows(IllegalArgumentException.class, () -> new CivilDate(2000, 13, 1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new CivilDate(2000, 1, 32, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new CivilDate(2000, 1, 1, Double.NaN));

        assertThrows(IllegalArgumentException.class,
                () -> new UtcDateTime(2000, 1, 1, 24, 0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new UtcDateTime(2000, 1, 1, 0, 60, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new UtcDateTime(2000, 1, 1, 0, 0, -0.1));
        assertThrows(IllegalArgumentException.class,
                () -> new UtcDateTime(2000, 1, 1, 0, 0, 61.0));
    }

    @Test
    void leapSecondsRemainRepresentable() {
        // 1998-12-31T23:59:60Z was a real leap second, so 60 must be accepted.
        assertEquals(60.0, new UtcDateTime(1998, 12, 31, 23, 59, 60.0).second());
    }

    @Test
    void julianDayBoundsBracketTheSwissEphemerisRange() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.julianDay(Double.NaN, "jd"));
        assertThrows(IllegalArgumentException.class,
                () -> Validation.julianDay(Double.POSITIVE_INFINITY, "jd"));
        assertThrows(IllegalArgumentException.class, () -> Validation.julianDay(1e300, "jd"));
        assertThrows(IllegalArgumentException.class, () -> Validation.julianDay(-1e300, "jd"));

        // The bounds sit outside the data range, so the library still gets to
        // report genuine range errors itself.
        assertEquals(2_451_545.0, Validation.julianDay(2_451_545.0, "jd"));
        assertEquals(-3_027_216.0, Validation.julianDay(-3_027_216.0, "jd"));
        assertEquals(7_857_132.0, Validation.julianDay(7_857_132.0, "jd"));
    }

    @Test
    void planetaryMoonIdentifiersCannotReachTheAsteroidRange() {
        // The two offsets are only 1000 apart: planetaryMoon(1433) would be 10433,
        // which is asteroid(433), Eros.
        assertEquals(9_001, CelestialBody.planetaryMoon(1));
        assertEquals(9_999, CelestialBody.planetaryMoon(999));
        assertThrows(IllegalArgumentException.class, () -> CelestialBody.planetaryMoon(1000));
        assertThrows(IllegalArgumentException.class, () -> CelestialBody.planetaryMoon(1433));
        assertThrows(IllegalArgumentException.class,
                () -> CelestialBody.planetaryMoon(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> CelestialBody.asteroid(Integer.MAX_VALUE));
    }

    @Test
    void theBuilderAppliesTheSameSiderealRulesAsTheSetters() {
        SwissEphConfig.Builder builder =
                SwissEphConfig.builder().library(java.nio.file.Path.of("libswe.so"));

        // open(config) pushes the settings itself, so a rule enforced only in the
        // setter would be bypassed by the configuration path.
        assertThrows(IllegalArgumentException.class,
                () -> builder.siderealMode(SiderealMode.USER));
        assertThrows(IllegalArgumentException.class,
                () -> builder.siderealMode(-1, 0.0, 0.0));

        assertEquals(SiderealMode.LAHIRI.value(),
                builder.siderealMode(SiderealMode.LAHIRI).build().siderealMode().orElseThrow());
        // USER is reachable through the raw form, which does take t0.
        assertEquals(SiderealMode.USER.value(),
                builder.siderealMode(SiderealMode.USER.value(), 2_451_545.0, 24.0)
                        .build().siderealMode().orElseThrow());
    }

    @Test
    void eclipseAltitudesAreBoundedTheWayTheNativeRoutinesBoundThem() {
        assertEquals(-500.0,
                Validation.eclipseObserver(new GeographicPosition(0, 0, -500.0)).altitudeMeters());
        assertEquals(25_000.0,
                Validation.eclipseObserver(new GeographicPosition(0, 0, 25_000.0)).altitudeMeters());
        assertThrows(IllegalArgumentException.class,
                () -> Validation.eclipseObserver(new GeographicPosition(0, 0, 25_001.0)));
        assertThrows(IllegalArgumentException.class,
                () -> Validation.eclipseObserver(new GeographicPosition(0, 0, -501.0)));
    }

    @Test
    void aDerivedPressureIsRefusedWhereTheBarometricModelBreaks() {
        // atpress == 0 makes the library compute 1013.25 * pow(1 - 0.0065*h/288, 5.255).
        // The base goes negative above 288/0.0065, and pow() then returns NaN.
        GeographicPosition tooHigh = new GeographicPosition(0, 0, 45_000.0);
        assertThrows(IllegalArgumentException.class,
                () -> Validation.pressureModel(tooHigh, AtmosphericConditions.FROM_ALTITUDE));

        // An explicit pressure never touches that model, so the same altitude is fine.
        Validation.pressureModel(tooHigh, AtmosphericConditions.STANDARD);
        Validation.pressureModel(new GeographicPosition(0, 0, 8_848.0),
                AtmosphericConditions.FROM_ALTITUDE);
    }

    @Test
    void configRejectsAnEmptySupportedVersionSet() {
        assertThrows(IllegalArgumentException.class,
                () -> SwissEphConfig.builder().supportedVersions());
    }

    @Test
    void configDefaultsToRejectingUnknownNativeVersions() {
        assertEquals(NativeVersionPolicy.REJECT,
                SwissEphConfig.builder().library(java.nio.file.Path.of("libswe.so"))
                        .build().versionPolicy());
        assertTrue(SwissEphConfig.DEFAULT_SUPPORTED_VERSIONS.contains("2.10.03"));
    }
}
