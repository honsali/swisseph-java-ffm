package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Objects;

/**
 * The {@code attr[]} block shared by {@code swe_sol_eclipse_how()},
 * {@code swe_sol_eclipse_where()}, and {@code swe_sol_eclipse_when_loc()}.
 */
public record SolarEclipseAttributes(double[] values) {
    private static final int COUNT = 20;

    public SolarEclipseAttributes {
        values = Objects.requireNonNull(values, "values").clone();
        if (values.length != COUNT) {
            throw new IllegalArgumentException(
                    "values must contain " + COUNT + " attributes, but had " + values.length);
        }
    }

    @Override
    public double[] values() {
        return values.clone();
    }

    /** {@code attr[0]}: fraction of the solar diameter covered by the Moon, the magnitude. */
    public double magnitude() {
        return values[0];
    }

    /** {@code attr[1]}: ratio of the lunar diameter to the solar one. */
    public double lunarSolarDiameterRatio() {
        return values[1];
    }

    /** {@code attr[2]}: fraction of the solar disc covered, the obscuration. */
    public double obscuration() {
        return values[2];
    }

    /** {@code attr[3]}: diameter of the core shadow in kilometres. */
    public double coreShadowDiameterKm() {
        return values[3];
    }

    /** {@code attr[4]}: azimuth of the Sun, degrees from south turning westwards. */
    public double sunAzimuth() {
        return values[4];
    }

    /** {@code attr[5]}: true altitude of the Sun above the horizon, refraction excluded. */
    public double sunTrueAltitude() {
        return values[5];
    }

    /** {@code attr[6]}: apparent altitude of the Sun above the horizon. */
    public double sunApparentAltitude() {
        return values[6];
    }

    /** {@code attr[7]}: elongation of the Moon in degrees. */
    public double moonElongation() {
        return values[7];
    }

    /** {@code attr[8]}: magnitude as defined by NASA, the ratio of the diameters. */
    public double nasaMagnitude() {
        return values[8];
    }

    /** {@code attr[9]}: Saros series number, or {@code -99999999} when unknown. */
    public double sarosSeriesNumber() {
        return values[9];
    }

    /** {@code attr[10]}: member number within the Saros series. */
    public double sarosSeriesMember() {
        return values[10];
    }

    /** Returns an attribute by its native {@code attr[]} index. */
    public double value(int index) {
        return values[Objects.checkIndex(index, COUNT)];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SolarEclipseAttributes that && Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "SolarEclipseAttributes[magnitude=" + magnitude()
                + ", obscuration=" + obscuration()
                + ", sunApparentAltitude=" + sunApparentAltitude()
                + ", values=" + Arrays.toString(values) + "]";
    }
}
