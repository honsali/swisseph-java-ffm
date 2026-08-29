package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * Input bits for the {@code iflag} parameter of {@code swe_calc()} and friends.
 *
 * <p>This enum models what an application <em>asks</em> for. What the library
 * actually delivered comes back as {@link ReturnedFlags}, a deliberately
 * different type: Swiss Ephemeris silently downgrades the requested ephemeris
 * when a data file is missing, and code that compares the two needs the
 * compiler to keep them apart.</p>
 */
public enum CalculationFlag {
    /** {@code SEFLG_JPLEPH}: use a JPL ephemeris file. */
    JPL_EPHEMERIS(1),
    /** {@code SEFLG_SWIEPH}: use the Swiss Ephemeris data files. */
    SWISS_EPHEMERIS(2),
    /** {@code SEFLG_MOSEPH}: use the built-in Moshier theory, which needs no data files. */
    MOSHIER_EPHEMERIS(4),
    /** {@code SEFLG_HELCTR}. */
    HELIOCENTRIC(8),
    /** {@code SEFLG_TRUEPOS}. */
    TRUE_POSITION(16),
    /** {@code SEFLG_J2000}. */
    J2000(32),
    /** {@code SEFLG_NONUT}. */
    NO_NUTATION(64),
    /** {@code SEFLG_SPEED3}. */
    SPEED_THREE_POINT(128),
    /** {@code SEFLG_SPEED}. */
    SPEED(256),
    /** {@code SEFLG_NOGDEFL}. */
    NO_GRAVITATIONAL_DEFLECTION(512),
    /** {@code SEFLG_NOABERR}. */
    NO_ABERRATION(1_024),
    /** {@code SEFLG_EQUATORIAL}. */
    EQUATORIAL(2_048),
    /** {@code SEFLG_XYZ}. */
    CARTESIAN(4_096),
    /** {@code SEFLG_RADIANS}. */
    RADIANS(8_192),
    /** {@code SEFLG_BARYCTR}. */
    BARYCENTRIC(16_384),
    /** {@code SEFLG_TOPOCTR}: requires a topocentric observer to be configured first. */
    TOPOCENTRIC(32_768),
    /** {@code SEFLG_SIDEREAL}: requires a sidereal mode to be configured first. */
    SIDEREAL(65_536),
    /** {@code SEFLG_ICRS}. */
    ICRS(131_072),
    /** {@code SEFLG_JPLHOR}. */
    JPL_HORIZONS(262_144),
    /** {@code SEFLG_JPLHOR_APPROX}. */
    JPL_HORIZONS_APPROXIMATE(524_288),
    /** {@code SEFLG_CENTER_BODY}. */
    CENTER_OF_BODY(1_048_576);

    private final int mask;

    CalculationFlag(int mask) {
        this.mask = mask;
    }

    /** Returns the bit value declared by {@code swephexp.h}. */
    public int value() {
        return mask;
    }

    /** Combines zero or more flags into the bit mask expected by the C API. */
    public static int mask(CalculationFlag... flags) {
        Objects.requireNonNull(flags, "flags");
        return mask(Arrays.asList(flags));
    }

    /** Combines zero or more flags into the bit mask expected by the C API. */
    public static int mask(Collection<CalculationFlag> flags) {
        Objects.requireNonNull(flags, "flags");
        int result = 0;
        for (CalculationFlag flag : flags) {
            result |= Objects.requireNonNull(flag, "flag").value();
        }
        return result;
    }
}
