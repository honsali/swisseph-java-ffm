package org.swisseph.ffm;

/**
 * Six values returned by {@code swe_calc()} or {@code swe_calc_ut()}.
 * Their exact coordinate system and unit depend on the requested flags.
 */
public record EphemerisPosition(
        double firstCoordinate,
        double secondCoordinate,
        double thirdCoordinate,
        double firstCoordinateSpeed,
        double secondCoordinateSpeed,
        double thirdCoordinateSpeed,
        int returnedFlags,
        String warning) {

    public EphemerisPosition {
        warning = warning == null ? "" : warning;
    }

    /** Convenience alias when polar ecliptic coordinates were requested. */
    public double longitude() {
        return firstCoordinate;
    }

    /** Convenience alias when polar ecliptic coordinates were requested. */
    public double latitude() {
        return secondCoordinate;
    }

    /** Convenience alias when polar coordinates were requested. */
    public double distance() {
        return thirdCoordinate;
    }

    /** Convenience alias when polar coordinates and speed were requested. */
    public double longitudeSpeed() {
        return firstCoordinateSpeed;
    }

    /** Convenience alias when polar coordinates and speed were requested. */
    public double latitudeSpeed() {
        return secondCoordinateSpeed;
    }

    /** Convenience alias when polar coordinates and speed were requested. */
    public double distanceSpeed() {
        return thirdCoordinateSpeed;
    }
}
