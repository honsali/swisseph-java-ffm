package org.swisseph.ffm;

/**
 * A decimal degree split into components by {@code swe_split_deg()}.
 *
 * <p>The native {@code isgn} output carries two unrelated meanings depending on
 * the options that were requested, and the two are indistinguishable from the
 * value alone: {@code 1} means "the input was positive" for a plain split and
 * "Taurus" for a zodiacal one. The options are therefore carried alongside, and
 * {@link #signum()}, {@link #zodiacSign()}, and {@link #nakshatra()} each refuse
 * to answer unless the split was actually made in their mode.</p>
 *
 * @param degrees        whole degrees; 0 to 29 with {@link DegreeSplitOption#ZODIACAL}
 * @param minutes        whole arc minutes
 * @param seconds        whole arc seconds
 * @param secondFraction fractional part of the seconds, or the rounded seconds
 *                       when a rounding option was requested
 * @param sign           the raw {@code isgn} output; prefer the guarded accessors
 * @param roundFlags     the {@code roundflag} mask the split was made with
 */
public record DegreeParts(
        int degrees,
        int minutes,
        int seconds,
        double secondFraction,
        int sign,
        int roundFlags) {

    /** Whether the split divided the circle into zodiac signs. */
    public boolean isZodiacal() {
        return (roundFlags & DegreeSplitOption.ZODIACAL.value()) != 0;
    }

    /**
     * Whether the split divided the circle into nakshatras.
     *
     * <p>False for a negative input even when the option was requested: the
     * native code takes the negative branch first and never reaches the
     * nakshatra split.</p>
     */
    public boolean isNakshatra() {
        return (roundFlags & DegreeSplitOption.NAKSHATRA.value()) != 0 && sign >= 0;
    }

    /**
     * The sign of the input, {@code -1} or {@code 1}.
     *
     * @throws IllegalStateException if the split divided the circle instead, in
     *                               which case the sign of the input was not
     *                               retained by the native code
     */
    public int signum() {
        if (isZodiacal() || isNakshatra()) {
            throw new IllegalStateException(
                    "this split used " + (isZodiacal() ? "ZODIACAL" : "NAKSHATRA")
                            + ", so isgn holds a division index rather than a sign; "
                            + "call " + (isZodiacal() ? "zodiacSign()" : "nakshatra()") + " instead");
        }
        return sign;
    }

    /**
     * The zodiac sign index, 0 for Aries through 11 for Pisces.
     *
     * @throws IllegalStateException if the split was not made with
     *                               {@link DegreeSplitOption#ZODIACAL}
     */
    public int zodiacSign() {
        if (!isZodiacal()) {
            throw new IllegalStateException(
                    "isgn holds " + sign + ", which is the sign of the input, not a zodiac "
                            + "index; split with DegreeSplitOption.ZODIACAL to get one");
        }
        return sign;
    }

    /**
     * The nakshatra index, 0 through 26.
     *
     * @throws IllegalStateException if the split was not made with
     *                               {@link DegreeSplitOption#NAKSHATRA}, or if the
     *                               input was negative, which makes the native
     *                               code fall back to a plain split
     */
    public int nakshatra() {
        if (!isNakshatra()) {
            throw new IllegalStateException(
                    "isgn holds " + sign + ", which is not a nakshatra index; split a "
                            + "non-negative angle with DegreeSplitOption.NAKSHATRA to get one");
        }
        return sign;
    }

    /** Formats as {@code 12d34'56"}, ignoring the sign component. */
    public String toDegreeMinuteSecond() {
        return degrees + "d" + minutes + "'" + seconds + "\"";
    }
}
