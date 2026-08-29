package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Objects;

/**
 * The attributes returned by {@code swe_pheno()} and {@code swe_pheno_ut()}.
 *
 * <p>{@code equals}, {@code hashCode}, and {@code toString} are written by hand
 * so that the {@code double[]} component compares by value.</p>
 */
public record PlanetaryPhenomena(double[] attributes, String warning) {
    private static final int ATTRIBUTE_COUNT = 20;

    public PlanetaryPhenomena {
        attributes = Objects.requireNonNull(attributes, "attributes").clone();
        if (attributes.length != ATTRIBUTE_COUNT) {
            throw new IllegalArgumentException("attributes must contain " + ATTRIBUTE_COUNT
                    + " values, but had " + attributes.length);
        }
        warning = warning == null ? "" : warning;
    }

    @Override
    public double[] attributes() {
        return attributes.clone();
    }

    /** {@code attr[0]}: phase angle in degrees, that is the Sun-body-Earth angle. */
    public double phaseAngle() {
        return attributes[0];
    }

    /** {@code attr[1]}: illuminated fraction of the disc, from 0 to 1. */
    public double illuminatedFraction() {
        return attributes[1];
    }

    /** {@code attr[2]}: elongation from the Sun in degrees. */
    public double elongation() {
        return attributes[2];
    }

    /** {@code attr[3]}: apparent diameter of the disc in degrees. */
    public double apparentDiameter() {
        return attributes[3];
    }

    /** {@code attr[4]}: apparent visual magnitude. */
    public double apparentMagnitude() {
        return attributes[4];
    }

    /** Returns an attribute by its native {@code attr[]} index. */
    public double attribute(int index) {
        return attributes[Objects.checkIndex(index, ATTRIBUTE_COUNT)];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlanetaryPhenomena that
                && Arrays.equals(attributes, that.attributes)
                && warning.equals(that.warning);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(attributes) + warning.hashCode();
    }

    @Override
    public String toString() {
        return "PlanetaryPhenomena[phaseAngle=" + phaseAngle()
                + ", illuminatedFraction=" + illuminatedFraction()
                + ", elongation=" + elongation()
                + ", apparentDiameter=" + apparentDiameter()
                + ", apparentMagnitude=" + apparentMagnitude()
                + ", attributes=" + Arrays.toString(attributes)
                + ", warning=" + warning + "]";
    }
}
