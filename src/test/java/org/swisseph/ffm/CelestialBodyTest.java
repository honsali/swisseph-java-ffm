package org.swisseph.ffm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CelestialBodyTest {
    @Test
    void exposesSwissEphemerisBodyIds() {
        assertEquals(0, CelestialBody.SUN.id());
        assertEquals(1, CelestialBody.MOON.id());
        assertEquals(9, CelestialBody.PLUTO.id());
        assertEquals(22, CelestialBody.INTERPOLATED_PERIGEE.id());
    }
}
