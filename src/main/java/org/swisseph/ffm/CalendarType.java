package org.swisseph.ffm;

/** Calendar convention understood by Swiss Ephemeris. */
public enum CalendarType {
    /** {@code SE_JUL_CAL}. */
    JULIAN(0),
    /** {@code SE_GREG_CAL}. */
    GREGORIAN(1);

    private final int nativeValue;

    CalendarType(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    /** Returns the {@code gregflag} value expected by the C API. */
    public int value() {
        return nativeValue;
    }
}
