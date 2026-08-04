package org.swisseph.ffm;

/** Horizontal coordinates returned by {@code swe_azalt()}. */
public record HorizontalCoordinates(
        double azimuth,
        double trueAltitude,
        double apparentAltitude) {
}
