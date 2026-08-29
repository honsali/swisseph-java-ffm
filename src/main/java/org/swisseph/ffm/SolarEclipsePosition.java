package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Objects;

/**
 * Where a solar eclipse falls on Earth at a given moment, from
 * {@code swe_sol_eclipse_where()}.
 *
 * <p>Limits that do not exist at this moment come back as
 * {@link GeographicPoint#UNDEFINED}; test with
 * {@link GeographicPoint#isDefined()} before using one.</p>
 */
public record SolarEclipsePosition(
        EclipseFlags flags,
        double[] geographicPositions,
        SolarEclipseAttributes attributes,
        String warning) {

    private static final int POSITION_COUNT = 10;

    public SolarEclipsePosition {
        Objects.requireNonNull(flags, "flags");
        Objects.requireNonNull(attributes, "attributes");
        geographicPositions = Objects.requireNonNull(geographicPositions, "geographicPositions").clone();
        if (geographicPositions.length != POSITION_COUNT) {
            throw new IllegalArgumentException("geographicPositions must contain " + POSITION_COUNT
                    + " values, but had " + geographicPositions.length);
        }
        warning = warning == null ? "" : warning;
    }

    @Override
    public double[] geographicPositions() {
        return geographicPositions.clone();
    }

    /** {@code geopos[0..1]}: where the central line meets the Earth. */
    public GeographicPoint centralLine() {
        return point(0);
    }

    /** {@code geopos[2..3]}: northern limit of the umbra. */
    public GeographicPoint northernUmbraLimit() {
        return point(2);
    }

    /** {@code geopos[4..5]}: southern limit of the umbra. */
    public GeographicPoint southernUmbraLimit() {
        return point(4);
    }

    /** {@code geopos[6..7]}: northern limit of the penumbra. */
    public GeographicPoint northernPenumbraLimit() {
        return point(6);
    }

    /** {@code geopos[8..9]}: southern limit of the penumbra. */
    public GeographicPoint southernPenumbraLimit() {
        return point(8);
    }

    private GeographicPoint point(int index) {
        return new GeographicPoint(geographicPositions[index], geographicPositions[index + 1]);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SolarEclipsePosition that
                && flags.equals(that.flags)
                && Arrays.equals(geographicPositions, that.geographicPositions)
                && attributes.equals(that.attributes)
                && warning.equals(that.warning);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flags, Arrays.hashCode(geographicPositions), attributes, warning);
    }

    @Override
    public String toString() {
        return "SolarEclipsePosition[flags=" + flags + ", centralLine=" + centralLine()
                + ", attributes=" + attributes + ", warning=" + warning + "]";
    }
}
