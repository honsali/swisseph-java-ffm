package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Objects;

/**
 * A solar eclipse found by {@code swe_sol_eclipse_when_glob()}, that is the next
 * or previous eclipse anywhere on Earth.
 *
 * <p>All times are Julian days in universal time. Phases that do not apply to
 * this eclipse are left at zero by the library.</p>
 */
public record GlobalSolarEclipse(EclipseFlags flags, double[] times, String warning) {
    private static final int TIME_COUNT = 10;

    public GlobalSolarEclipse {
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

    /** {@code tret[1]}: the moment the eclipse is at local apparent noon. */
    public double localApparentNoon() {
        return times[1];
    }

    /** {@code tret[2]}: first contact anywhere on Earth. */
    public double begin() {
        return times[2];
    }

    /** {@code tret[3]}: last contact anywhere on Earth. */
    public double end() {
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

    /** {@code tret[6]}: start of the central line. */
    public double centerLineBegin() {
        return times[6];
    }

    /** {@code tret[7]}: end of the central line. */
    public double centerLineEnd() {
        return times[7];
    }

    /** {@code tret[8]}: the moment a hybrid eclipse turns total. */
    public double annularTotalBecomesTotal() {
        return times[8];
    }

    /** {@code tret[9]}: the moment a hybrid eclipse turns annular again. */
    public double annularTotalBecomesAnnular() {
        return times[9];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GlobalSolarEclipse that
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
        return "GlobalSolarEclipse[flags=" + flags + ", maximum=" + maximum()
                + ", times=" + Arrays.toString(times) + ", warning=" + warning + "]";
    }
}
