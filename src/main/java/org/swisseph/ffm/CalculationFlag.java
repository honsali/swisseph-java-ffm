package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Collection;

/** Flags accepted by {@code swe_calc()} and {@code swe_calc_ut()}. */
public enum CalculationFlag {
    JPL_EPHEMERIS(1),
    SWISS_EPHEMERIS(2),
    MOSHIER_EPHEMERIS(4),
    HELIOCENTRIC(8),
    TRUE_POSITION(16),
    J2000(32),
    NO_NUTATION(64),
    SPEED_THREE_POINT(128),
    SPEED(256),
    NO_GRAVITATIONAL_DEFLECTION(512),
    NO_ABERRATION(1_024),
    EQUATORIAL(2_048),
    CARTESIAN(4_096),
    RADIANS(8_192),
    BARYCENTRIC(16_384),
    TOPOCENTRIC(32_768),
    SIDEREAL(65_536),
    ICRS(131_072),
    JPL_HORIZONS(262_144),
    JPL_HORIZONS_APPROXIMATE(524_288),
    CENTER_OF_BODY(1_048_576);

    private final int mask;

    CalculationFlag(int mask) {
        this.mask = mask;
    }

    /** Returns the bit value declared by Swiss Ephemeris 2.10.03. */
    public int value() {
        return mask;
    }

    /** Combines zero or more flags into the bit mask expected by the C API. */
    public static int mask(CalculationFlag... flags) {
        if (flags == null) {
            throw new NullPointerException("flags");
        }
        return mask(Arrays.asList(flags));
    }

    /** Combines zero or more flags into the bit mask expected by the C API. */
    public static int mask(Collection<CalculationFlag> flags) {
        if (flags == null) {
            throw new NullPointerException("flags");
        }
        int result = 0;
        for (CalculationFlag flag : flags) {
            result |= java.util.Objects.requireNonNull(flag, "flag").mask;
        }
        return result;
    }
}
