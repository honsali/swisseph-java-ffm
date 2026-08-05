package org.swisseph.ffm;

import java.util.Objects;

/** The 20 attributes returned by {@code swe_pheno_ut()}. */
public record PlanetaryPhenomena(double[] attributes, String warning) {
    private static final int ATTRIBUTE_COUNT = 20;

    public PlanetaryPhenomena {
        attributes = Objects.requireNonNull(attributes, "attributes").clone();
        if (attributes.length != ATTRIBUTE_COUNT) {
            throw new IllegalArgumentException("attributes must contain 20 values");
        }
        warning = warning == null ? "" : warning;
    }

    @Override
    public double[] attributes() {
        return attributes.clone();
    }

    /** Phase angle in degrees. */
    public double phaseAngle() {
        return attributes[0];
    }

    /** Illuminated fraction of the body's disc. */
    public double illuminatedFraction() {
        return attributes[1];
    }

    /** Elongation from the Sun in degrees. */
    public double elongation() {
        return attributes[2];
    }

    /** Apparent diameter in degrees. */
    public double apparentDiameter() {
        return attributes[3];
    }

    /** Apparent visual magnitude. */
    public double apparentMagnitude() {
        return attributes[4];
    }

    /** Returns an attribute by its native {@code attr[]} index. */
    public double attribute(int index) {
        return attributes[index];
    }
}
