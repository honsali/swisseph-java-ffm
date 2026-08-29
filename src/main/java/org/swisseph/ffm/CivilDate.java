package org.swisseph.ffm;

/** A calendar date with the time of day as a decimal hour. */
public record CivilDate(int year, int month, int day, double hour) {
    public CivilDate {
        Validation.inRange(month, 1, 12, "month");
        Validation.inRange(day, 1, 31, "day");
        Validation.finite(hour, "hour");
    }
}
