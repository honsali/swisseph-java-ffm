package org.swisseph.ffm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value semantics of the result types.
 *
 * <p>Records holding a {@code double[]} get identity-based {@code equals} and
 * {@code hashCode} by default, and a {@code toString} that prints
 * {@code [D@2a139a55}. Every such type overrides all three, and these tests
 * hold that line.</p>
 */
class ValueObjectTest {

    @Test
    void houseCuspsCompareByValue() {
        HouseCusps first = new HouseCusps(
                cusps(13, 42.0), new double[10], HouseSystem.PLACIDUS, true);
        HouseCusps second = new HouseCusps(
                cusps(13, 42.0), new double[10], HouseSystem.PLACIDUS, true);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertFalse(first.toString().contains("[D@"), "toString must not print array identity");
        assertTrue(first.toString().contains("PLACIDUS"));
    }

    @Test
    void houseCuspsDistinguishSubstitutedSystems() {
        double[] cusps = cusps(13, 1.0);
        assertNotEquals(
                new HouseCusps(cusps, new double[10], HouseSystem.PLACIDUS, true),
                new HouseCusps(cusps, new double[10], HouseSystem.PLACIDUS, false));
        assertTrue(new HouseCusps(cusps, new double[10], HouseSystem.PLACIDUS, false)
                .toString().contains("substituted"));
    }

    @Test
    void houseCuspsCopyDefensivelyBothWays() {
        double[] cusps = cusps(13, 42.0);
        double[] additional = new double[10];
        additional[0] = 84.0;

        HouseCusps result = new HouseCusps(cusps, additional, HouseSystem.KOCH, true);
        cusps[1] = 0.0;
        additional[0] = 0.0;

        assertEquals(42.0, result.cusp(1));
        assertEquals(84.0, result.ascendant());

        double[] handedOut = result.cusps();
        handedOut[1] = 0.0;
        assertEquals(42.0, result.cusp(1));
    }

    @Test
    void houseCuspsRejectWrongLengthsAndOutOfRangeHouses() {
        assertThrows(IllegalArgumentException.class,
                () -> new HouseCusps(new double[12], new double[10], HouseSystem.PLACIDUS, true));
        assertThrows(IllegalArgumentException.class,
                () -> new HouseCusps(new double[13], new double[9], HouseSystem.PLACIDUS, true));

        HouseCusps cusps = new HouseCusps(
                new double[13], new double[10], HouseSystem.PLACIDUS, true);
        assertEquals(12, cusps.houseCount());
        assertThrows(IndexOutOfBoundsException.class, () -> cusps.cusp(0));
        assertThrows(IndexOutOfBoundsException.class, () -> cusps.cusp(13));

        HouseCusps gauquelin = new HouseCusps(
                new double[37], new double[10], HouseSystem.GAUQUELIN, true);
        assertEquals(36, gauquelin.houseCount());
        assertEquals(0.0, gauquelin.cusp(36));
    }

    @Test
    void planetaryPhenomenaCompareByValue() {
        double[] attributes = new double[20];
        attributes[0] = 30.0;
        attributes[1] = 0.75;

        PlanetaryPhenomena first = new PlanetaryPhenomena(attributes, null);
        PlanetaryPhenomena second = new PlanetaryPhenomena(attributes.clone(), "");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(30.0, first.phaseAngle());
        assertEquals(0.75, first.illuminatedFraction());
        assertEquals("", first.warning());
        assertFalse(first.toString().contains("[D@"));
        assertThrows(IndexOutOfBoundsException.class, () -> first.attribute(20));
    }

    @Test
    void eclipseResultsCompareByValue() {
        double[] times = new double[10];
        times[0] = 2_451_545.0;
        EclipseFlags flags = new EclipseFlags(EclipseType.TOTAL.value());

        GlobalSolarEclipse first = new GlobalSolarEclipse(flags, times, null);
        GlobalSolarEclipse second = new GlobalSolarEclipse(flags, times.clone(), "");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(2_451_545.0, first.maximum());
        assertFalse(first.toString().contains("[D@"));

        GlobalLunarEclipse lunar = new GlobalLunarEclipse(flags, times, "");
        assertEquals(lunar, new GlobalLunarEclipse(flags, times.clone(), ""));
        assertEquals(2_451_545.0, lunar.maximum());
    }

    @Test
    void eclipseAttributeTypesAreNotInterchangeable() {
        double[] values = new double[20];
        values[0] = 1.02;
        assertEquals(1.02, new SolarEclipseAttributes(values).magnitude());
        assertEquals(1.02, new LunarEclipseAttributes(values).umbralMagnitude());
        assertNotEquals(
                (Object) new SolarEclipseAttributes(values),
                (Object) new LunarEclipseAttributes(values));
    }

    @Test
    void localEclipsesExposeTheirContacts() {
        double[] times = new double[10];
        for (int index = 0; index < times.length; index++) {
            times[index] = index;
        }
        LocalSolarEclipse solar = new LocalSolarEclipse(
                new EclipseFlags(EclipseVisibility.VISIBLE.value()),
                times,
                new SolarEclipseAttributes(new double[20]),
                "");

        assertTrue(solar.isVisible());
        assertEquals(0.0, solar.maximum());
        assertEquals(1.0, solar.firstContact());
        assertEquals(4.0, solar.fourthContact());
        assertEquals(5.0, solar.sunrise());

        LocalLunarEclipse lunar = new LocalLunarEclipse(
                new EclipseFlags(0), times, new LunarEclipseAttributes(new double[20]), "");
        assertFalse(lunar.isVisible());
        assertEquals(2.0, lunar.partialBegin());
        assertEquals(6.0, lunar.penumbralBegin());
        assertEquals(8.0, lunar.moonrise());
    }

    @Test
    void eclipsePositionsReportUndefinedLimits() {
        double[] positions = new double[10];
        positions[0] = 10.0;
        positions[1] = 20.0;
        for (int index = 2; index < positions.length; index++) {
            positions[index] = GeographicPoint.UNDEFINED;
        }

        SolarEclipsePosition position = new SolarEclipsePosition(
                new EclipseFlags(0), positions, new SolarEclipseAttributes(new double[20]), "");

        assertTrue(position.centralLine().isDefined());
        assertEquals(10.0, position.centralLine().longitude());
        assertEquals(20.0, position.centralLine().latitude());
        assertFalse(position.northernUmbraLimit().isDefined());
        assertThrows(IllegalStateException.class,
                () -> position.northernUmbraLimit().toObserver());
        assertEquals(GeographicPosition.of(10.0, 20.0), position.centralLine().toObserver());
    }

    @Test
    void ephemerisPositionExposesRetrogradeMotion() {
        int withSpeed = CalculationFlag.SWISS_EPHEMERIS.value() | CalculationFlag.SPEED.value();
        EphemerisPosition direct = new EphemerisPosition(
                10.0, 0.0, 1.0, 0.9, 0.0, 0.0, new ReturnedFlags(withSpeed), null);
        EphemerisPosition retrograde = new EphemerisPosition(
                10.0, 0.0, 1.0, -0.3, 0.0, 0.0, new ReturnedFlags(withSpeed), "note");

        assertFalse(direct.isRetrograde());
        assertTrue(retrograde.isRetrograde());
        assertEquals("", direct.warning());
        assertEquals("note", retrograde.warning());
        assertEquals(10.0, direct.longitude());
        assertEquals(0.9, direct.longitudeSpeed());
    }

    @Test
    void retrogradeMotionRefusesToAnswerFromTheWrongNumber() {
        // Without speed the components are all zero, so the honest answer is not
        // "direct" but "you did not ask".
        EphemerisPosition noSpeed = new EphemerisPosition(10.0, 0.0, 1.0, 0.0, 0.0, 0.0,
                new ReturnedFlags(CalculationFlag.SWISS_EPHEMERIS.value()), "");
        assertThrows(IllegalStateException.class, noSpeed::isRetrograde);

        // In cartesian output the first speed is dx/dt, and in equatorial output
        // it is motion in right ascension. Neither says anything about being
        // retrograde in ecliptic longitude.
        EphemerisPosition cartesian = new EphemerisPosition(1.0, 0.0, 0.0, -0.5, 0.0, 0.0,
                new ReturnedFlags(CalculationFlag.SPEED.value()
                        | CalculationFlag.CARTESIAN.value()), "");
        assertThrows(IllegalStateException.class, cartesian::isRetrograde);

        EphemerisPosition equatorial = new EphemerisPosition(10.0, 0.0, 1.0, -0.5, 0.0, 0.0,
                new ReturnedFlags(CalculationFlag.SPEED.value()
                        | CalculationFlag.EQUATORIAL.value()), "");
        assertThrows(IllegalStateException.class, equatorial::isRetrograde);
    }

    @Test
    void sphericalCoordinatesRefuseTheWrongLabels() {
        SphericalCoordinates ecliptic = new SphericalCoordinates(
                120.0, 5.0, HorizontalCoordinateType.ECLIPTIC);
        assertEquals(120.0, ecliptic.eclipticLongitude());
        assertEquals(5.0, ecliptic.eclipticLatitude());
        assertThrows(IllegalStateException.class, ecliptic::rightAscension);
        assertThrows(IllegalStateException.class, ecliptic::declination);

        SphericalCoordinates equatorial = new SphericalCoordinates(
                120.0, 5.0, HorizontalCoordinateType.EQUATORIAL);
        assertEquals(120.0, equatorial.rightAscension());
        assertEquals(5.0, equatorial.declination());
        assertThrows(IllegalStateException.class, equatorial::eclipticLongitude);
    }

    @Test
    void eclipseCircumstancesReportAbsenceRatherThanFailing() {
        SolarEclipseCircumstances none = new SolarEclipseCircumstances(
                new EclipseFlags(0), new SolarEclipseAttributes(new double[20]), null);
        assertFalse(none.isEclipsed(), "flags of zero mean no eclipse is visible");
        assertEquals("", none.warning());

        double[] attributes = new double[20];
        attributes[0] = 1.02;
        SolarEclipseCircumstances total = new SolarEclipseCircumstances(
                new EclipseFlags(EclipseType.TOTAL.value()),
                new SolarEclipseAttributes(attributes), "");
        assertTrue(total.isEclipsed());
        assertTrue(total.flags().has(EclipseType.TOTAL));
        assertEquals(1.02, total.magnitude());

        LunarEclipseCircumstances lunar = new LunarEclipseCircumstances(
                new EclipseFlags(EclipseType.PENUMBRAL.value()),
                new LunarEclipseAttributes(attributes), "");
        assertTrue(lunar.isVisible());
        assertEquals(1.02, lunar.umbralMagnitude());
    }

    @Test
    void fixedStarSplitsTheCanonicalName() {
        EphemerisPosition position = new EphemerisPosition(
                0, 0, 0, 0, 0, 0, new ReturnedFlags(0), "");
        FixedStarPosition named = new FixedStarPosition("Sirius,alCMa", position);
        assertEquals("Sirius", named.traditionalName());
        assertEquals("alCMa", named.nomenclatureName());

        FixedStarPosition bare = new FixedStarPosition("Sirius", position);
        assertEquals("Sirius", bare.traditionalName());
        assertEquals("", bare.nomenclatureName());
    }

    @Test
    void riseTransitReportsAbsentEventsWithoutThrowing() {
        assertTrue(new RiseTransitResult(false, Double.NaN, "").time().isEmpty());
        assertEquals(2_451_545.0,
                new RiseTransitResult(true, 2_451_545.0, "").time().orElseThrow());
    }

    @Test
    void ephemerisFileReportsCoverageAndJplNumber() {
        EphemerisFile file = new EphemerisFile("/ephe/sepl_18.se1", 2378496.5, 2597641.5, 431);
        assertTrue(file.covers(2_451_545.0));
        assertFalse(file.covers(1_000_000.0));
        assertEquals(431, file.jplNumber().orElseThrow());
        assertTrue(new EphemerisFile("/ephe/sefstars.txt", 0, 0, 0).jplNumber().isEmpty());
    }

    @Test
    void settingsStartEmptyAndRecordWhatWasApplied() {
        assertTrue(SwissEphSettings.EMPTY.ephemerisPathIfSet().isEmpty());
        assertTrue(SwissEphSettings.EMPTY.siderealAyanamsa().isEmpty());

        SwissEphSettings applied = SwissEphSettings.EMPTY
                .withEphemerisPath("/ephe")
                .withSiderealMode(
                        SiderealMode.LAHIRI.value() | SiderealOption.ECLIPTIC_OF_DATE.value(),
                        0.0, 0.0);

        assertEquals("/ephe", applied.ephemerisPathIfSet().orElseThrow());
        assertEquals(SiderealMode.LAHIRI, applied.siderealAyanamsa().orElseThrow());
    }

    @Test
    void degreePartsFormatAndGuardTheSignComponent() {
        DegreeParts plain = new DegreeParts(12, 30, 0, 0.0, 1, 0);
        assertEquals("12d30'0\"", plain.toDegreeMinuteSecond());
        assertEquals(1, plain.signum());
        // isgn is 1 here because the input was positive, not because it is Taurus.
        // Without the round flags the two readings are indistinguishable, which is
        // exactly the confusion this guard exists to prevent.
        assertThrows(IllegalStateException.class, plain::zodiacSign);
        assertThrows(IllegalStateException.class, plain::nakshatra);

        DegreeParts zodiacal = new DegreeParts(
                5, 30, 0, 0.0, 1, DegreeSplitOption.ZODIACAL.value());
        assertTrue(zodiacal.isZodiacal());
        assertEquals(1, zodiacal.zodiacSign());
        assertThrows(IllegalStateException.class, zodiacal::signum);

        DegreeParts nakshatra = new DegreeParts(
                5, 30, 0, 0.0, 3, DegreeSplitOption.NAKSHATRA.value());
        assertEquals(3, nakshatra.nakshatra());

        // A negative input makes the native code skip the nakshatra branch, so
        // isgn falls back to -1 and must not be read as an index.
        DegreeParts negativeNakshatra = new DegreeParts(
                5, 30, 0, 0.0, -1, DegreeSplitOption.NAKSHATRA.value());
        assertFalse(negativeNakshatra.isNakshatra());
        assertEquals(-1, negativeNakshatra.signum());
        assertThrows(IllegalStateException.class, negativeNakshatra::nakshatra);
    }

    private static double[] cusps(int length, double firstCusp) {
        double[] cusps = new double[length];
        cusps[1] = firstCusp;
        return cusps;
    }
}
