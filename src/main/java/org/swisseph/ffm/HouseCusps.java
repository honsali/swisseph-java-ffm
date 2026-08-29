package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Objects;

/**
 * House cusps and the ten additional points returned by {@code swe_houses_ex()}
 * or {@code swe_houses_armc()}.
 *
 * <p>The cusp array is one-based, as in C: {@code cusp(1)} is the first house.</p>
 *
 * <p>{@link #requestedSystemUsed()} reports whether the library honoured the
 * requested system. Swiss Ephemeris returns an error code, yet still fills the
 * cusps from a substitute system, when the request cannot be satisfied. The
 * best known case is Placidus beyond the polar circles, where it silently falls
 * back to Porphyry. Throwing away those cusps would be wrong, and returning
 * them as if nothing happened would be worse, so both the numbers and the fact
 * of the substitution are reported.</p>
 *
 * <p>{@code equals}, {@code hashCode}, and {@code toString} are written by hand
 * because the record defaults would compare the {@code double[]} components by
 * identity, making two results with the same numbers unequal.</p>
 */
public record HouseCusps(
        double[] cusps,
        double[] additionalPoints,
        HouseSystem requestedSystem,
        boolean requestedSystemUsed) {

    private static final int ADDITIONAL_POINT_COUNT = 10;

    public HouseCusps {
        Objects.requireNonNull(requestedSystem, "requestedSystem");
        cusps = Objects.requireNonNull(cusps, "cusps").clone();
        additionalPoints = Objects.requireNonNull(additionalPoints, "additionalPoints").clone();
        if (cusps.length != 13 && cusps.length != 37) {
            throw new IllegalArgumentException(
                    "cusps must contain 13 values, or 37 for Gauquelin sectors, but had "
                            + cusps.length);
        }
        if (additionalPoints.length != ADDITIONAL_POINT_COUNT) {
            throw new IllegalArgumentException("additionalPoints must contain "
                    + ADDITIONAL_POINT_COUNT + " values, but had " + additionalPoints.length);
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

    /** Returns a one-based cusp: 1 to 12, or 1 to 36 for Gauquelin sectors. */
    public double cusp(int house) {
        if (house < 1 || house >= cusps.length) {
            throw new IndexOutOfBoundsException(
                    "house must be between 1 and " + (cusps.length - 1) + ", but was " + house);
        }
        return cusps[house];
    }

    /** Number of houses or sectors in this result. */
    public int houseCount() {
        return cusps.length - 1;
    }

    /** {@code SE_ASC}. */
    public double ascendant() {
        return additionalPoints[0];
    }

    /** {@code SE_MC}. */
    public double midheaven() {
        return additionalPoints[1];
    }

    /** {@code SE_ARMC}: right ascension of the midheaven, in degrees. */
    public double armc() {
        return additionalPoints[2];
    }

    /** {@code SE_VERTEX}. */
    public double vertex() {
        return additionalPoints[3];
    }

    /** {@code SE_EQUASC}: the equatorial ascendant. */
    public double equatorialAscendant() {
        return additionalPoints[4];
    }

    /** {@code SE_COASC1}: co-ascendant after W. Koch. */
    public double coAscendantKoch() {
        return additionalPoints[5];
    }

    /** {@code SE_COASC2}: co-ascendant after M. Munkasey. */
    public double coAscendantMunkasey() {
        return additionalPoints[6];
    }

    /** {@code SE_POLASC}: polar ascendant after M. Munkasey. */
    public double polarAscendant() {
        return additionalPoints[7];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof HouseCusps that
                && requestedSystem == that.requestedSystem
                && requestedSystemUsed == that.requestedSystemUsed
                && Arrays.equals(cusps, that.cusps)
                && Arrays.equals(additionalPoints, that.additionalPoints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestedSystem, requestedSystemUsed,
                Arrays.hashCode(cusps), Arrays.hashCode(additionalPoints));
    }

    @Override
    public String toString() {
        return "HouseCusps[system=" + requestedSystem
                + (requestedSystemUsed ? "" : " (substituted)")
                + ", ascendant=" + ascendant() + ", midheaven=" + midheaven()
                + ", cusps=" + Arrays.toString(cusps)
                + ", additionalPoints=" + Arrays.toString(additionalPoints) + "]";
    }
}
