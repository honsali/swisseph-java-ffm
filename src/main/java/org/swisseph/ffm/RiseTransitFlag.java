package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/** Event and option bits accepted by {@code swe_rise_trans()}. */
public enum RiseTransitFlag {
    /** {@code SE_CALC_RISE}. */
    RISE(1),
    /** {@code SE_CALC_SET}. */
    SET(2),
    /** {@code SE_CALC_MTRANSIT}. */
    UPPER_MERIDIAN_TRANSIT(4),
    /** {@code SE_CALC_ITRANSIT}. */
    LOWER_MERIDIAN_TRANSIT(8),
    /** {@code SE_BIT_GEOCTR_NO_ECL_LAT}. */
    GEOCENTRIC_NO_ECLIPTIC_LATITUDE(128),
    /** {@code SE_BIT_DISC_CENTER}. */
    DISC_CENTER(256),
    /** {@code SE_BIT_NO_REFRACTION}. */
    NO_REFRACTION(512),
    /** {@code SE_BIT_CIVIL_TWILIGHT}. */
    CIVIL_TWILIGHT(1_024),
    /** {@code SE_BIT_NAUTIC_TWILIGHT}. */
    NAUTICAL_TWILIGHT(2_048),
    /** {@code SE_BIT_ASTRO_TWILIGHT}. */
    ASTRONOMICAL_TWILIGHT(4_096),
    /** {@code SE_BIT_DISC_BOTTOM}. */
    DISC_BOTTOM(8_192),
    /** {@code SE_BIT_FIXED_DISC_SIZE}. */
    FIXED_DISC_SIZE(16_384),
    /** {@code SE_BIT_HINDU_RISING}, a composite of three option bits. */
    HINDU_RISING(128 | 256 | 512);

    private final int mask;

    RiseTransitFlag(int mask) {
        this.mask = mask;
    }

    public int value() {
        return mask;
    }

    public static int mask(RiseTransitFlag... flags) {
        Objects.requireNonNull(flags, "flags");
        return mask(Arrays.asList(flags));
    }

    public static int mask(Collection<RiseTransitFlag> flags) {
        Objects.requireNonNull(flags, "flags");
        int result = 0;
        for (RiseTransitFlag flag : flags) {
            result |= Objects.requireNonNull(flag, "flag").value();
        }
        return result;
    }
}
