package org.swisseph.ffm;

import java.util.Optional;

/** House systems understood by Swiss Ephemeris. */
public enum HouseSystem {
    ALCABITIUS('B'),
    APC('Y'),
    MERIDIAN('X'),
    HORIZONTAL('H'),
    CAMPANUS('C'),
    CARTER_POLI_EQUATORIAL('F'),
    EQUAL_ASCENDANT('A'),
    EQUAL_ASCENDANT_ALTERNATE('E'),
    EQUAL_MC('D'),
    EQUAL_ARIES('N'),
    /** Gauquelin sectors: 36 divisions rather than 12 houses. */
    GAUQUELIN('G'),
    SUNSHINE_TREINDL('I'),
    SUNSHINE_MAKRANSKY('i'),
    KOCH('K'),
    KRUSINSKI_PISA_GOELZER('U'),
    MORINUS('M'),
    PLACIDUS('P'),
    POLICH_PAGE('T'),
    PORPHYRY('O'),
    PULLEN_SD('L'),
    PULLEN_SR('Q'),
    /** Savard's supposed Albategnius houses. */
    SAVARD_A('J'),
    REGIOMONTANUS('R'),
    SRIPATI('S'),
    VEHLOW('V'),
    WHOLE_SIGN('W');

    private final char code;

    HouseSystem(char code) {
        this.code = code;
    }

    /** Returns the ASCII house-system code expected by the C API. */
    public char code() {
        return code;
    }

    /**
     * Whether this system needs the Sun's declination as an input.
     *
     * <p>True only for the two Sunshine variants, which the native code selects
     * on {@code toupper(hsys) == 'I'}. They read the declination from
     * {@code ascmc[9]}; every other system leaves that slot alone.</p>
     */
    public boolean needsSolarDeclination() {
        return this == SUNSHINE_TREINDL || this == SUNSHINE_MAKRANSKY;
    }

    /** Gauquelin uses 36 sectors; every other system uses 12 houses. */
    public int houseCount() {
        return this == GAUQUELIN ? 36 : 12;
    }

    /**
     * Size of the {@code cusps} array this system needs.
     *
     * <p>The array is one-based: index 0 is unused, so it holds one more element
     * than there are houses.</p>
     */
    public int cuspArrayLength() {
        return houseCount() + 1;
    }

    /** Looks up a system by its native code letter. */
    public static Optional<HouseSystem> ofCode(char code) {
        for (HouseSystem system : values()) {
            if (system.code == code) {
                return Optional.of(system);
            }
        }
        return Optional.empty();
    }
}
