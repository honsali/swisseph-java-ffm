package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/** Event and option bits accepted by {@code swe_rise_trans()}. */
public enum RiseTransitFlag {
    RISE(1),
    SET(2),
    UPPER_MERIDIAN_TRANSIT(4),
    LOWER_MERIDIAN_TRANSIT(8),
    GEOCENTRIC_NO_ECLIPTIC_LATITUDE(128),
    DISC_CENTER(256),
    NO_REFRACTION(512),
    CIVIL_TWILIGHT(1_024),
    NAUTICAL_TWILIGHT(2_048),
    ASTRONOMICAL_TWILIGHT(4_096),
    DISC_BOTTOM(8_192),
    FIXED_DISC_SIZE(16_384),
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
            result |= Objects.requireNonNull(flag, "flag").mask;
        }
        return result;
    }
}
