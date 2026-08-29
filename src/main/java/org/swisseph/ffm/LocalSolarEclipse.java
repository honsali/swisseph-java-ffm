package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Objects;

/**
 * A solar eclipse found by {@code swe_sol_eclipse_when_loc()}, that is the next
 * or previous eclipse visible from one place.
 *
 * <p>All times are Julian days in universal time. Consult
 * {@code flags().visibility()} to learn which contacts are above the horizon;
 * a contact that is not visible still carries a time.</p>
 */
public record LocalSolarEclipse(
        EclipseFlags flags,
        double[] times,
        SolarEclipseAttributes attributes,
        String warning) {

    private static final int TIME_COUNT = 10;

    public LocalSolarEclipse {
        Objects.requireNonNull(flags, "flags");
        Objects.requireNonNull(attributes, "attributes");
        times = Objects.requireNonNull(times, "times").clone();
        if (times.length != TIME_COUNT) {
            throw new IllegalArgumentException(
                    "times must contain " + TIME_COUNT + " values, but had " + times.length);
        }
        warning = warning == null ? "" : warning;
    }

    @Override
    public double[] times() {
        return times.clone();
    }

    /** {@code tret[0]}: moment of maximum eclipse at this place. */
    public double maximum() {
        return times[0];
    }

    /** {@code tret[1]}: first contact, when the eclipse begins. */
    public double firstContact() {
        return times[1];
    }

    /** {@code tret[2]}: second contact, when totality or annularity begins. */
    public double secondContact() {
        return times[2];
    }

    /** {@code tret[3]}: third contact, when totality or annularity ends. */
    public double thirdContact() {
        return times[3];
    }

    /** {@code tret[4]}: fourth contact, when the eclipse ends. */
    public double fourthContact() {
        return times[4];
    }

    /** {@code tret[5]}: sunrise, if it falls between first and fourth contact. */
    public double sunrise() {
        return times[5];
    }

    /** {@code tret[6]}: sunset, if it falls between first and fourth contact. */
    public double sunset() {
        return times[6];
    }

    /** Whether any part of the eclipse is above the horizon at this place. */
    public boolean isVisible() {
        return flags.has(EclipseVisibility.VISIBLE);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LocalSolarEclipse that
                && flags.equals(that.flags)
                && Arrays.equals(times, that.times)
                && attributes.equals(that.attributes)
                && warning.equals(that.warning);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flags, Arrays.hashCode(times), attributes, warning);
    }

    @Override
    public String toString() {
        return "LocalSolarEclipse[flags=" + flags + ", maximum=" + maximum()
                + ", times=" + Arrays.toString(times) + ", attributes=" + attributes
                + ", warning=" + warning + "]";
    }
}
