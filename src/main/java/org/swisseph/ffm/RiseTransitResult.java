package org.swisseph.ffm;

import java.util.OptionalDouble;

/**
 * Outcome of a rise, set, or transit search.
 *
 * <p>Swiss Ephemeris returns {@code -2} when the event simply does not happen,
 * for instance during a polar night. That is a legitimate answer rather than a
 * failure, so it is reported as {@code found() == false} instead of an
 * exception.</p>
 */
public record RiseTransitResult(boolean found, double julianDayUt, String warning) {
    public RiseTransitResult {
        warning = warning == null ? "" : warning;
    }

    /** The event time, or empty when the event does not occur. */
    public OptionalDouble time() {
        return found ? OptionalDouble.of(julianDayUt) : OptionalDouble.empty();
    }
}
