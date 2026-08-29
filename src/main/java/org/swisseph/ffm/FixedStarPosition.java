package org.swisseph.ffm;

import java.util.Objects;

/**
 * A fixed-star position, together with the name the library resolved.
 *
 * <p>Swiss Ephemeris rewrites the star buffer with its canonical
 * {@code traditional name,nomenclature name} form, so {@link #name()} is often
 * richer than the string that was passed in.</p>
 */
public record FixedStarPosition(String name, EphemerisPosition position) {
    public FixedStarPosition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(position, "position");
    }

    /** The traditional name, that is the part before the comma. */
    public String traditionalName() {
        int comma = name.indexOf(',');
        return comma < 0 ? name : name.substring(0, comma);
    }

    /** The nomenclature name, that is the part after the comma, or empty. */
    public String nomenclatureName() {
        int comma = name.indexOf(',');
        return comma < 0 ? "" : name.substring(comma + 1);
    }
}
