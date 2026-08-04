package org.swisseph.ffm;

/** House systems understood by Swiss Ephemeris 2.10.03. */
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

    /** Gauquelin uses 36 sectors; all other systems use 12 houses. */
    public int houseCount() {
        return this == GAUQUELIN ? 36 : 12;
    }
}
