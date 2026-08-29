package org.swisseph.ffm;

/** The three ephemerides Swiss Ephemeris can compute from. */
public enum Ephemeris {
    /** JPL file, selected by {@code SEFLG_JPLEPH}. */
    JPL(CalculationFlag.JPL_EPHEMERIS),
    /** Swiss Ephemeris {@code .se1} files, selected by {@code SEFLG_SWIEPH}. */
    SWISS(CalculationFlag.SWISS_EPHEMERIS),
    /** Built-in Moshier theory, selected by {@code SEFLG_MOSEPH}. Needs no data files. */
    MOSHIER(CalculationFlag.MOSHIER_EPHEMERIS);

    private final CalculationFlag flag;

    Ephemeris(CalculationFlag flag) {
        this.flag = flag;
    }

    public CalculationFlag flag() {
        return flag;
    }

    public int value() {
        return flag.value();
    }
}
