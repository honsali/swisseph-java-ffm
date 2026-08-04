package org.swisseph.ffm;

import java.util.Objects;

/** House cusps and the ten additional points returned by {@code swe_houses_ex()}. */
public record HouseCusps(double[] cusps, double[] additionalPoints) {
    private static final int ADDITIONAL_POINT_COUNT = 10;

    public HouseCusps {
        cusps = Objects.requireNonNull(cusps, "cusps").clone();
        additionalPoints = Objects.requireNonNull(additionalPoints, "additionalPoints").clone();
        if (cusps.length != 13 && cusps.length != 37) {
            throw new IllegalArgumentException("cusps must contain 13 or 37 values");
        }
        if (additionalPoints.length != ADDITIONAL_POINT_COUNT) {
            throw new IllegalArgumentException("additionalPoints must contain 10 values");
        }
    }

    @Override
    public double[] cusps() {
        return cusps.clone();
    }

    @Override
    public double[] additionalPoints() {
        return additionalPoints.clone();
    }

    /** Returns a one-based house cusp (1..12, or 1..36 for Gauquelin sectors). */
    public double cusp(int house) {
        if (house < 1 || house >= cusps.length) {
            throw new IndexOutOfBoundsException("house must be between 1 and " + (cusps.length - 1));
        }
        return cusps[house];
    }

    public double ascendant() {
        return additionalPoints[0];
    }

    public double midheaven() {
        return additionalPoints[1];
    }

    public double armc() {
        return additionalPoints[2];
    }

    public double vertex() {
        return additionalPoints[3];
    }
}
