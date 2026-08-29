package org.swisseph.ffm;

import java.util.Objects;

/**
 * What a solar eclipse looks like from one place at one moment, from
 * {@code swe_sol_eclipse_how()}.
 *
 * <p>The native return value is part of the answer, not just an error code: it
 * is {@code 0} when no eclipse is visible there, and otherwise carries the
 * eclipse type and visibility bits. Reporting only the attributes would leave a
 * caller unable to tell "no eclipse" from "an eclipse of magnitude zero".</p>
 */
public record SolarEclipseCircumstances(
        EclipseFlags flags,
        SolarEclipseAttributes attributes,
        String warning) {

    public SolarEclipseCircumstances {
        Objects.requireNonNull(flags, "flags");
        Objects.requireNonNull(attributes, "attributes");
        warning = warning == null ? "" : warning;
    }

    /** Whether any eclipse is under way at this place and time. */
    public boolean isEclipsed() {
        return flags.value() != 0;
    }

    /** Fraction of the solar diameter covered by the Moon. */
    public double magnitude() {
        return attributes.magnitude();
    }

    /** Fraction of the solar disc covered by the Moon. */
    public double obscuration() {
        return attributes.obscuration();
    }
}
