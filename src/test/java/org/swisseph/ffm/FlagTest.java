package org.swisseph.ffm;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bit values and mask arithmetic, checked against the constants in {@code swephexp.h}. */
class FlagTest {

    @Test
    void calculationFlagValuesMatchTheHeader() {
        assertEquals(1, CalculationFlag.JPL_EPHEMERIS.value());
        assertEquals(2, CalculationFlag.SWISS_EPHEMERIS.value());
        assertEquals(4, CalculationFlag.MOSHIER_EPHEMERIS.value());
        assertEquals(256, CalculationFlag.SPEED.value());
        assertEquals(2048, CalculationFlag.EQUATORIAL.value());
        assertEquals(32768, CalculationFlag.TOPOCENTRIC.value());
        assertEquals(65536, CalculationFlag.SIDEREAL.value());
        assertEquals(1048576, CalculationFlag.CENTER_OF_BODY.value());
    }

    @Test
    void everyCalculationFlagIsASingleDistinctBit() {
        int seen = 0;
        for (CalculationFlag flag : CalculationFlag.values()) {
            int value = flag.value();
            assertEquals(1, Integer.bitCount(value), flag + " must be a single bit");
            assertEquals(0, seen & value, flag + " reuses a bit already taken");
            seen |= value;
        }
    }

    @Test
    void masksCombineAndTolerateEmptyInput() {
        assertEquals(0, CalculationFlag.mask());
        assertEquals(2 | 256, CalculationFlag.mask(
                CalculationFlag.SWISS_EPHEMERIS, CalculationFlag.SPEED));
        assertEquals(1 | 256 | 512, RiseTransitFlag.mask(
                RiseTransitFlag.RISE, RiseTransitFlag.DISC_CENTER, RiseTransitFlag.NO_REFRACTION));
        assertEquals(4 | 1 | 2, EclipseType.mask(
                EclipseType.TOTAL, EclipseType.CENTRAL, EclipseType.NON_CENTRAL));
        assertEquals(256 | 2048, SiderealOption.mask(
                SiderealOption.ECLIPTIC_AT_T0, SiderealOption.ECLIPTIC_OF_DATE));
        assertEquals(8 | 16, DegreeSplitOption.mask(
                DegreeSplitOption.ZODIACAL, DegreeSplitOption.KEEP_SIGN));
    }

    @Test
    void masksRejectNullElements() {
        assertThrows(NullPointerException.class,
                () -> CalculationFlag.mask(CalculationFlag.SPEED, null));
        assertThrows(NullPointerException.class, () -> EclipseType.mask((EclipseType[]) null));
    }

    @Test
    void hinduRisingIsTheDocumentedComposite() {
        assertEquals(
                RiseTransitFlag.GEOCENTRIC_NO_ECLIPTIC_LATITUDE.value()
                        | RiseTransitFlag.DISC_CENTER.value()
                        | RiseTransitFlag.NO_REFRACTION.value(),
                RiseTransitFlag.HINDU_RISING.value());
    }

    @Test
    void eclipseTypeGroupsMatchTheHeaderAliases() {
        assertEquals(1 | 2 | 4 | 8 | 16 | 32, EclipseType.mask(EclipseType.allSolar()));
        assertEquals(4 | 16 | 64, EclipseType.mask(EclipseType.allLunar()));
    }

    @Test
    void returnedFlagsSeparateWhatWasDeliveredFromWhatWasAsked() {
        ReturnedFlags flags = new ReturnedFlags(
                CalculationFlag.MOSHIER_EPHEMERIS.value() | CalculationFlag.SPEED.value());

        assertTrue(flags.has(CalculationFlag.SPEED));
        assertFalse(flags.has(CalculationFlag.SWISS_EPHEMERIS));
        assertEquals(Ephemeris.MOSHIER, flags.ephemeris().orElseThrow());
        assertTrue(flags.used(Ephemeris.MOSHIER));
        assertFalse(flags.used(Ephemeris.SWISS));
        assertEquals(
                EnumSet.of(CalculationFlag.MOSHIER_EPHEMERIS, CalculationFlag.SPEED),
                flags.toSet());
        assertTrue(flags.toString().contains("SPEED"));
    }

    @Test
    void returnedFlagsReportNoEphemerisWhenNoneIsSet() {
        assertTrue(new ReturnedFlags(CalculationFlag.SPEED.value()).ephemeris().isEmpty());
    }

    @Test
    void eclipseFlagsSplitTypesFromVisibility() {
        EclipseFlags flags = new EclipseFlags(
                EclipseType.TOTAL.value()
                        | EclipseType.CENTRAL.value()
                        | EclipseVisibility.VISIBLE.value()
                        | EclipseVisibility.MAXIMUM_VISIBLE.value());

        assertEquals(Set.of(EclipseType.TOTAL, EclipseType.CENTRAL), flags.types());
        assertEquals(
                Set.of(EclipseVisibility.VISIBLE, EclipseVisibility.MAXIMUM_VISIBLE),
                flags.visibility());
        assertFalse(flags.has(EclipseType.PARTIAL));
        assertTrue(flags.has(EclipseVisibility.VISIBLE));
    }

    @Test
    void ephemerisMapsOntoItsCalculationFlag() {
        assertSame(CalculationFlag.SWISS_EPHEMERIS, Ephemeris.SWISS.flag());
        assertEquals(CalculationFlag.MOSHIER_EPHEMERIS.value(), Ephemeris.MOSHIER.value());
    }

    @Test
    void siderealModeLooksUpByNativeValue() {
        assertEquals(SiderealMode.LAHIRI, SiderealMode.of(1).orElseThrow());
        assertEquals(SiderealMode.USER, SiderealMode.of(255).orElseThrow());
        assertTrue(SiderealMode.of(9999).isEmpty());
    }

    @Test
    void siderealModeValuesAreDistinct() {
        Set<Integer> seen = new java.util.HashSet<>();
        for (SiderealMode mode : SiderealMode.values()) {
            assertTrue(seen.add(mode.value()), mode + " duplicates a native value");
        }
    }

    @Test
    void houseSystemCodesAreDistinctAndCaseSensitive() {
        Set<Character> seen = new java.util.HashSet<>();
        for (HouseSystem system : HouseSystem.values()) {
            assertTrue(seen.add(system.code()), system + " duplicates a code letter");
        }
        assertEquals(HouseSystem.SUNSHINE_TREINDL, HouseSystem.ofCode('I').orElseThrow());
        assertEquals(HouseSystem.SUNSHINE_MAKRANSKY, HouseSystem.ofCode('i').orElseThrow());
        assertTrue(HouseSystem.ofCode('z').isEmpty());
    }

    @Test
    void onlyGauquelinUsesThirtySixSectors() {
        for (HouseSystem system : HouseSystem.values()) {
            int expected = system == HouseSystem.GAUQUELIN ? 36 : 12;
            assertEquals(expected, system.houseCount(), system.name());
            assertEquals(expected + 1, system.cuspArrayLength(), system.name());
        }
    }

    @Test
    void celestialBodyIdentifiersMatchTheHeader() {
        assertEquals(-1, CelestialBody.ECLIPTIC_NUTATION.id());
        assertEquals(0, CelestialBody.SUN.id());
        assertEquals(1, CelestialBody.MOON.id());
        assertEquals(9, CelestialBody.PLUTO.id());
        assertEquals(15, CelestialBody.CHIRON.id());
        assertEquals(22, CelestialBody.INTERPOLATED_PERIGEE.id());
        assertEquals(10_433, CelestialBody.asteroid(433));
        assertEquals(9_001, CelestialBody.planetaryMoon(1));
        assertThrows(IllegalArgumentException.class, () -> CelestialBody.asteroid(0));
        assertThrows(IllegalArgumentException.class, () -> CelestialBody.planetaryMoon(-1));
    }
}
