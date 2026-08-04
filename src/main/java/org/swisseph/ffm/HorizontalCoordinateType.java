package org.swisseph.ffm;

/** Input coordinate systems accepted by {@code swe_azalt()}. */
public enum HorizontalCoordinateType {
    ECLIPTIC(0),
    EQUATORIAL(1);

    private final int nativeValue;

    HorizontalCoordinateType(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    /** Returns {@code SE_ECL2HOR} or {@code SE_EQU2HOR}. */
    public int nativeValue() {
        return nativeValue;
    }
}
