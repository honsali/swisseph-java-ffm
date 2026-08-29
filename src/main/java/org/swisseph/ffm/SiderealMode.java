package org.swisseph.ffm;

import java.util.Optional;

/**
 * Predefined ayanamshas accepted by {@code swe_set_sid_mode()}.
 *
 * <p>The numbering is part of the C API and is verified against the loaded
 * library by the integration suite, which asks {@code swe_get_ayanamsa_name()}
 * for every constant here and checks that the names are present and distinct.
 * A drift between this table and the native build therefore fails the tests
 * instead of silently producing positions for the wrong ayanamsha.</p>
 */
public enum SiderealMode {
    FAGAN_BRADLEY(0),
    LAHIRI(1),
    DELUCE(2),
    RAMAN(3),
    USHASHASHI(4),
    KRISHNAMURTI(5),
    DJWHAL_KHUL(6),
    YUKTESHWAR(7),
    JN_BHASIN(8),
    BABYL_KUGLER1(9),
    BABYL_KUGLER2(10),
    BABYL_KUGLER3(11),
    BABYL_HUBER(12),
    BABYL_ETPSC(13),
    ALDEBARAN_15TAU(14),
    HIPPARCHOS(15),
    SASSANIAN(16),
    GALCENT_0SAG(17),
    J2000(18),
    J1900(19),
    B1950(20),
    SURYASIDDHANTA(21),
    SURYASIDDHANTA_MSUN(22),
    ARYABHATA(23),
    ARYABHATA_MSUN(24),
    SS_REVATI(25),
    SS_CITRA(26),
    TRUE_CITRA(27),
    TRUE_REVATI(28),
    TRUE_PUSHYA(29),
    GALCENT_RGILBRAND(30),
    GALEQU_IAU1958(31),
    GALEQU_TRUE(32),
    GALEQU_MULA(33),
    GALALIGN_MARDYKS(34),
    TRUE_MULA(35),
    GALCENT_MULA_WILHELM(36),
    ARYABHATA_522(37),
    BABYL_BRITTON(38),
    TRUE_SHEORAN(39),
    GALCENT_COCHRANE(40),
    GALEQU_FIORENZA(41),
    VALENS_MOON(42),
    LAHIRI_1940(43),
    LAHIRI_VP285(44),
    KRISHNAMURTI_VP291(45),
    LAHIRI_ICRC(46),
    /** {@code SE_SIDM_USER}: the reference epoch and ayanamsha are supplied by the caller. */
    USER(255);

    private final int nativeValue;

    SiderealMode(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    /** Returns the {@code sid_mode} value expected by the C API. */
    public int value() {
        return nativeValue;
    }

    /** Looks up a mode by its native value. */
    public static Optional<SiderealMode> of(int nativeValue) {
        for (SiderealMode mode : values()) {
            if (mode.nativeValue == nativeValue) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
