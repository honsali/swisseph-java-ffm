package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/** Eclipse type, centrality, and visibility bits from Swiss Ephemeris. */
public enum EclipseFlag {
    CENTRAL(1),
    NON_CENTRAL(2),
    TOTAL(4),
    ANNULAR(8),
    PARTIAL(16),
    ANNULAR_TOTAL(32),
    PENUMBRAL(64),
    VISIBLE(128),
    MAXIMUM_VISIBLE(256),
    FIRST_CONTACT_VISIBLE(512),
    SECOND_CONTACT_VISIBLE(1_024),
    THIRD_CONTACT_VISIBLE(2_048),
    FOURTH_CONTACT_VISIBLE(4_096),
    PENUMBRAL_BEGIN_VISIBLE(8_192),
    PENUMBRAL_END_VISIBLE(16_384);

    private final int mask;

    EclipseFlag(int mask) {
        this.mask = mask;
    }

    public int value() {
        return mask;
    }

    public static int mask(EclipseFlag... flags) {
        Objects.requireNonNull(flags, "flags");
        return mask(Arrays.asList(flags));
    }

    public static int mask(Collection<EclipseFlag> flags) {
        Objects.requireNonNull(flags, "flags");
        int result = 0;
        for (EclipseFlag flag : flags) {
            result |= Objects.requireNonNull(flag, "flag").mask;
        }
        return result;
    }
}
