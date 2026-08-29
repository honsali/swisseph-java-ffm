package org.swisseph.ffm;

import java.util.Objects;

/**
 * What a lunar eclipse looks like from one place at one moment, from
 * {@code swe_lun_eclipse_how()}.
 *
 * <p>The native return value carries the eclipse type and is reported rather
 * than reduced to a success flag. Note that it is also zeroed when the Moon is
 * below the horizon, so it describes local visibility rather than the existence
 * of an eclipse; see {@link #isVisible()}.</p>
 */
public record LunarEclipseCircumstances(
        EclipseFlags flags,
        LunarEclipseAttributes attributes,
        String warning) {

    public LunarEclipseCircumstances {
        Objects.requireNonNull(flags, "flags");
        Objects.requireNonNull(attributes, "attributes");
        warning = warning == null ? "" : warning;
    }

    /**
     * Whether the eclipse is visible from the place that was asked about.
     *
     * <p>Deliberately not called "is there an eclipse": upstream returns zero
     * flags when the Moon is below the horizon, while still filling in the
     * magnitudes. A total eclipse happening below the horizon therefore reports
     * {@code false} here and an {@link #umbralMagnitude()} above 1, and reading
     * this as "no eclipse" would be wrong. Use the magnitudes to ask whether an
     * eclipse is under way at all.</p>
     */
    public boolean isVisible() {
        return flags.value() != 0;
    }

    /** Umbral magnitude; above 1 for a total eclipse. */
    public double umbralMagnitude() {
        return attributes.umbralMagnitude();
    }
}
