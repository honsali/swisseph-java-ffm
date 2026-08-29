package org.swisseph.ffm;

import java.util.Optional;

/**
 * A snapshot of the settings currently applied to a native context.
 *
 * <p>Swiss Ephemeris offers no way to read its configuration back, so this
 * record tracks what was pushed into it. It matters because the settings are
 * shared: every {@link SwissEph} handle opened against the same library sees
 * the same ephemeris path, JPL file, observer, and sidereal mode. Comparing
 * this snapshot is how a caller checks whether another handle has changed the
 * configuration underneath it.</p>
 *
 * @param ephemerisPath       argument of the last {@code swe_set_ephe_path()} call
 * @param jplFile             argument of the last {@code swe_set_jpl_file()} call
 * @param topocentricObserver argument of the last {@code swe_set_topo()} call
 * @param siderealMode        first argument of the last {@code swe_set_sid_mode()} call
 * @param siderealT0          reference epoch passed with a user-defined ayanamsha
 * @param siderealAyanamsaAtT0 ayanamsha value passed with a user-defined ayanamsha
 */
public record SwissEphSettings(
        String ephemerisPath,
        String jplFile,
        GeographicPosition topocentricObserver,
        Integer siderealMode,
        double siderealT0,
        double siderealAyanamsaAtT0) {

    /** Nothing configured yet: the library is using its compiled-in defaults. */
    public static final SwissEphSettings EMPTY =
            new SwissEphSettings(null, null, null, null, 0.0, 0.0);

    public Optional<String> ephemerisPathIfSet() {
        return Optional.ofNullable(ephemerisPath);
    }

    public Optional<String> jplFileIfSet() {
        return Optional.ofNullable(jplFile);
    }

    public Optional<GeographicPosition> topocentricObserverIfSet() {
        return Optional.ofNullable(topocentricObserver);
    }

    /** The raw {@code sid_mode} argument, option bits included. */
    public Optional<Integer> siderealModeIfSet() {
        return Optional.ofNullable(siderealMode);
    }

    /** The predefined ayanamsha, with the {@code SE_SIDBIT_*} bits stripped. */
    public Optional<SiderealMode> siderealAyanamsa() {
        return siderealModeIfSet().flatMap(mode -> SiderealMode.of(mode & 0xFF));
    }

    SwissEphSettings withEphemerisPath(String path) {
        return new SwissEphSettings(path, jplFile, topocentricObserver, siderealMode,
                siderealT0, siderealAyanamsaAtT0);
    }

    SwissEphSettings withJplFile(String file) {
        return new SwissEphSettings(ephemerisPath, file, topocentricObserver, siderealMode,
                siderealT0, siderealAyanamsaAtT0);
    }

    SwissEphSettings withTopocentricObserver(GeographicPosition observer) {
        return new SwissEphSettings(ephemerisPath, jplFile, observer, siderealMode,
                siderealT0, siderealAyanamsaAtT0);
    }

    SwissEphSettings withSiderealMode(int mode, double t0, double ayanamsaAtT0) {
        return new SwissEphSettings(ephemerisPath, jplFile, topocentricObserver, mode,
                t0, ayanamsaAtT0);
    }
}
