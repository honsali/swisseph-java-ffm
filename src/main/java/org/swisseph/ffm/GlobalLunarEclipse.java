package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Objects;

/**
 * A lunar eclipse found by {@code swe_lun_eclipse_when()}.
 *
 * <p>The {@code tret[]} indices deliberately mirror the solar layout, which is
 * why index 1 is unused. All times are Julian days in universal time.</p>
 */
public record GlobalLunarEclipse(EclipseFlags flags, double[] times, String warning) {
    private static final int TIME_COUNT = 10;

    public GlobalLunarEclipse {
        Objects.requireNonNull(flags, "flags");
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

    @Override
    public boolean equals(Object other) {
        return other instanceof GlobalLunarEclipse that
                && flags.equals(that.flags)
                && Arrays.equals(times, that.times)
                && warning.equals(that.warning);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flags, Arrays.hashCode(times), warning);
    }

    @Override
    public String toString() {
        return "GlobalLunarEclipse[flags=" + flags + ", maximum=" + maximum()
                + ", times=" + Arrays.toString(times) + ", warning=" + warning + "]";
    }
}
