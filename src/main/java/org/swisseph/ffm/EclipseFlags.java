package org.swisseph.ffm;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The {@code int32} an eclipse function returns, split into the two families of
 * bits it actually carries.
 */
public record EclipseFlags(int value) {

    public boolean has(EclipseType type) {
        Objects.requireNonNull(type, "type");
        return (value & type.value()) != 0;
    }

    public boolean has(EclipseVisibility visibility) {
        Objects.requireNonNull(visibility, "visibility");
        return (value & visibility.value()) != 0;
    }

    /** The kinds of eclipse reported, for example {@code TOTAL} plus {@code CENTRAL}. */
    public Set<EclipseType> types() {
        EnumSet<EclipseType> result = EnumSet.noneOf(EclipseType.class);
        for (EclipseType type : EclipseType.values()) {
            if (has(type)) {
                result.add(type);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * The visibility bits reported.
     *
     * <p>Only meaningful for the local eclipse functions: the global searches
     * leave these bits clear.</p>
     */
    public Set<EclipseVisibility> visibility() {
        EnumSet<EclipseVisibility> result = EnumSet.noneOf(EclipseVisibility.class);
        for (EclipseVisibility bit : EclipseVisibility.values()) {
            if (has(bit)) {
                result.add(bit);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public String toString() {
        return "EclipseFlags[0x" + Integer.toHexString(value)
                + ", types=" + types() + ", visibility=" + visibility() + "]";
    }
}
