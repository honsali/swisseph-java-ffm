package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * The {@code SE_SIDBIT_*} bits that modify a {@link SiderealMode}.
 *
 * <p>They are OR-ed onto the mode number rather than passed separately, which
 * is why they live in their own type: mixing them into {@link SiderealMode}
 * would make the mode lookup ambiguous.</p>
 */
public enum SiderealOption {
    /** {@code SE_SIDBIT_ECL_T0}: project onto the ecliptic of the reference epoch. */
    ECLIPTIC_AT_T0(256),
    /** {@code SE_SIDBIT_SSY_PLANE}: project onto the mean plane of the solar system. */
    SOLAR_SYSTEM_PLANE(512),
    /** {@code SE_SIDBIT_USER_UT}: the user-supplied {@code t0} is in universal time. */
    USER_T0_IN_UT(1_024),
    /** {@code SE_SIDBIT_ECL_DATE}: project onto the ecliptic of date. */
    ECLIPTIC_OF_DATE(2_048),
    /** {@code SE_SIDBIT_NO_PREC_OFFSET}. */
    NO_PRECESSION_OFFSET(4_096),
    /**
     * {@code SE_SIDBIT_PREC_ORIG}: use the precession model of the original
     * ayanamsha.
     *
     * <p>Present for completeness and <strong>rejected</strong> by this binding.
     * Upstream describes it as a test feature, and it does not configure the
     * sidereal mode: it overwrites the process-global
     * {@code swed.astro_models} in one direction only. Clearing the bit later
     * does not restore the previous models, and {@code swe_set_ephe_path()}
     * resets them without the bit changing, so no snapshot of it can stay
     * truthful.</p>
     */
    ORIGINAL_PRECESSION(8_192);

    private final int mask;

    SiderealOption(int mask) {
        this.mask = mask;
    }

    public int value() {
        return mask;
    }

    public static int mask(SiderealOption... options) {
        Objects.requireNonNull(options, "options");
        return mask(Arrays.asList(options));
    }

    public static int mask(Collection<SiderealOption> options) {
        Objects.requireNonNull(options, "options");
        int result = 0;
        for (SiderealOption option : options) {
            result |= Objects.requireNonNull(option, "option").value();
        }
        return result;
    }
}
