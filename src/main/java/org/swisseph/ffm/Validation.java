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
        if (atmosphere.derivesPressureFromAltitude()
                && observer.altitudeMeters() >= MAX_DERIVED_PRESSURE_ALTITUDE_METERS) {
            throw new IllegalArgumentException("deriving the pressure from an altitude of "
                    + observer.altitudeMeters() + " m is not possible: the native barometric "
                    + "model returns NaN at or above " + MAX_DERIVED_PRESSURE_ALTITUDE_METERS
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
