package org.swisseph.ffm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.swisseph.ffm.NativeTestSupport.angularDistance;
import static org.swisseph.ffm.NativeTestSupport.minutes;
import static org.swisseph.ffm.NativeTestSupport.normalizeDegrees;

/**
 * Checks the binding against values that can be verified independently.
 *
 * <p>A wrong {@code FunctionDescriptor} does not fail to compile and rarely
 * fails to run: it reads the wrong register and returns a plausible-looking
 * number. Assertions of the shape "is finite" or "is between 0 and 360" pass
 * happily against such a binding, so everything here is anchored to a value
 * that is either a definition (the Sun stands at longitude 0 at the equinox) or
 * a documented historical fact (the greatest eclipse of 11 August 1999 fell at
 * 11:03 UT over Romania).</p>
 */
class ReferenceValueIntegrationTest {

    private static final double J2000 = 2_451_545.0;

    // ------------------------------------------------------------------
    // Time
    // ------------------------------------------------------------------

    @Test
    @DisplayName("J2000.0 is Julian day 2451545.0 by definition")
    void julianDayMatchesItsDefinition() {
        try (SwissEph swe = NativeTestSupport.open()) {
            assertEquals(J2000, swe.julianDay(2000, 1, 1, 12.0, CalendarType.GREGORIAN), 1.0e-9);
            assertEquals(new CivilDate(2000, 1, 1, 12.0),
                    swe.reverseJulianDay(J2000, CalendarType.GREGORIAN));

            // One day later, one day of Julian day.
            assertEquals(J2000 + 1.0,
                    swe.julianDay(2000, 1, 2, 12.0, CalendarType.GREGORIAN), 1.0e-9);
        }
    }

    @Test
    @DisplayName("UTC conversions round-trip and expose delta T")
    void utcConversionsRoundTrip() {
        try (SwissEph swe = NativeTestSupport.open()) {
            UtcDateTime utc = new UtcDateTime(2000, 1, 1, 12, 0, 0.0);
            JulianDate julian = swe.utcToJulianDay(utc, CalendarType.GREGORIAN);

            // Delta T was about 63.8 seconds in 2000, so ET runs ahead of UT.
            double deltaTSeconds = julian.deltaTDays() * 86_400.0;
            assertTrue(deltaTSeconds > 60.0 && deltaTSeconds < 70.0,
                    "delta T at J2000 should be near 64 s but was " + deltaTSeconds);
            assertEquals(julian.deltaTDays(), swe.deltaT(julian.universalTime()), 1.0e-6);

            UtcDateTime roundTripped =
                    swe.universalTimeToUtc(julian.universalTime(), CalendarType.GREGORIAN);
            assertEquals(2000, roundTripped.year());
            assertEquals(1, roundTripped.month());
            assertEquals(1, roundTripped.day());
            assertEquals(12, roundTripped.hour());
            assertEquals(0, roundTripped.minute());
            assertEquals(0.0, roundTripped.second(), 1.0e-3);
        }
    }

    @Test
    @DisplayName("A time-zone shift moves the clock by exactly the offset")
    void timeZoneShiftIsExact() {
        try (SwissEph swe = NativeTestSupport.open()) {
            UtcDateTime shifted =
                    swe.applyTimeZone(new UtcDateTime(2000, 6, 15, 12, 0, 0.0), 2.0);
            assertEquals(10, shifted.hour());
            assertEquals(0, shifted.minute());
            assertEquals(15, shifted.day());
        }
    }

    // ------------------------------------------------------------------
    // Positions
    // ------------------------------------------------------------------

    @Test
    @DisplayName("At the March 2000 equinox the Sun stands at longitude 0")
    void sunIsAtTheVernalPointAtTheEquinox() {
        try (SwissEph swe = NativeTestSupport.open()) {
            // The March equinox of 2000 fell on the 20th at 07:35 UT. The Sun moves
            // 0.041 degrees per hour, so even a generous error in that timestamp
            // stays far inside this tolerance, while a mis-declared native call
            // would miss it by tens of degrees.
            double equinox = swe.julianDay(2000, 3, 20, 7.0 + 35.0 / 60.0, CalendarType.GREGORIAN);
            EphemerisPosition sun = swe.calculateUt(equinox, CelestialBody.SUN, ephemerisFlag());

            assertEquals(0.0, angularDistance(sun.longitude(), 0.0), 0.05,
                    "solar longitude at the equinox was " + sun.longitude());

            // And the December solstice of 2000, on the 21st at 13:37 UT, puts it at 270.
            double solstice =
                    swe.julianDay(2000, 12, 21, 13.0 + 37.0 / 60.0, CalendarType.GREGORIAN);
            EphemerisPosition atSolstice =
                    swe.calculateUt(solstice, CelestialBody.SUN, ephemerisFlag());
            assertEquals(0.0, angularDistance(atSolstice.longitude(), 270.0), 0.05,
                    "solar longitude at the solstice was " + atSolstice.longitude());
        }
    }

    @Test
    @DisplayName("Solar motion and distance match the geometry of early January")
    void solarMotionAndDistanceAreRight() {
        try (SwissEph swe = NativeTestSupport.open()) {
            EphemerisPosition sun = swe.calculateUt(
                    J2000, CelestialBody.SUN, ephemerisFlag(), CalculationFlag.SPEED);

            // Earth passes perihelion in the first days of January, where it moves
            // fastest and sits closest to the Sun.
            assertTrue(sun.longitudeSpeed() > 1.005 && sun.longitudeSpeed() < 1.030,
                    "apparent solar motion near perihelion was " + sun.longitudeSpeed()
                            + " deg/day");
            assertTrue(sun.distance() > 0.980 && sun.distance() < 0.987,
                    "Earth-Sun distance near perihelion was " + sun.distance() + " AU");
            assertFalse(sun.isRetrograde());
            assertTrue(sun.returnedFlags().has(CalculationFlag.SPEED));
        }
    }

    @Test
    @DisplayName("Lunar motion stays inside its physical range")
    void lunarMotionIsPhysical() {
        try (SwissEph swe = NativeTestSupport.open()) {
            EphemerisPosition moon = swe.calculateUt(
                    J2000, CelestialBody.MOON, ephemerisFlag(), CalculationFlag.SPEED);

            // The Moon covers between 11.8 and 15.4 degrees a day; it never reverses.
            assertTrue(moon.longitudeSpeed() > 11.0 && moon.longitudeSpeed() < 15.5,
                    "lunar motion was " + moon.longitudeSpeed() + " deg/day");
            // Its distance ranges over roughly 0.0024 to 0.0027 AU.
            assertTrue(moon.distance() > 0.0023 && moon.distance() < 0.0028,
                    "lunar distance was " + moon.distance() + " AU");
            assertTrue(Math.abs(moon.latitude()) < 6.0);
        }
    }

    @Test
    @DisplayName("Ephemeris time and universal time agree once delta T is applied")
    void ephemerisAndUniversalTimeAgree() {
        try (SwissEph swe = NativeTestSupport.open()) {
            double deltaT = swe.deltaT(J2000);
            EphemerisPosition fromUt = swe.calculateUt(J2000, CelestialBody.MARS, ephemerisFlag());
            EphemerisPosition fromEt =
                    swe.calculate(J2000 + deltaT, CelestialBody.MARS, ephemerisFlag());

            // swe_calc_ut is documented as swe_calc applied to tjd_ut + delta T.
            assertEquals(fromEt.longitude(), fromUt.longitude(), 1.0e-9);
            assertEquals(fromEt.distance(), fromUt.distance(), 1.0e-12);
        }
    }

    @Test
    @DisplayName("Phenomena describe an illuminated disc")
    void phenomenaDescribeAnIlluminatedDisc() {
        try (SwissEph swe = NativeTestSupport.open()) {
            PlanetaryPhenomena moon =
                    swe.phenomenaUt(J2000, CelestialBody.MOON, ephemerisFlag());

            assertTrue(moon.illuminatedFraction() >= 0.0 && moon.illuminatedFraction() <= 1.0);
            assertTrue(moon.phaseAngle() >= 0.0 && moon.phaseAngle() <= 180.0);
            assertTrue(moon.elongation() >= 0.0 && moon.elongation() <= 180.0);
            // The lunar disc spans about half a degree.
            assertTrue(moon.apparentDiameter() > 0.45 && moon.apparentDiameter() < 0.60,
                    "lunar apparent diameter was " + moon.apparentDiameter() + " degrees");

            // Phase angle and illuminated fraction are two views of one geometry.
            double expected = (1.0 + Math.cos(Math.toRadians(moon.phaseAngle()))) / 2.0;
            assertEquals(expected, moon.illuminatedFraction(), 0.02);
        }
    }

    // ------------------------------------------------------------------
    // Houses
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ARMC equals local sidereal time expressed in degrees")
    void armcMatchesSiderealTime() {
        try (SwissEph swe = NativeTestSupport.open()) {
            GeographicPosition greenwich = GeographicPosition.of(0.0, 51.4778);
            HouseCusps houses = swe.houses(J2000, greenwich, HouseSystem.PLACIDUS);

            assertTrue(houses.requestedSystemUsed());
            assertEquals(0.0,
                    angularDistance(houses.armc(), swe.siderealTime(J2000) * 15.0), 1.0e-6);

            // Greenwich mean sidereal time at J2000 noon UT is about 18.697 hours.
            assertEquals(18.697, swe.siderealTime(J2000), 0.01);
        }
    }

    @Test
    @DisplayName("Whole-sign and equal houses have exactly the structure they promise")
    void houseSystemsHaveTheirDefiningStructure() {
        try (SwissEph swe = NativeTestSupport.open()) {
            GeographicPosition paris = GeographicPosition.of(2.3522, 48.8566);

            HouseCusps wholeSign = swe.houses(J2000, paris, HouseSystem.WHOLE_SIGN);
            assertEquals(0.0, wholeSign.cusp(1) % 30.0, 1.0e-9,
                    "a whole-sign first cusp must fall on a sign boundary");

            HouseCusps equal = swe.houses(J2000, paris, HouseSystem.EQUAL_ASCENDANT);
            assertEquals(0.0, angularDistance(equal.cusp(1), equal.ascendant()), 1.0e-9,
                    "the equal-house first cusp is the ascendant");
            for (int house = 1; house < 12; house++) {
                assertEquals(30.0,
                        normalizeDegrees(equal.cusp(house + 1) - equal.cusp(house)), 1.0e-9,
                        "equal houses must be 30 degrees apart, house " + house);
            }
        }
    }

    @Test
    @DisplayName("Gauquelin returns 36 sectors, not 12 houses")
    void gauquelinReturnsThirtySixSectors() {
        try (SwissEph swe = NativeTestSupport.open()) {
            HouseCusps sectors = swe.houses(
                    J2000, GeographicPosition.of(2.3522, 48.8566), HouseSystem.GAUQUELIN);

            assertEquals(36, sectors.houseCount());
            assertEquals(37, sectors.cusps().length);
            assertTrue(Double.isFinite(sectors.cusp(36)));
        }
    }

    @Test
    @DisplayName("Placidus beyond the polar circle reports its substitution")
    void placidusBeyondThePolarCircleIsFlagged() {
        try (SwissEph swe = NativeTestSupport.open()) {
            // Swiss Ephemeris cannot compute Placidus above 66 degrees and quietly
            // fills the cusps from Porphyry instead. The numbers stay usable, but the
            // caller has to be told.
            HouseCusps polar =
                    swe.houses(J2000, GeographicPosition.of(15.0, 78.0), HouseSystem.PLACIDUS);

            assertFalse(polar.requestedSystemUsed(),
                    "the substitution must be visible to the caller");
            for (int house = 1; house <= 12; house++) {
                assertTrue(Double.isFinite(polar.cusp(house)),
                        "substituted cusps must still be usable, house " + house);
            }

            HouseCusps temperate =
                    swe.houses(J2000, GeographicPosition.of(15.0, 48.0), HouseSystem.PLACIDUS);
            assertTrue(temperate.requestedSystemUsed());
        }
    }

    @Test
    @DisplayName("Houses from ARMC reproduce houses from a timestamp")
    void housesFromArmcReproduceHousesFromATimestamp() {
        try (SwissEph swe = NativeTestSupport.open()) {
            GeographicPosition observer = GeographicPosition.of(12.4964, 41.9028);
            HouseCusps fromTime = swe.houses(J2000, observer, HouseSystem.CAMPANUS);

            // The obliquity of the ecliptic comes from body -1.
            EphemerisPosition obliquity =
                    swe.calculateUt(J2000, CelestialBody.ECLIPTIC_NUTATION, ephemerisFlag());
            double trueObliquity = obliquity.firstCoordinate();

            HouseCusps fromArmc = swe.housesFromArmc(
                    fromTime.armc(), observer.latitude(), trueObliquity, HouseSystem.CAMPANUS);

            for (int house = 1; house <= 12; house++) {
                assertEquals(0.0, angularDistance(fromTime.cusp(house), fromArmc.cusp(house)),
                        1.0e-6, "cusp " + house + " differs between the two entry points");
            }
        }
    }

    @Test
    @DisplayName("A body on the ascendant sits at the start of the first house")
    void housePositionAgreesWithTheAscendant() {
        try (SwissEph swe = NativeTestSupport.open()) {
            GeographicPosition observer = GeographicPosition.of(12.4964, 41.9028);
            HouseCusps houses = swe.houses(J2000, observer, HouseSystem.PLACIDUS);
            EphemerisPosition obliquity =
                    swe.calculateUt(J2000, CelestialBody.ECLIPTIC_NUTATION, ephemerisFlag());

            double position = swe.housePosition(houses.armc(), observer.latitude(),
                    obliquity.firstCoordinate(), HouseSystem.PLACIDUS, houses.ascendant(), 0.0);

            assertEquals(1.0, position, 1.0e-6,
                    "a point on the ascendant must sit exactly at house 1.0");
        }
    }

    // ------------------------------------------------------------------
    // Horizon
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Azimuth and altitude round-trip through the reverse conversion")
    void horizontalCoordinatesRoundTrip() {
        try (SwissEph swe = NativeTestSupport.open()) {
            GeographicPosition observer = new GeographicPosition(2.3522, 48.8566, 35.0);
            EphemerisPosition sun = swe.calculateUt(J2000, CelestialBody.SUN, ephemerisFlag());

            HorizontalCoordinates horizontal = swe.azimuthAltitude(
                    J2000, HorizontalCoordinateType.ECLIPTIC, observer,
                    AtmosphericConditions.STANDARD,
                    sun.longitude(), sun.latitude(), sun.distance());

            EclipticCoordinates back = swe.azimuthAltitudeReverse(
                    J2000, HorizontalCoordinateType.ECLIPTIC, observer,
                    horizontal.azimuth(), horizontal.trueAltitude());

            assertEquals(0.0, angularDistance(back.longitude(), sun.longitude()), 1.0e-4);
            assertEquals(sun.latitude(), back.latitude(), 1.0e-4);

            // Refraction lifts a body near the horizon, never lowers it.
            assertTrue(horizontal.apparentAltitude() >= horizontal.trueAltitude() - 1.0e-9);
        }
    }

    @Test
    @DisplayName("The Sun rises near 06:00 on the equator at the equinox")
    void sunriseOnTheEquatorAtTheEquinox() {
        try (SwissEph swe = NativeTestSupport.open()) {
            double startOfDay = swe.julianDay(2000, 3, 20, 0.0, CalendarType.GREGORIAN);
            RiseTransitResult sunrise = swe.riseTransit(
                    startOfDay, CelestialBody.SUN, GeographicPosition.of(0.0, 0.0),
                    AtmosphericConditions.STANDARD, RiseTransitFlag.RISE);

            assertTrue(sunrise.found());
            double hoursUt = (sunrise.julianDayUt() - startOfDay) * 24.0;
            assertTrue(hoursUt > 5.5 && hoursUt < 6.5,
                    "equinox sunrise on the equator at longitude 0 was at " + hoursUt + " h UT");
        }
    }

    @Test
    @DisplayName("The polar night is reported as no event, not as an error")
    void polarNightYieldsNoEvent() {
        try (SwissEph swe = NativeTestSupport.open()) {
            // Longyearbyen in December: the Sun does not rise at all.
            double december = swe.julianDay(2000, 12, 21, 0.0, CalendarType.GREGORIAN);
            RiseTransitResult sunrise = swe.riseTransit(
                    december, CelestialBody.SUN, GeographicPosition.of(15.6469, 78.2232),
                    AtmosphericConditions.STANDARD, RiseTransitFlag.RISE);

            assertFalse(sunrise.found(), "the Sun must not rise at 78 degrees north in December");
            assertTrue(sunrise.time().isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // Eclipses
    // ------------------------------------------------------------------

    @Test
    @DisplayName("The total solar eclipse of 11 August 1999 is found at 11:03 UT")
    void findsTheTotalSolarEclipseOf1999() {
        try (SwissEph swe = NativeTestSupport.open()) {
            double searchFrom = swe.julianDay(1999, 8, 1, 0.0, CalendarType.GREGORIAN);
            double greatestEclipse =
                    swe.julianDay(1999, 8, 11, 11.0 + 3.0 / 60.0, CalendarType.GREGORIAN);

            GlobalSolarEclipse eclipse = swe.solarEclipseWhenGlobal(
                    searchFrom, ephemerisFlag().value(), EclipseType.allSolar(), false);

            assertEquals(greatestEclipse, eclipse.maximum(), minutes(20.0),
                    "greatest eclipse landed at "
                            + swe.reverseJulianDay(eclipse.maximum(), CalendarType.GREGORIAN));
            assertTrue(eclipse.flags().has(EclipseType.TOTAL));
            assertTrue(eclipse.flags().has(EclipseType.CENTRAL));
            assertTrue(eclipse.begin() < eclipse.maximum());
            assertTrue(eclipse.end() > eclipse.maximum());
        }
    }

    @Test
    @DisplayName("The 1999 eclipse track crosses Romania, where it was greatest")
    void locatesTheGreatestEclipseOf1999() {
        try (SwissEph swe = NativeTestSupport.open()) {
            double greatestEclipse =
                    swe.julianDay(1999, 8, 11, 11.0 + 3.0 / 60.0, CalendarType.GREGORIAN);

            SolarEclipsePosition position =
                    swe.solarEclipseWhere(greatestEclipse, ephemerisFlag().value());
            GeographicPoint central = position.centralLine();

            assertTrue(central.isDefined());
            // Greatest eclipse fell near Ramnicu Valcea, at about 24.3 E and 45.1 N.
            assertEquals(24.3, central.longitude(), 3.0,
                    "central line longitude was " + central.longitude());
            assertEquals(45.1, central.latitude(), 3.0,
                    "central line latitude was " + central.latitude());

            SolarEclipseAttributes circumstances = swe.solarEclipseHow(
                    greatestEclipse, ephemerisFlag().value(), central.toObserver());
            assertTrue(circumstances.magnitude() > 0.99,
                    "the eclipse was total on the central line, magnitude was "
                            + circumstances.magnitude());
            assertTrue(circumstances.obscuration() > 0.99);
            assertTrue(circumstances.sunApparentAltitude() > 0.0,
                    "the Sun must be above the horizon on the central line");
        }
    }

    @Test
    @DisplayName("The total solar eclipse of 21 August 2017 is found at 18:26 UT")
    void findsTheTotalSolarEclipseOf2017() {
        try (SwissEph swe = NativeTestSupport.open()) {
            double searchFrom = swe.julianDay(2017, 8, 1, 0.0, CalendarType.GREGORIAN);
            double greatestEclipse =
                    swe.julianDay(2017, 8, 21, 18.0 + 26.0 / 60.0, CalendarType.GREGORIAN);

            GlobalSolarEclipse eclipse = swe.solarEclipseWhenGlobal(
                    searchFrom, ephemerisFlag().value(), EclipseType.allSolar(), false);

            assertEquals(greatestEclipse, eclipse.maximum(), minutes(20.0));
            assertTrue(eclipse.flags().has(EclipseType.TOTAL));
        }
    }

    @Test
    @DisplayName("Searching backwards finds the eclipse before the start date")
    void searchesBackwardsAsWellAsForwards() {
        try (SwissEph swe = NativeTestSupport.open()) {
            double searchFrom = swe.julianDay(1999, 9, 1, 0.0, CalendarType.GREGORIAN);
            GlobalSolarEclipse backwards = swe.solarEclipseWhenGlobal(
                    searchFrom, ephemerisFlag().value(), EclipseType.allSolar(), true);

            assertTrue(backwards.maximum() < searchFrom);
            assertEquals(swe.julianDay(1999, 8, 11, 11.0 + 3.0 / 60.0, CalendarType.GREGORIAN),
                    backwards.maximum(), minutes(20.0));
        }
    }

    @Test
    @DisplayName("The total lunar eclipse of 21 January 2000 is found at 04:44 UT")
    void findsTheTotalLunarEclipseOf2000() {
        try (SwissEph swe = NativeTestSupport.open()) {
            double searchFrom = swe.julianDay(2000, 1, 1, 0.0, CalendarType.GREGORIAN);
            double greatestEclipse =
                    swe.julianDay(2000, 1, 21, 4.0 + 44.0 / 60.0, CalendarType.GREGORIAN);

            GlobalLunarEclipse eclipse = swe.lunarEclipseWhen(
                    searchFrom, ephemerisFlag().value(), EclipseType.allLunar(), false);

            assertEquals(greatestEclipse, eclipse.maximum(), minutes(20.0));
            assertTrue(eclipse.flags().has(EclipseType.TOTAL));
            // The phases must nest: penumbra outside partial outside totality.
            assertTrue(eclipse.penumbralBegin() < eclipse.partialBegin());
            assertTrue(eclipse.partialBegin() < eclipse.totalityBegin());
            assertTrue(eclipse.totalityEnd() < eclipse.partialEnd());
            assertTrue(eclipse.partialEnd() < eclipse.penumbralEnd());
        }
    }

    @Test
    @DisplayName("Lunar eclipse circumstances read through the lunar attribute layout")
    void lunarEclipseCircumstancesUseTheLunarLayout() {
        try (SwissEph swe = NativeTestSupport.open()) {
            double greatestEclipse =
                    swe.julianDay(2000, 1, 21, 4.0 + 44.0 / 60.0, CalendarType.GREGORIAN);

            // Visible from the Americas; Washington DC had the Moon high in the sky.
            LunarEclipseAttributes circumstances = swe.lunarEclipseHow(
                    greatestEclipse, ephemerisFlag().value(),
                    GeographicPosition.of(-77.0369, 38.9072));

            assertTrue(circumstances.umbralMagnitude() > 1.0,
                    "a total lunar eclipse has umbral magnitude above 1, was "
                            + circumstances.umbralMagnitude());
            assertTrue(circumstances.penumbralMagnitude() > circumstances.umbralMagnitude());
            assertTrue(circumstances.moonApparentAltitude() > 0.0,
                    "the Moon was above the horizon at Washington");
            assertTrue(circumstances.oppositionDistance() < 2.0,
                    "an eclipsed Moon sits close to opposition");
        }
    }

    @Test
    @DisplayName("A local solar eclipse search reports its contacts in order")
    void localSolarEclipseContactsAreOrdered() {
        try (SwissEph swe = NativeTestSupport.open()) {
            double searchFrom = swe.julianDay(1999, 8, 1, 0.0, CalendarType.GREGORIAN);
            // Munich lay just north of the 1999 path of totality.
            LocalSolarEclipse eclipse = swe.solarEclipseWhenLocal(
                    searchFrom, ephemerisFlag().value(),
                    GeographicPosition.of(11.5820, 48.1351), false);

            assertTrue(eclipse.isVisible());
            assertTrue(eclipse.firstContact() < eclipse.maximum());
            assertTrue(eclipse.maximum() < eclipse.fourthContact());
            assertTrue(eclipse.attributes().magnitude() > 0.9,
                    "Munich saw a near-total eclipse, magnitude was "
                            + eclipse.attributes().magnitude());
            assertEquals(swe.julianDay(1999, 8, 11, 11.0 + 3.0 / 60.0, CalendarType.GREGORIAN),
                    eclipse.maximum(), minutes(30.0));
        }
    }

    // ------------------------------------------------------------------
    // Sidereal
    // ------------------------------------------------------------------

    @Test
    @DisplayName("The Lahiri ayanamsha at J2000 is about 23.85 degrees")
    void lahiriAyanamshaMatchesItsPublishedValue() {
        try (SwissEph swe = NativeTestSupport.open()) {
            swe.setSiderealMode(SiderealMode.LAHIRI);
            double ayanamsa = swe.ayanamsaUt(J2000);

            assertEquals(23.85, ayanamsa, 0.05, "Lahiri ayanamsha at J2000 was " + ayanamsa);

            // A sidereal longitude is the tropical one minus the ayanamsha.
            EphemerisPosition tropical =
                    swe.calculateUt(J2000, CelestialBody.SUN, ephemerisFlag());
            EphemerisPosition sidereal = swe.calculateUt(J2000, CelestialBody.SUN,
                    ephemerisFlag(), CalculationFlag.SIDEREAL);

            assertEquals(0.0,
                    angularDistance(sidereal.longitude(), tropical.longitude() - ayanamsa),
                    1.0e-6);
            assertNotEquals(tropical.longitude(), sidereal.longitude());
        }
    }

    // ------------------------------------------------------------------
    // Fixed stars
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Sirius sits where the catalogue puts it")
    void siriusMatchesTheCatalogue() {
        NativeTestSupport.requireFixedStarCatalogue();
        try (SwissEph swe = NativeTestSupport.open()) {
            FixedStarPosition sirius = swe.fixedStar2Ut(J2000, "Sirius", ephemerisFlag());

            assertEquals("Sirius", sirius.traditionalName());
            assertFalse(sirius.nomenclatureName().isBlank(),
                    "the library resolves the nomenclature name into the same buffer");
            // Sirius stands near 14 degrees of Cancer, far south of the ecliptic.
            assertEquals(104.1, sirius.position().longitude(), 1.0,
                    "Sirius longitude was " + sirius.position().longitude());
            assertEquals(-39.6, sirius.position().latitude(), 1.0,
                    "Sirius latitude was " + sirius.position().latitude());
            assertEquals(-1.46, swe.fixedStarMagnitude("Sirius"), 0.1);

            // The indexed and sequential readers must agree exactly.
            FixedStarPosition sequential = swe.fixedStarUt(J2000, "Sirius", ephemerisFlag());
            assertEquals(sequential.position().longitude(), sirius.position().longitude(), 1.0e-9);
        }
    }

    // ------------------------------------------------------------------
    // Introspection
    // ------------------------------------------------------------------

    @Test
    @DisplayName("The library reports which data files a result actually came from")
    void reportsTheDataFilesInUse() {
        Path ephemeris = NativeTestSupport.requireEphemerisDirectory();
        try (SwissEph swe = NativeTestSupport.open()) {
            swe.setEphemerisPath(ephemeris);

            EphemerisPosition sun = swe.calculateUt(
                    J2000, CelestialBody.SUN, CalculationFlag.SWISS_EPHEMERIS);
            assertTrue(sun.returnedFlags().used(Ephemeris.SWISS),
                    "with data files present the Swiss ephemeris must not be downgraded: "
                            + sun.warning());

            EphemerisFile planetFile = swe.currentFile(EphemerisFileSlot.PLANET).orElseThrow();
            assertTrue(planetFile.path().endsWith(".se1"),
                    "expected a Swiss Ephemeris data file but got " + planetFile.path());
            assertTrue(planetFile.covers(J2000),
                    planetFile.path() + " does not cover J2000");
            assertTrue(planetFile.jplNumber().orElseThrow() >= 400,
                    "a Swiss Ephemeris file records the JPL series it derives from, got "
                            + planetFile.jplEphemerisNumber());

            swe.calculateUt(J2000, CelestialBody.MOON, CalculationFlag.SWISS_EPHEMERIS);
            assertTrue(swe.currentFiles().containsKey(EphemerisFileSlot.MOON));
        }
    }

    @Test
    @DisplayName("A missing data file is reported as a downgrade, not as a success")
    void aMissingDataFileIsVisibleInTheReturnedFlags() {
        try (SwissEph swe = NativeTestSupport.openWithoutData()) {
            // Point the library at a directory that holds nothing.
            swe.setEphemerisPath(System.getProperty("java.io.tmpdir") + "/swisseph-absent");

            EphemerisPosition sun = swe.calculateUt(
                    J2000, CelestialBody.SUN, CalculationFlag.SWISS_EPHEMERIS);

            // This is the whole point of keeping requested and returned flags apart:
            // the call succeeds, but not with the ephemeris that was asked for.
            assertFalse(sun.returnedFlags().used(Ephemeris.SWISS));
            assertEquals(Ephemeris.MOSHIER, sun.returnedFlags().ephemeris().orElseThrow());
            assertFalse(sun.warning().isBlank(),
                    "the library explains the substitution through serr");
        }
    }

    // ------------------------------------------------------------------
    // Utilities
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Degree splitting produces the documented components")
    void degreeSplittingIsExact() {
        try (SwissEph swe = NativeTestSupport.open()) {
            DegreeParts plain = swe.splitDegrees(12.5);
            assertEquals(12, plain.degrees());
            assertEquals(30, plain.minutes());
            assertEquals(0, plain.seconds());
            assertEquals(1, plain.signum());
            assertFalse(plain.isZodiacal());

            // 35.5 degrees is 5 degrees 30 minutes of Taurus, the second sign.
            DegreeParts zodiacal = swe.splitDegrees(35.5, DegreeSplitOption.ZODIACAL);
            assertEquals(5, zodiacal.degrees());
            assertEquals(30, zodiacal.minutes());
            assertEquals(1, zodiacal.zodiacSign());

            DegreeParts negative = swe.splitDegrees(-12.5);
            assertEquals(-1, negative.signum());
            assertEquals(12, negative.degrees());
        }
    }

    @Test
    @DisplayName("Body names come back from the library")
    void bodyNamesComeFromTheLibrary() {
        try (SwissEph swe = NativeTestSupport.open()) {
            assertEquals("Sun", swe.bodyName(CelestialBody.SUN));
            assertEquals("Moon", swe.bodyName(CelestialBody.MOON));
            assertEquals("Pluto", swe.bodyName(CelestialBody.PLUTO));
        }
    }

    /** Prefers the real data files when the build supplied them, Moshier otherwise. */
    private static CalculationFlag ephemerisFlag() {
        return NativeTestSupport.ephemerisDirectory().isPresent()
                ? CalculationFlag.SWISS_EPHEMERIS
                : CalculationFlag.MOSHIER_EPHEMERIS;
    }
}
