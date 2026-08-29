package org.swisseph.ffm;

/**
 * A longitude and latitude reported by an eclipse calculation.
 *
 * <p>Distinct from {@link GeographicPosition}, which validates its inputs:
 * {@code swe_sol_eclipse_where()} uses {@code -99} to mean "this limit does not
 * exist", and a validating type would reject its own output.</p>
 */
public record GeographicPoint(double longitude, double latitude) {
    /** The value Swiss Ephemeris writes for a shadow limit that does not exist. */
    public static final double UNDEFINED = -99.0;

    /** Returns whether the library actually computed this point. */
    public boolean isDefined() {
        return longitude != UNDEFINED && latitude != UNDEFINED
                && Double.isFinite(longitude) && Double.isFinite(latitude);
    }

    /** Converts to a validated observer position at sea level. */
    public GeographicPosition toObserver() {
        if (!isDefined()) {
            throw new IllegalStateException("this eclipse point is undefined: " + this);
        }
        return new GeographicPosition(longitude, latitude, 0.0);
    }
}
