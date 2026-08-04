package org.swisseph.ffm;

/** Geographic longitude, latitude, and height used by observer-dependent calls. */
public record GeographicPosition(double longitude, double latitude, double altitudeMeters) {
    public GeographicPosition {
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude must be finite and between -180 and 180 degrees");
        }
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude must be finite and between -90 and 90 degrees");
        }
        if (!Double.isFinite(altitudeMeters)) {
            throw new IllegalArgumentException("altitudeMeters must be finite");
        }
    }
}
