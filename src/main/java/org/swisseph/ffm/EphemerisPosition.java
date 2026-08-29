package org.swisseph.ffm;

/**
 * The six values returned by {@code swe_calc()} or {@code swe_calc_ut()}.
 *
 * <p>Their coordinate system and unit depend on the flags that were requested,
 * which is why the components are named neutrally. The aliases below are valid
 * only for the default polar ecliptic output.</p>
 *
 * @param returnedFlags what the library actually computed, which may differ
 *                      from what was asked for
 * @param warning       the contents of {@code serr}; non-empty even on success
 *                      when the library downgraded the request
 */
public record EphemerisPosition(
        double firstCoordinate,
        double secondCoordinate,
        double thirdCoordinate,
        double firstCoordinateSpeed,
        double secondCoordinateSpeed,
        double thirdCoordinateSpeed,
        ReturnedFlags returnedFlags,
        String warning) {

    public EphemerisPosition {
        warning = warning == null ? "" : warning;
    }

    /** Ecliptic longitude in degrees, for the default polar output. */
    public double longitude() {
        return firstCoordinate;
    }

    /** Ecliptic latitude in degrees, for the default polar output. */
    public double latitude() {
        return secondCoordinate;
    }

    /** Distance in astronomical units, for the default polar output. */
    public double distance() {
        return thirdCoordinate;
    }

    /** Longitude speed in degrees per day; zero unless {@code SPEED} was requested. */
    public double longitudeSpeed() {
        return firstCoordinateSpeed;
    }

    /** Latitude speed in degrees per day; zero unless {@code SPEED} was requested. */
    public double latitudeSpeed() {
        return secondCoordinateSpeed;
    }

    /** Distance speed in AU per day; zero unless {@code SPEED} was requested. */
    public double distanceSpeed() {
        return thirdCoordinateSpeed;
    }

    /**
     * Whether the body is retrograde, that is moving backwards in ecliptic
     * longitude.
     *
     * <p>Only meaningful for polar ecliptic output with speed. In cartesian
     * output the first speed is {@code dx/dt}, and in equatorial output it is
     * the motion in right ascension; a negative value there says nothing about
     * retrograde motion. Rather than answer from the wrong number, those cases
     * are refused.</p>
     *
     * @throws IllegalStateException if speed was not requested, or if the
     *                               coordinates are cartesian or equatorial
     */
    public boolean isRetrograde() {
        if (!returnedFlags.has(CalculationFlag.SPEED)
                && !returnedFlags.has(CalculationFlag.SPEED_THREE_POINT)) {
            throw new IllegalStateException(
                    "speed was not requested, so the speed components are zero; "
                            + "add CalculationFlag.SPEED to ask about retrograde motion");
        }
        if (returnedFlags.has(CalculationFlag.CARTESIAN)) {
            throw new IllegalStateException("these are cartesian coordinates, so the first speed "
                    + "is dx/dt rather than motion in longitude");
        }
        if (returnedFlags.has(CalculationFlag.EQUATORIAL)) {
            throw new IllegalStateException("these are equatorial coordinates, so the first speed "
                    + "is motion in right ascension rather than in ecliptic longitude");
        }
        return firstCoordinateSpeed < 0.0;
    }
}
