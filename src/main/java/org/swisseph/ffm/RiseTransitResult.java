package org.swisseph.ffm;

/** Result of {@code swe_rise_trans()}, including the circumpolar no-event case. */
public record RiseTransitResult(boolean found, double julianDayUt, String message) {
    public RiseTransitResult {
        message = message == null ? "" : message;
        if (found && !Double.isFinite(julianDayUt)) {
            throw new IllegalArgumentException("julianDayUt must be finite when an event was found");
        }
    }
}
