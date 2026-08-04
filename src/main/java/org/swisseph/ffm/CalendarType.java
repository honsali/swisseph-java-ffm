package org.swisseph.ffm;

/** Calendar convention understood by Swiss Ephemeris. */
public enum CalendarType {
    JULIAN(0),
    GREGORIAN(1);

    private final int nativeValue;

    CalendarType(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    int nativeValue() {
        return nativeValue;
    }
}
