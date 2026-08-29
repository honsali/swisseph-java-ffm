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

    /** Returns whether the body is retrograde, that is moving backwards in longitude. */
    public boolean isRetrograde() {
        return firstCoordinateSpeed < 0.0;
    }
}
