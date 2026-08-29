package org.swisseph.ffm;

/**
 * Azimuth and altitude returned by {@code swe_azalt()}.
 *
 * @param azimuth         degrees measured from south, turning westwards
 * @param trueAltitude    geometric altitude, refraction excluded
 * @param apparentAltitude altitude as seen through the atmosphere
 */
public record HorizontalCoordinates(double azimuth, double trueAltitude, double apparentAltitude) {
}
