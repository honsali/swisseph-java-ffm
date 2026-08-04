package org.swisseph.ffm;

import java.util.Objects;

/**
 * Raw, lossless result of a solar or lunar eclipse call.
 *
 * <p>Depending on the function, {@code times} contains 0 or 10 values,
 * {@code attributes} contains 0 or 20 values, and {@code geographicPositions}
 * contains 0 or 10 values. The array indices follow {@code swephexp.h}.</p>
 */
public record EclipseResult(
        int flags,
        double[] times,
        double[] attributes,
        double[] geographicPositions,
        String warning) {

    public EclipseResult {
        times = copyWithLength(times, "times", 10);
        attributes = copyWithLength(attributes, "attributes", 20);
        geographicPositions = copyWithLength(geographicPositions, "geographicPositions", 10);
        warning = warning == null ? "" : warning;
    }

    @Override
    public double[] times() {
        return times.clone();
    }

    @Override
    public double[] attributes() {
        return attributes.clone();
    }

    @Override
    public double[] geographicPositions() {
        return geographicPositions.clone();
    }

    public boolean has(EclipseFlag flag) {
        Objects.requireNonNull(flag, "flag");
        return (flags & flag.value()) != 0;
    }

    public double time(int index) {
        return times[index];
    }

    public double attribute(int index) {
        return attributes[index];
    }

    /** Longitude of the central line returned by {@code swe_sol_eclipse_where()}. */
    public double longitude() {
        return geographicPositions[0];
    }

    /** Latitude of the central line returned by {@code swe_sol_eclipse_where()}. */
    public double latitude() {
        return geographicPositions[1];
    }

    private static double[] copyWithLength(double[] values, String name, int populatedLength) {
        Objects.requireNonNull(values, name);
        if (values.length != 0 && values.length != populatedLength) {
            throw new IllegalArgumentException(
                    name + " must be empty or contain " + populatedLength + " values");
        }
        return values.clone();
    }
}
