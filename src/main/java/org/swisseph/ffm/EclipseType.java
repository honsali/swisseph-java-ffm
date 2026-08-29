package org.swisseph.ffm;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Eclipse kinds, used both to restrict a search ({@code ifltype}) and to
 * describe what was found.
 *
 * <p>Kept separate from {@link EclipseVisibility}, which carries the
 * observer-dependent bits that only ever come back <em>out</em> of the
 * library.</p>
 */
public enum EclipseType {
    /** {@code SE_ECL_CENTRAL}. */
    CENTRAL(1),
    /** {@code SE_ECL_NONCENTRAL}. */
    NON_CENTRAL(2),
    /** {@code SE_ECL_TOTAL}. */
    TOTAL(4),
    /** {@code SE_ECL_ANNULAR}. */
    ANNULAR(8),
    /** {@code SE_ECL_PARTIAL}. */
    PARTIAL(16),
    /** {@code SE_ECL_ANNULAR_TOTAL}, also known as a hybrid eclipse. */
    ANNULAR_TOTAL(32),
    /** {@code SE_ECL_PENUMBRAL}, lunar eclipses only. */
    PENUMBRAL(64);

    private final int mask;

    EclipseType(int mask) {
        this.mask = mask;
    }

    public int value() {
        return mask;
    }

    /** Whether this kind can describe a solar eclipse. */
    public boolean isSolar() {
        return this != PENUMBRAL;
    }

    /** Whether this kind can describe a lunar eclipse. */
    public boolean isLunar() {
        return this == TOTAL || this == PARTIAL || this == PENUMBRAL;
    }

    /** {@code SE_ECL_ALLTYPES_SOLAR}. */
    public static Set<EclipseType> allSolar() {
        return EnumSet.of(CENTRAL, NON_CENTRAL, TOTAL, ANNULAR, PARTIAL, ANNULAR_TOTAL);
    }

    /** {@code SE_ECL_ALLTYPES_LUNAR}. */
    public static Set<EclipseType> allLunar() {
        return EnumSet.of(TOTAL, PARTIAL, PENUMBRAL);
    }

    public static int mask(EclipseType... types) {
        Objects.requireNonNull(types, "types");
        return mask(Arrays.asList(types));
    }

    public static int mask(Collection<EclipseType> types) {
        Objects.requireNonNull(types, "types");
        int result = 0;
        for (EclipseType type : types) {
            result |= Objects.requireNonNull(type, "type").value();
        }
        return result;
    }
}
