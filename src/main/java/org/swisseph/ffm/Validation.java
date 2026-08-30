package org.swisseph.ffm;

/**
 * Argument checks applied before anything reaches native memory.
 *
 * <p>Swiss Ephemeris is written in C and does not defend itself: a {@code NaN}
 * Julian day makes its search loops run to their iteration cap, an out-of-range
 * latitude produces silently wrong houses, and an absurd magnitude makes an
 * eclipse search spin. Rejecting these in Java turns a confusing result into an
 * immediate {@link IllegalArgumentException}.</p>
 */
final class Validation {
    /**
     * Lowest Julian day accepted. Below the start of the Swiss Ephemeris data
     * range, which begins at JD -3027215.5 in year -13200, so the library itself
     * still gets to report genuine range errors through {@code serr}.
     */
    static final double MIN_JULIAN_DAY = -3_100_000.0;
    /**
     * Highest Julian day accepted. Above the end of the data range, which stops
     * at JD 7857131.5 in year 17191.
     */
    static final double MAX_JULIAN_DAY = 8_000_000.0;

    /** Absolute zero: no temperature below this is physically meaningful. */
    static final double MIN_TEMPERATURE_CELSIUS = -273.15;
    /** Generous upper bound; real atmospheric readings stay far below it. */
    static final double MAX_TEMPERATURE_CELSIUS = 1_000.0;
    /** Highest atmospheric pressure accepted, in millibar. */
    static final double MAX_PRESSURE_MILLIBAR = 100_000.0;

    /** {@code SEI_ECL_GEOALT_MIN}: the eclipse routines reject anything lower. */
    static final double MIN_ECLIPSE_ALTITUDE_METERS = -500.0;
    /** {@code SEI_ECL_GEOALT_MAX}: the eclipse routines reject anything higher. */
    static final double MAX_ECLIPSE_ALTITUDE_METERS = 25_000.0;

    /**
     * Where the barometric model behind {@code atpress == 0} breaks down.
     *
     * <p>Swiss Ephemeris derives the pressure as
     * {@code 1013.25 * pow(1 - 0.0065 * altitude / 288, 5.255)}. The base turns
     * negative above {@code 288 / 0.0065}, and a negative base with a
     * fractional exponent is {@code NaN}, which then flows silently into the
     * refracted altitude.</p>
     */
    static final double MAX_DERIVED_PRESSURE_ALTITUDE_METERS = 288.0 / 0.0065;

    /**
     * Rejects a raw {@code sid_mode} the library would silently replace.
     *
     * <p>{@code swe_set_sid_mode()} clamps a negative value to zero and, for any
     * base it does not define, falls back to Fagan/Bradley without saying so.
     * The snapshot in {@code settings()} would go on reporting the value that
     * was asked for.</p>
     */
    static int siderealMode(int mode) {
        if (mode < 0) {
            throw new IllegalArgumentException(
                    "sidereal mode must not be negative, but was " + mode);
        }
        int base = mode & 0xFF;
        int options = mode & ~0xFF;
        if (SiderealMode.of(base).isEmpty()) {
            throw new IllegalArgumentException("sidereal mode " + base
                    + " is not one this library defines; upstream would silently substitute "
                    + "Fagan/Bradley. See SiderealMode for the defined values.");
        }
        int knownOptions = 0;
        for (SiderealOption option : SiderealOption.values()) {
            knownOptions |= option.value();
        }
        if ((options & ~knownOptions) != 0) {
            throw new IllegalArgumentException("sidereal mode carries unknown option bits 0x"
                    + Integer.toHexString(options & ~knownOptions));
        }
        siderealOptions(SiderealMode.of(base).orElseThrow(), options);
        return mode;
    }

    /**
     * Rejects option bits the chosen ayanamsha would neutralise.
     *
     * <p>Every case here is one where {@code swe_set_sid_mode()} accepts the
     * request and then computes something else, leaving the snapshot in
     * {@code settings()} describing a configuration that is not in force.</p>
     */
    private static void siderealOptions(SiderealMode base, int options) {
        boolean eclipticAtT0 = (options & SiderealOption.ECLIPTIC_AT_T0.value()) != 0;
        boolean solarSystemPlane = (options & SiderealOption.SOLAR_SYSTEM_PLANE.value()) != 0;
        if (eclipticAtT0 && solarSystemPlane) {
            // The projection is chosen by an if/else-if that tests ECL_T0 first.
            throw new IllegalArgumentException("ECLIPTIC_AT_T0 and SOLAR_SYSTEM_PLANE select "
                    + "different projections and cannot be combined; upstream would silently "
                    + "use ECLIPTIC_AT_T0");
        }
        if ((options & SiderealOption.USER_T0_IN_UT.value()) != 0
                && base != SiderealMode.USER) {
            // Only read inside the SE_SIDM_USER branch.
            throw new IllegalArgumentException(
                    "USER_T0_IN_UT only means anything with SiderealMode.USER, but the mode is "
                            + base);
        }
        if (base.ignoresOptions() && options != 0) {
            throw new IllegalArgumentException(base + " is computed directly and upstream "
                    + "discards its option bits; requesting any would describe a configuration "
                    + "that is not in force");
        }
        if (base.forcesEclipticAtT0()
                && (options & ~SiderealOption.ECLIPTIC_AT_T0.value()) != 0) {
            throw new IllegalArgumentException(base + " is defined by its reference frame and "
                    + "upstream replaces its options with ECLIPTIC_AT_T0, so no other option "
                    + "can be honoured");
        }
    }

    /** Rejects an observer the eclipse routines would refuse. */
    static GeographicPosition eclipseObserver(GeographicPosition observer) {
        double altitude = observer.altitudeMeters();
        if (altitude < MIN_ECLIPSE_ALTITUDE_METERS || altitude > MAX_ECLIPSE_ALTITUDE_METERS) {
            throw new IllegalArgumentException("the eclipse routines accept altitudes between "
                    + MIN_ECLIPSE_ALTITUDE_METERS + " and " + MAX_ECLIPSE_ALTITUDE_METERS
                    + " metres, but was " + altitude);
        }
        return observer;
    }

    /**
     * Rejects the pairing of a very high observer with a derived pressure, which
     * the native barometric model turns into {@code NaN}.
     */
    static void pressureModel(GeographicPosition observer, AtmosphericConditions atmosphere) {
        // Strictly above: at exactly the limit the base is zero and pow() is
        // still finite.
        if (atmosphere.derivesPressureFromAltitude()
                && observer.altitudeMeters() > MAX_DERIVED_PRESSURE_ALTITUDE_METERS) {
            throw new IllegalArgumentException("deriving the pressure from an altitude of "
                    + observer.altitudeMeters() + " m is not possible: the native barometric "
                    + "model returns NaN above " + MAX_DERIVED_PRESSURE_ALTITUDE_METERS
                    + " m. Give an explicit pressure instead.");
        }
    }

    private Validation() {
    }

    static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number, but was " + value);
        }
        return value;
    }

    static double julianDay(double value, String name) {
        finite(value, name);
        if (value < MIN_JULIAN_DAY || value > MAX_JULIAN_DAY) {
            throw new IllegalArgumentException(name + " must be between " + MIN_JULIAN_DAY
                    + " and " + MAX_JULIAN_DAY + ", but was " + value);
        }
        return value;
    }

    static double longitude(double value) {
        finite(value, "longitude");
        if (value < -180.0 || value > 180.0) {
            throw new IllegalArgumentException(
                    "longitude must be between -180 and 180 degrees, but was " + value);
        }
        return value;
    }

    static double latitude(double value) {
        finite(value, "latitude");
        if (value < -90.0 || value > 90.0) {
            throw new IllegalArgumentException(
                    "latitude must be between -90 and 90 degrees, but was " + value);
        }
        return value;
    }

    static double altitude(double value) {
        return finite(value, "altitudeMeters");
    }

    static double pressure(double value) {
        finite(value, "pressureMillibar");
        if (value < 0.0 || value > MAX_PRESSURE_MILLIBAR) {
            throw new IllegalArgumentException("pressureMillibar must be between 0 and "
                    + MAX_PRESSURE_MILLIBAR + ", but was " + value);
        }
        return value;
    }

    static double temperature(double value) {
        finite(value, "temperatureCelsius");
        if (value <= MIN_TEMPERATURE_CELSIUS || value > MAX_TEMPERATURE_CELSIUS) {
            throw new IllegalArgumentException("temperatureCelsius must be above "
                    + MIN_TEMPERATURE_CELSIUS + " and at most " + MAX_TEMPERATURE_CELSIUS
                    + ", but was " + value);
        }
        return value;
    }

    static double degrees(double value, String name) {
        return finite(value, name);
    }

    static int inRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and "
                    + maximum + ", but was " + value);
        }
        return value;
    }
}
