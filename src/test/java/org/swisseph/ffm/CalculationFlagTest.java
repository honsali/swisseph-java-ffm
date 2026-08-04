package org.swisseph.ffm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculationFlagTest {
    @Test
    void combinesNativeBitFlags() {
        int flags = CalculationFlag.mask(
                CalculationFlag.SWISS_EPHEMERIS,
                CalculationFlag.SPEED,
                CalculationFlag.EQUATORIAL);

        assertEquals(2 | 256 | 2_048, flags);
    }

    @Test
    void acceptsAnEmptyFlagList() {
        assertEquals(0, CalculationFlag.mask());
        assertEquals(0, CalculationFlag.mask(List.of()));
    }

    @Test
    void rejectsNullFlags() {
        assertThrows(NullPointerException.class,
                () -> CalculationFlag.mask(CalculationFlag.SPEED, null));
    }
}
