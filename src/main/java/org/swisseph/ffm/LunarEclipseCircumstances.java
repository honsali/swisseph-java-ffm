package org.swisseph.ffm;

import java.util.Objects;

/**
 * What a lunar eclipse looks like from one place at one moment, from
 * {@code swe_lun_eclipse_how()}.
 *
 * <p>As with the solar form, the native return value carries the eclipse type
 * and is {@code 0} when there is no eclipse, so it is reported rather than
 * reduced to a success flag.</p>
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

    /** Whether any eclipse is under way at this time. */
    public boolean isEclipsed() {
        return flags.value() != 0;
    }

    /** Umbral magnitude; above 1 for a total eclipse. */
    public double umbralMagnitude() {
        return attributes.umbralMagnitude();
    }
}
