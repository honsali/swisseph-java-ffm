package org.swisseph.ffm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendedApiValueTest {
    @Test
    void validatesGeographicPositions() {
        assertEquals(new GeographicPosition(-7.5898, 33.5731, 20.0),
                new GeographicPosition(-7.5898, 33.5731, 20.0));
        assertThrows(IllegalArgumentException.class,
                () -> new GeographicPosition(181.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new GeographicPosition(0.0, Double.NaN, 0.0));
    }

    @Test
    void combinesRiseTransitAndEclipseFlags() {
        assertEquals(1 | 256 | 512, RiseTransitFlag.mask(
                RiseTransitFlag.RISE,
                RiseTransitFlag.DISC_CENTER,
                RiseTransitFlag.NO_REFRACTION));
        assertEquals(4 | 1 | 2, EclipseFlag.mask(
                EclipseFlag.TOTAL,
                EclipseFlag.CENTRAL,
                EclipseFlag.NON_CENTRAL));
    }

    @Test
    void houseCuspsAreDefensivelyCopied() {
        double[] cusps = new double[13];
        double[] additionalPoints = new double[10];
        cusps[1] = 42.0;
        additionalPoints[0] = 84.0;

        HouseCusps result = new HouseCusps(cusps, additionalPoints);
        cusps[1] = 0.0;
        additionalPoints[0] = 0.0;

        assertEquals(42.0, result.cusp(1));
        assertEquals(84.0, result.ascendant());
        double[] returned = result.cusps();
        returned[1] = 0.0;
        assertEquals(42.0, result.cusp(1));
    }

    @Test
    void eclipseResultsPreserveMasksAndProtectArrays() {
        double[] times = new double[10];
        double[] attributes = new double[20];
        times[0] = 2_451_545.0;
        attributes[0] = 1.02;

        EclipseResult result = new EclipseResult(
                EclipseFlag.TOTAL.value() | EclipseFlag.CENTRAL.value(),
                times,
                attributes,
                new double[0],
                null);
        times[0] = 0.0;

        assertTrue(result.has(EclipseFlag.TOTAL));
        assertTrue(result.has(EclipseFlag.CENTRAL));
        assertFalse(result.has(EclipseFlag.PARTIAL));
        assertEquals(2_451_545.0, result.time(0));
        assertEquals(1.02, result.attribute(0));
        assertArrayEquals(new double[0], result.geographicPositions());
        assertEquals("", result.warning());
    }

    @Test
    void planetaryPhenomenaProtectAttributesAndExposeNamedValues() {
        double[] attributes = new double[20];
        attributes[0] = 30.0;
        attributes[1] = 0.75;
        attributes[2] = 45.0;
        attributes[3] = 0.5;
        attributes[4] = -12.3;

        PlanetaryPhenomena result = new PlanetaryPhenomena(attributes, null);
        attributes[0] = 0.0;

        assertEquals(30.0, result.phaseAngle());
        assertEquals(0.75, result.illuminatedFraction());
        assertEquals(45.0, result.elongation());
        assertEquals(0.5, result.apparentDiameter());
        assertEquals(-12.3, result.apparentMagnitude());
        assertEquals("", result.warning());

        double[] returned = result.attributes();
        returned[0] = 0.0;
        assertEquals(30.0, result.phaseAngle());
    }
}
