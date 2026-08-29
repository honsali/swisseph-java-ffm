package org.swisseph.ffm;

/** The pair of Julian days returned by {@code swe_utc_to_jd()}. */
public record JulianDate(double ephemerisTime, double universalTime) {
    /** Returns {@code ephemerisTime - universalTime}, that is delta T in days. */
    public double deltaTDays() {
        return ephemerisTime - universalTime;
    }
}
