package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Objects;

/**
 * The {@code attr[]} block returned by {@code swe_lun_eclipse_how()} and
 * {@code swe_lun_eclipse_when_loc()}.
 *
 * <p>The indices differ from the solar ones, which is why this is a separate
 * type: reading a lunar result through the solar accessors would return the
 * right numbers under the wrong names.</p>
 */
public record LunarEclipseAttributes(double[] values) {
    private static final int COUNT = 20;

    public LunarEclipseAttributes {
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

    /** {@code attr[0]}: umbral magnitude. */
    public double umbralMagnitude() {
        return values[0];
    }

    /** {@code attr[1]}: penumbral magnitude. */
    public double penumbralMagnitude() {
        return values[1];
    }

    /** {@code attr[4]}: azimuth of the Moon, degrees from south turning westwards. */
    public double moonAzimuth() {
        return values[4];
    }

    /** {@code attr[5]}: true altitude of the Moon above the horizon. */
    public double moonTrueAltitude() {
        return values[5];
    }

    /** {@code attr[6]}: apparent altitude of the Moon above the horizon. */
    public double moonApparentAltitude() {
        return values[6];
    }

    /** {@code attr[7]}: distance of the Moon from exact opposition, in degrees. */
    public double oppositionDistance() {
        return values[7];
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
        return other instanceof LunarEclipseAttributes that && Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "LunarEclipseAttributes[umbralMagnitude=" + umbralMagnitude()
                + ", penumbralMagnitude=" + penumbralMagnitude()
                + ", values=" + Arrays.toString(values) + "]";
    }
}
