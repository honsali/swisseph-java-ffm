package org.swisseph.ffm;

/** The file slots reported by {@code swe_get_current_file_data()}. */
public enum EphemerisFileSlot {
    /** Planet file {@code sepl_*.se1}, or the JPL file when one is in use. */
    PLANET(0),
    /** Moon file {@code semo_*.se1}. */
    MOON(1),
    /** Main asteroid file {@code seas_*.se1}. */
    MAIN_ASTEROID(2),
    /** Individual asteroid or planetary-moon file, if one was opened. */
    OTHER_ASTEROID(3),
    /** Fixed-star file {@code sefstars.txt}. */
    FIXED_STAR(4);

    private final int nativeValue;

    EphemerisFileSlot(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    /** Returns the {@code ifno} value expected by the C API. */
    public int value() {
        return nativeValue;
    }
}
