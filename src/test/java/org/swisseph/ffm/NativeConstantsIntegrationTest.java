package org.swisseph.ffm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-checks the Java constant tables against the loaded library.
 *
 * <p>Numbering tables copied out of a C header are the classic place for a
 * binding to rot: nothing fails to compile when an entry drifts, and the only
 * symptom is an ayanamsha or a house system quietly computing the wrong thing.
 * Swiss Ephemeris can name its own constants, so these tests ask it to, rather
 * than trusting what was transcribed.</p>
 */
class NativeConstantsIntegrationTest {

    @Test
    @DisplayName("Every sidereal mode names a distinct ayanamsha in the native library")
    void siderealModesAreNamedAndDistinct() {
        try (SwissEph swe = NativeTestSupport.open()) {
            Map<String, SiderealMode> namesSeen = new HashMap<>();

            for (SiderealMode mode : SiderealMode.values()) {
                if (mode == SiderealMode.USER) {
                    // A user-defined ayanamsha has no predefined name.
                    continue;
                }
                String name = swe.ayanamsaName(mode);
                assertFalse(name.isBlank(),
                        mode + " (value " + mode.value() + ") is unknown to this library");

                SiderealMode clash = namesSeen.put(name, mode);
                assertNull(clash, mode + " and " + clash + " both map to the ayanamsha \""
                        + name + "\", so the numbering has drifted");
            }

            // Spot-check two entries whose names are stable across releases.
            assertTrue(swe.ayanamsaName(SiderealMode.LAHIRI).contains("Lahiri"),
                    "mode 1 should be Lahiri but was " + swe.ayanamsaName(SiderealMode.LAHIRI));
            assertTrue(swe.ayanamsaName(SiderealMode.FAGAN_BRADLEY).contains("Fagan"),
                    "mode 0 should be Fagan/Bradley but was "
                            + swe.ayanamsaName(SiderealMode.FAGAN_BRADLEY));
        }
    }

    @Test
    @DisplayName("Every house system is recognised by the native library")
    void houseSystemsAreRecognised() {
        try (SwissEph swe = NativeTestSupport.open()) {
            // swe_house_name() answers "Placidus" for anything it does not know, so
            // a wrong code letter shows up as that default coming back for a system
            // that is not Placidus.
            for (HouseSystem system : HouseSystem.values()) {
                String name = swe.houseName(system);
                assertFalse(name.isBlank(),
                        system + " (code '" + system.code() + "') has no name");
                if (system != HouseSystem.PLACIDUS) {
                    assertFalse(name.equals("Placidus"),
                            system + " (code '" + system.code() + "') fell through to the "
                                    + "Placidus default, so its code letter is wrong");
                }
            }

            assertTrue(swe.houseName(HouseSystem.PLACIDUS).contains("Placidus"));
            assertTrue(swe.houseName(HouseSystem.KOCH).contains("Koch"));
            // The two Sunshine variants differ only by letter case, which is exactly
            // the kind of detail a transcription loses.
            assertFalse(swe.houseName(HouseSystem.SUNSHINE_TREINDL)
                            .equals(swe.houseName(HouseSystem.SUNSHINE_MAKRANSKY)),
                    "'I' and 'i' must select different systems");
        }
    }

    @Test
    @DisplayName("Every standard body is named by the native library")
    void celestialBodiesAreNamed() {
        try (SwissEph swe = NativeTestSupport.open()) {
            for (CelestialBody body : CelestialBody.values()) {
                if (body == CelestialBody.ECLIPTIC_NUTATION) {
                    // -1 is a request for obliquity and nutation, not a body.
                    continue;
                }
                String name = swe.bodyName(body);
                assertFalse(name.isBlank(), body + " has no name in this library");
                assertFalse(name.startsWith("?"),
                        body + " (id " + body.id() + ") is unknown to this library: " + name);
            }

            assertEquals("Chiron", swe.bodyName(CelestialBody.CHIRON));
            assertEquals("mean Node", swe.bodyName(CelestialBody.MEAN_NODE));
        }
    }

    @Test
    @DisplayName("Requested and returned calculation flags use the same bit layout")
    void calculationFlagBitsRoundTripThroughTheLibrary() {
        try (SwissEph swe = NativeTestSupport.open()) {
            // The library echoes the bits it honoured, so asking for a distinctive
            // combination and reading it back proves both directions of the mapping.
            EphemerisPosition equatorial = swe.calculateUt(2_451_545.0, CelestialBody.SUN,
                    CalculationFlag.MOSHIER_EPHEMERIS,
                    CalculationFlag.SPEED,
                    CalculationFlag.EQUATORIAL);

            ReturnedFlags flags = equatorial.returnedFlags();
            assertTrue(flags.has(CalculationFlag.MOSHIER_EPHEMERIS));
            assertTrue(flags.has(CalculationFlag.SPEED));
            assertFalse(flags.has(CalculationFlag.HELIOCENTRIC));

            // Equatorial coordinates put the Sun's declination inside the obliquity,
            // which is a different number from the ecliptic latitude of zero.
            assertTrue(Math.abs(equatorial.secondCoordinate()) < 23.5);

            EphemerisPosition ecliptic = swe.calculateUt(2_451_545.0, CelestialBody.SUN,
                    CalculationFlag.MOSHIER_EPHEMERIS);
            assertTrue(Math.abs(ecliptic.latitude()) < 0.01,
                    "the Sun sits on the ecliptic by definition");
        }
    }

    @Test
    @DisplayName("Radians and degrees differ by exactly the conversion factor")
    void radianFlagChangesTheUnit() {
        try (SwissEph swe = NativeTestSupport.open()) {
            EphemerisPosition degrees = swe.calculateUt(
                    2_451_545.0, CelestialBody.JUPITER, CalculationFlag.MOSHIER_EPHEMERIS);
            EphemerisPosition radians = swe.calculateUt(2_451_545.0, CelestialBody.JUPITER,
                    CalculationFlag.MOSHIER_EPHEMERIS, CalculationFlag.RADIANS);

            assertEquals(Math.toRadians(degrees.longitude()), radians.longitude(), 1.0e-12);
        }
    }
}
