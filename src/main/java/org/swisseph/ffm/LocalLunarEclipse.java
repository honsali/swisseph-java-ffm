package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Objects;

/**
 * A lunar eclipse found by {@code swe_lun_eclipse_when_loc()}, that is the next
 * or previous eclipse visible from one place.
 *
 * <p>All times are Julian days in universal time.</p>
 */
public record LocalLunarEclipse(
        EclipseFlags flags,
        double[] times,
        LunarEclipseAttributes attributes,
        String warning) {

    private static final int TIME_COUNT = 10;

    public LocalLunarEclipse {
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

    /** {@code tret[0]}: moment of greatest eclipse. */
    public double maximum() {
        return times[0];
    }

    /** {@code tret[2]}: start of the partial phase. */
    public double partialBegin() {
        return times[2];
    }

    /** {@code tret[3]}: end of the partial phase. */
    public double partialEnd() {
        return times[3];
    }

    /** {@code tret[4]}: start of totality. */
    public double totalityBegin() {
        return times[4];
    }

    /** {@code tret[5]}: end of totality. */
    public double totalityEnd() {
        return times[5];
    }

    /** {@code tret[6]}: start of the penumbral phase. */
    public double penumbralBegin() {
        return times[6];
    }

    /** {@code tret[7]}: end of the penumbral phase. */
    public double penumbralEnd() {
        return times[7];
    }

    /** {@code tret[8]}: moonrise, if it falls during the eclipse. */
    public double moonrise() {
        return times[8];
    }

    /** {@code tret[9]}: moonset, if it falls during the eclipse. */
    public double moonset() {
        return times[9];
    }

    /** Whether any part of the eclipse is above the horizon at this place. */
    public boolean isVisible() {
        return flags.has(EclipseVisibility.VISIBLE);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LocalLunarEclipse that
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
        return "LocalLunarEclipse[flags=" + flags + ", maximum=" + maximum()
                + ", times=" + Arrays.toString(times) + ", attributes=" + attributes
                + ", warning=" + warning + "]";
    }
}
