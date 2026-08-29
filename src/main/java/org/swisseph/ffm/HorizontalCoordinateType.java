package org.swisseph.ffm;

/** Input coordinate system accepted by {@code swe_azalt()}. */
public enum HorizontalCoordinateType {
    /** {@code SE_ECL2HOR}. */
    ECLIPTIC(0),
    /** {@code SE_EQU2HOR}. */
    EQUATORIAL(1);

    private final int nativeValue;

    HorizontalCoordinateType(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    public int value() {
        return nativeValue;
    }
}
