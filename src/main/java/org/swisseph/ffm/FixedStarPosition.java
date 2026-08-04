package org.swisseph.ffm;

import java.util.Objects;

/** Resolved fixed-star name and position returned by {@code swe_fixstar_ut()}. */
public record FixedStarPosition(String name, EphemerisPosition position) {
    public FixedStarPosition {
        name = Objects.requireNonNull(name, "name");
        position = Objects.requireNonNull(position, "position");
    }
}
