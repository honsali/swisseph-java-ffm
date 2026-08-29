package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/** Rounding and formatting bits accepted by {@code swe_split_deg()}. */
public enum DegreeSplitOption {
    /** {@code SE_SPLIT_DEG_ROUND_SEC}. */
    ROUND_SECONDS(1),
    /** {@code SE_SPLIT_DEG_ROUND_MIN}. */
    ROUND_MINUTES(2),
    /** {@code SE_SPLIT_DEG_ROUND_DEG}. */
    ROUND_DEGREES(4),
    /** {@code SE_SPLIT_DEG_ZODIACAL}: split into a zodiac sign plus 0..29 degrees. */
    ZODIACAL(8),
    /** {@code SE_SPLIT_DEG_KEEP_SIGN}: never let rounding carry into the next sign. */
    KEEP_SIGN(16),
    /** {@code SE_SPLIT_DEG_KEEP_DEG}: never let rounding carry into the next degree. */
    KEEP_DEGREE(32),
    /** {@code SE_SPLIT_DEG_NAKSHATRA}: split into a nakshatra plus 0..13 degrees. */
    NAKSHATRA(1_024);

    private final int mask;

    DegreeSplitOption(int mask) {
        this.mask = mask;
    }

    public int value() {
        return mask;
    }

    public static int mask(DegreeSplitOption... options) {
        Objects.requireNonNull(options, "options");
        return mask(Arrays.asList(options));
    }

    public static int mask(Collection<DegreeSplitOption> options) {
        Objects.requireNonNull(options, "options");
        int result = 0;
        for (DegreeSplitOption option : options) {
            result |= Objects.requireNonNull(option, "option").value();
        }
        return result;
    }
}
