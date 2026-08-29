package org.swisseph.ffm;

/**
 * An observer position: geographic longitude, latitude, and height above sea
 * level in metres.
 *
 * <p>Longitude comes first, matching the {@code double geopos[3]} layout the C
 * API expects. Every observer-dependent method takes this type rather than two
 * bare doubles, so the two coordinates cannot be swapped by accident.</p>
 */
public record GeographicPosition(double longitude, double latitude, double altitudeMeters) {
    public GeographicPosition {
        Validation.longitude(longitude);
        Validation.latitude(latitude);
        Validation.altitude(altitudeMeters);
    }

    /** Creates a position at sea level. */
    public static GeographicPosition of(double longitude, double latitude) {
        return new GeographicPosition(longitude, latitude, 0.0);
    }
}
