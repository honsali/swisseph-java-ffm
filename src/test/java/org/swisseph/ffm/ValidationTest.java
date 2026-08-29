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
