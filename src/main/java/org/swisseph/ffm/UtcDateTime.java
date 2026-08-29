package org.swisseph.ffm;

/** A UTC timestamp with a fractional second, as exchanged with the C API. */
public record UtcDateTime(int year, int month, int day, int hour, int minute, double second) {
    public UtcDateTime {
        Validation.inRange(month, 1, 12, "month");
        Validation.inRange(day, 1, 31, "day");
        Validation.inRange(hour, 0, 23, "hour");
        Validation.inRange(minute, 0, 59, "minute");
        Validation.finite(second, "second");
        if (second < 0.0 || second >= 61.0) {
            throw new IllegalArgumentException(
                    "second must be in [0, 61) to allow for leap seconds, but was " + second);
        }
    }
}
