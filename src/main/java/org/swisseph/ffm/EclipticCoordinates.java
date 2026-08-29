package org.swisseph.ffm;

/**
 * Ecliptic or equatorial coordinates returned by {@code swe_azalt_rev()}.
 *
 * <p>Which of the two systems the values belong to is decided by the
 * {@link HorizontalCoordinateType} passed to the call.</p>
 */
public record EclipticCoordinates(double longitude, double latitude) {
}
