package org.swisseph.ffm;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

/**
 * The {@code iflag} value Swiss Ephemeris returns from a successful calculation.
 *
 * <p>This is not the mask that was requested. When the data files for the
 * requested ephemeris are missing, the library falls back to another one and
 * says so here, so checking {@link #ephemeris()} against what was asked for is
 * the only way to notice a silent downgrade.</p>
 */
public record ReturnedFlags(int value) {

    public boolean has(CalculationFlag flag) {
        Objects.requireNonNull(flag, "flag");
        return (value & flag.value()) != 0;
    }

    /** Every {@link CalculationFlag} whose bit is set. */
    public Set<CalculationFlag> toSet() {
        EnumSet<CalculationFlag> result = EnumSet.noneOf(CalculationFlag.class);
        for (CalculationFlag flag : CalculationFlag.values()) {
            if (has(flag)) {
                result.add(flag);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /** The ephemeris actually used, or empty if the library reported none. */
    public Optional<Ephemeris> ephemeris() {
        for (Ephemeris ephemeris : Ephemeris.values()) {
            if (has(ephemeris.flag())) {
                return Optional.of(ephemeris);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns whether the calculation used the requested ephemeris.
     *
     * @param requested the ephemeris the caller asked for
     */
    public boolean used(Ephemeris requested) {
        Objects.requireNonNull(requested, "requested");
        return has(requested.flag());
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "ReturnedFlags[0x"
                + Integer.toHexString(value) + ": ", "]");
        joiner.setEmptyValue("ReturnedFlags[0x" + Integer.toHexString(value) + "]");
        for (CalculationFlag flag : toSet()) {
            joiner.add(flag.name());
        }
        return joiner.toString();
    }
}
