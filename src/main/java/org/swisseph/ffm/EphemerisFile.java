package org.swisseph.ffm;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * A data file Swiss Ephemeris currently has open, as reported by
 * {@code swe_get_current_file_data()}.
 *
 * <p>This is the only reliable way to find out which file a result actually
 * came from: the library searches its ephemeris path, falls back to another
 * ephemeris when a file is missing, and does not otherwise say which one it
 * settled on.</p>
 *
 * @param path                 absolute path of the open file
 * @param startJulianDay       first Julian day the file covers
 * @param endJulianDay         last Julian day the file covers
 * @param jplEphemerisNumber   the JPL DE number the file is based on, for
 *                             example 431; 0 when not applicable
 */
public record EphemerisFile(
        String path,
        double startJulianDay,
        double endJulianDay,
        int jplEphemerisNumber) {

    public EphemerisFile {
        Objects.requireNonNull(path, "path");
    }

    /** Returns whether the file covers the given Julian day. */
    public boolean covers(double julianDay) {
        return julianDay >= startJulianDay && julianDay <= endJulianDay;
    }

    /** The JPL DE number, or empty when the file does not derive from one. */
    public OptionalInt jplNumber() {
        return jplEphemerisNumber == 0 ? OptionalInt.empty() : OptionalInt.of(jplEphemerisNumber);
    }
}
