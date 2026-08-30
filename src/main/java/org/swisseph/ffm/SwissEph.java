package org.swisseph.ffm;

import org.swisseph.ffm.internal.NativeBindings;
import org.swisseph.ffm.internal.NativeContext;
import org.swisseph.ffm.internal.NativeStrings;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * A handle on a loaded Swiss Ephemeris library.
 *
 * <h2>Execution model</h2>
 * <p>Swiss Ephemeris keeps its entire state in {@code TLS struct swe_data swed},
 * which is thread-local on GCC, Clang, and MSVC builds and process-global on
 * builds compiled without thread-local support. Every call made through this
 * class therefore runs on one dedicated platform thread owned by the library
 * context, so configuration and calculation always see the same {@code swed}
 * whichever way the native library was built. Calls are consequently
 * serialized; this class is safe to use from any number of Java threads.</p>
 *
 * <h2>Lifecycle</h2>
 * <p>Handles opened against the same library file share one native context and
 * are reference counted. Closing a handle releases that handle: only when the
 * last one is closed does {@code swe_close()} run and the library unload. One
 * component closing its handle can no longer break another component that is
 * still working.</p>
 *
 * <p>Native <em>settings</em> are still shared, because the C library has only
 * one set of them. Changing the ephemeris path, the JPL file, the observer, or
 * the sidereal mode affects every handle on that library. {@link #settings()}
 * reports what is currently applied so this can be detected rather than
 * guessed.</p>
 *
 * <pre>{@code
 * try (SwissEph swe = SwissEph.open(SwissEphConfig.builder()
 *         .library(Path.of("/opt/swisseph/libswe.so"))
 *         .ephemerisPath(Path.of("/opt/swisseph/ephe"))
 *         .build())) {
 *     double jd = swe.julianDay(2026, 8, 4, 12.0, CalendarType.GREGORIAN);
 *     EphemerisPosition sun = swe.calculateUt(jd, CelestialBody.SUN,
 *             CalculationFlag.SWISS_EPHEMERIS, CalculationFlag.SPEED);
 *     System.out.println(sun.longitude());
 * }
 * }</pre>
 */
public final class SwissEph implements AutoCloseable {
    /**
     * Longest ephemeris path Swiss Ephemeris accepts.
     *
     * <p>{@code swe_set_ephe_path()} compares against {@code AS_MAXCH - 1 - 13}
     * and <em>silently substitutes its compiled-in default</em> for anything
     * longer, so an over-long path is rejected here rather than allowed to
     * produce results from an unexpected directory.</p>
     */
    public static final int MAX_EPHEMERIS_PATH_BYTES = NativeBindings.AS_MAXCH - 1 - 13;

    private static final int POSITION_VALUE_COUNT = 6;
    private static final int HOUSE_ADDITIONAL_POINT_COUNT = 10;
    private static final int ECLIPSE_TIME_COUNT = 10;
    private static final int ECLIPSE_ATTRIBUTE_COUNT = 20;
    private static final int ECLIPSE_GEOGRAPHIC_POSITION_COUNT = 10;
    private static final int PHENOMENA_ATTRIBUTE_COUNT = 20;
    /**
     * {@code swe_rise_trans()} documents a single return value. The buffer is
     * larger so that a future upstream change cannot turn into a stack write
     * past the end of our allocation.
     */
    private static final int RISE_TRANSIT_VALUE_COUNT = 10;
    /**
     * The value {@code swe_houses_armc()} reads in {@code ascmc[9]} as "I do not
     * know the Sun's declination". Anything else, zero included, is taken as a
     * real declination.
     */
    private static final double SOLAR_DECLINATION_UNKNOWN = 99.0;

    private final NativeContext context;
    private final SwissEphConfig config;
    private final AtomicBoolean closed = new AtomicBoolean();

    private SwissEph(NativeContext context, SwissEphConfig config) {
        this.context = context;
        this.config = config;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Opens a handle described by {@code config}, applying its settings before
     * returning.
     *
     * @throws SwissEphException      if the library cannot be loaded or linked
     * @throws IllegalStateException  if the library reports a version outside
     *                                {@link SwissEphConfig#supportedVersions()}
     *                                and the policy is {@link NativeVersionPolicy#REJECT}
     */
    public static SwissEph open(SwissEphConfig config) {
        Objects.requireNonNull(config, "config");
        NativeContext context = NativeContext.acquire(
                config.libraryPath(), version -> checkVersion(config, version));
        SwissEph handle = new SwissEph(context, config);
        try {
            handle.applyConfiguredSettings();
        } catch (RuntimeException | Error failure) {
            handle.close();
            throw failure;
        }
        return handle;
    }

    /** Opens a handle on an explicit shared-library file. */
    public static SwissEph open(Path libraryPath) {
        return open(SwissEphConfig.of(libraryPath));
    }

    /**
     * Opens a handle on the library found by {@link NativeLibraryLocator}.
     *
     * @throws IllegalStateException if no library is found, with a message
     *                               listing every candidate that was tried
     */
    public static SwissEph open() {
        return open(SwissEphConfig.fromEnvironment());
    }

    private static void checkVersion(SwissEphConfig config, String version) {
        if (config.versionPolicy() == NativeVersionPolicy.ACCEPT
                || config.supportedVersions().contains(version)) {
            return;
        }
        String message = "Swiss Ephemeris native library reports version " + version
                + ", which this binding does not support " + config.supportedVersions()
                + ". The function descriptors are written against those versions; running "
                + "against another build risks reading the wrong memory rather than failing "
                + "cleanly. Override with SwissEphConfig.Builder.supportedVersions(...) or "
                + "versionPolicy(...) if the build is known to be compatible.";
        if (config.versionPolicy() == NativeVersionPolicy.REJECT) {
            throw new IllegalStateException(message);
        }
        System.getLogger(SwissEph.class.getName()).log(System.Logger.Level.WARNING, message);
    }

    /**
     * Pushes the whole configuration in one native task.
     *
     * <p>One task, not four: the settings are process-wide, so applying them as
     * separate units of work would let a concurrent open or calculation observe
     * a hybrid state -- the ephemeris path of one configuration with the
     * sidereal mode of another.</p>
     */
    private void applyConfiguredSettings() {
        if (config.ephemerisPath().isEmpty() && config.jplFile().isEmpty()
                && config.topocentricObserver().isEmpty() && config.siderealMode().isEmpty()) {
            return;
        }
        config.ephemerisPath().ifPresent(path ->
                NativeStrings.requireNativeSafe(path, "ephemerisPath", MAX_EPHEMERIS_PATH_BYTES + 1));
        config.jplFile().ifPresent(file ->
                NativeStrings.requireNativeSafe(file, "jplFile", NativeBindings.AS_MAXCH));
        call(bindings -> {
            SwissEphSettings applied = context.settings();
            try (Arena arena = Arena.ofConfined()) {
                if (config.ephemerisPath().isPresent()) {
                    String path = config.ephemerisPath().orElseThrow();
                    bindings.setEphemerisPath(arena.allocateFrom(path));
                    applied = applied.withEphemerisPath(path);
                }
                if (config.jplFile().isPresent()) {
                    String file = config.jplFile().orElseThrow();
                    bindings.setJplFile(arena.allocateFrom(file));
                    applied = applied.withJplFile(file);
                }
            }
            if (config.topocentricObserver().isPresent()) {
                GeographicPosition observer = config.topocentricObserver().orElseThrow();
                bindings.setTopocentricPosition(
                        observer.longitude(), observer.latitude(), observer.altitudeMeters());
                applied = applied.withTopocentricObserver(observer);
            }
            if (config.siderealMode().isPresent()) {
                int mode = config.siderealMode().orElseThrow();
                bindings.setSiderealMode(
                        mode, config.siderealT0(), config.siderealAyanamsaAtT0());
                applied = applied.withSiderealMode(
                        mode, config.siderealT0(), config.siderealAyanamsaAtT0());
            }
            context.settings(applied);
            return null;
        });
    }

    /**
     * Releases this handle. Idempotent.
     *
     * <p>{@code swe_close()} runs and the library unloads only when this was the
     * last handle on it.</p>
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            context.release();
        }
    }

    /** Whether this handle is still usable. */
    public boolean isOpen() {
        return !closed.get() && context.isOpen();
    }

    /** The configuration this handle was opened with. */
    public SwissEphConfig config() {
        return config;
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    /** The value of {@code swe_version()}, read once when the library was loaded. */
    public String version() {
        ensureOpen();
        return context.nativeVersion();
    }

    /** The shared-library file this handle was opened from. */
    public Path libraryPath() {
        return context.libraryPath();
    }

    /** The path {@code swe_get_library_path()} reports for the loaded binary. */
    public String nativeLibraryPath() {
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment buffer = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                bindings.libraryPath(buffer);
                return NativeStrings.readBuffer(buffer);
            }
        });
    }

    /**
     * What is currently configured on the shared native context.
     *
     * <p>Any handle on the same library can have changed this, so a component
     * that cares should compare before and after rather than assume.</p>
     */
    public SwissEphSettings settings() {
        return context.settings();
    }

    /** How many live handles share this library. */
    public int handleCount() {
        return context.referenceCount();
    }

    /**
     * The data file currently open in {@code slot}, if any.
     *
     * <p>This is the only reliable way to learn which file a result came from.
     * Swiss Ephemeris searches its ephemeris path, falls back to another
     * ephemeris when a file is missing, and reports the substitution only
     * through the returned flags. A slot stays empty until a calculation has
     * actually needed it.</p>
     */
    public Optional<EphemerisFile> currentFile(EphemerisFileSlot slot) {
        Objects.requireNonNull(slot, "slot");
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment start = arena.allocate(JAVA_DOUBLE);
                MemorySegment end = arena.allocate(JAVA_DOUBLE);
                MemorySegment jplNumber = arena.allocate(JAVA_INT);
                MemorySegment pointer = bindings.currentFileData(
                        slot.value(), start, end, jplNumber);
                String path = NativeStrings.read(pointer, NativeBindings.AS_MAXCH);
                if (path == null || path.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(new EphemerisFile(
                        path,
                        start.get(JAVA_DOUBLE, 0),
                        end.get(JAVA_DOUBLE, 0),
                        jplNumber.get(JAVA_INT, 0)));
            }
        });
    }

    /** Every data file the library currently has open, keyed by slot. */
    public Map<EphemerisFileSlot, EphemerisFile> currentFiles() {
        Map<EphemerisFileSlot, EphemerisFile> result = new LinkedHashMap<>();
        for (EphemerisFileSlot slot : EphemerisFileSlot.values()) {
            currentFile(slot).ifPresent(file -> result.put(slot, file));
        }
        return Collections.unmodifiableMap(result);
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    /**
     * Calls {@code swe_set_ephe_path()} and closes any data files already open.
     *
     * <p>Affects every handle on this library. Note that the native code lets
     * the {@code SE_EPHE_PATH} environment variable override this argument, so
     * {@link #currentFiles()} is the way to confirm which directory actually
     * won.</p>
     *
     * @throws IllegalArgumentException if the path is longer than
     *                                  {@link #MAX_EPHEMERIS_PATH_BYTES} bytes,
     *                                  which the native code would silently
     *                                  replace with its compiled-in default
     */
    public void setEphemerisPath(String path) {
        NativeStrings.requireNativeSafe(path, "path", MAX_EPHEMERIS_PATH_BYTES + 1);
        call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                bindings.setEphemerisPath(arena.allocateFrom(path));
            }
            // Recorded on the native thread, so this read-modify-write is
            // serialized against every other settings update.
            context.settings(context.settings().withEphemerisPath(path));
            return null;
        });
    }

    /** Calls {@code swe_set_ephe_path()} with a single directory. */
    public void setEphemerisPath(Path path) {
        Objects.requireNonNull(path, "path");
        setEphemerisPath(path.toAbsolutePath().normalize().toString());
    }

    /** Calls {@code swe_set_jpl_file()}. Affects every handle on this library. */
    public void setJplFile(String fileName) {
        NativeStrings.requireNativeSafe(fileName, "fileName", NativeBindings.AS_MAXCH);
        call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                bindings.setJplFile(arena.allocateFrom(fileName));
            }
            // Recorded on the native thread, so this read-modify-write is
            // serialized against every other settings update.
            context.settings(context.settings().withJplFile(fileName));
            return null;
        });
    }

    /**
     * Calls {@code swe_set_topo()}, the observer used when
     * {@link CalculationFlag#TOPOCENTRIC} is requested.
     */
    public void setTopocentricObserver(GeographicPosition observer) {
        Objects.requireNonNull(observer, "observer");
        call(bindings -> {
            bindings.setTopocentricPosition(
                    observer.longitude(), observer.latitude(), observer.altitudeMeters());
            // Recorded on the native thread, so this read-modify-write is
            // serialized against every other settings update.
            context.settings(context.settings().withTopocentricObserver(observer));
            return null;
        });
    }

    /** Selects a predefined ayanamsha for {@link CalculationFlag#SIDEREAL}. */
    public void setSiderealMode(SiderealMode mode, SiderealOption... options) {
        Objects.requireNonNull(mode, "mode");
        if (mode == SiderealMode.USER) {
            throw new IllegalArgumentException("SiderealMode.USER defines its ayanamsha from t0 "
                    + "and ayanamsaAtT0; use setSiderealMode(int, double, double) to supply them");
        }
        setSiderealMode(mode.value() | SiderealOption.mask(options), 0.0, 0.0);
    }

    /**
     * Calls {@code swe_set_sid_mode()} with a raw mode value.
     *
     * @param mode         a {@link SiderealMode} value, optionally OR-ed with
     *                     {@link SiderealOption} bits
     * @param t0           reference epoch, for {@link SiderealMode#USER}
     * @param ayanamsaAtT0 ayanamsha at {@code t0}, for {@link SiderealMode#USER}
     */
    public void setSiderealMode(int mode, double t0, double ayanamsaAtT0) {
        Validation.siderealMode(mode);
        Validation.siderealReference(mode, t0, ayanamsaAtT0);
        call(bindings -> {
            bindings.setSiderealMode(mode, t0, ayanamsaAtT0);
            // Recorded on the native thread, so this read-modify-write is
            // serialized against every other settings update.
            context.settings(context.settings().withSiderealMode(mode, t0, ayanamsaAtT0));
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Names
    // ------------------------------------------------------------------

    /** A display name from {@code swe_get_planet_name()}. */
    public String bodyName(CelestialBody body) {
        return bodyName(Objects.requireNonNull(body, "body").id());
    }

    /** Accepts asteroid and fictitious-body identifiers as well as the standard ones. */
    public String bodyName(int bodyId) {
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment buffer = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                bindings.planetName(bodyId, buffer);
                return NativeStrings.readBuffer(buffer);
            }
        });
    }

    /** The name {@code swe_get_ayanamsa_name()} gives a sidereal mode. */
    public String ayanamsaName(SiderealMode mode) {
        return ayanamsaName(Objects.requireNonNull(mode, "mode").value());
    }

    /**
     * The name {@code swe_get_ayanamsa_name()} gives a raw sidereal-mode value.
     *
     * <p>Returns an empty string for a mode the library does not define.</p>
     *
     * @throws IllegalArgumentException if {@code mode} is negative. The C code
     *         reduces the argument with {@code isidmode %= 256} and then checks
     *         only the upper bound, so a negative value reaches
     *         {@code ayanamsa_name[-1]} and reads outside the table.
     */
    public String ayanamsaName(int mode) {
        if (mode < 0) {
            throw new IllegalArgumentException(
                    "sidereal mode must not be negative, but was " + mode);
        }
        return call(bindings -> {
            String name = NativeStrings.read(bindings.ayanamsaName(mode), NativeBindings.AS_MAXCH);
            return name == null ? "" : name;
        });
    }

    /** The name {@code swe_house_name()} gives a house system. */
    public String houseName(HouseSystem houseSystem) {
        Objects.requireNonNull(houseSystem, "houseSystem");
        return call(bindings -> {
            String name = NativeStrings.read(
                    bindings.houseName(houseSystem.code()), NativeBindings.AS_MAXCH);
            return name == null ? "" : name;
        });
    }

    // ------------------------------------------------------------------
    // Time
    // ------------------------------------------------------------------

    /**
     * Converts a civil date to a Julian day with {@code swe_julday()}.
     *
     * <p>Out-of-range fields are normalised rather than rejected, because that is
     * what {@code swe_julday()} does and this method exists to expose it: the
     * 31st of February becomes the 2nd or 3rd of March, and an hour of 25 rolls
     * into the next day. Code ported from C would change meaning if this
     * validated instead. Use {@link #utcToJulianDay(UtcDateTime, CalendarType)}
     * when you want impossible dates refused; {@link UtcDateTime} checks its
     * fields.</p>
     */
    public double julianDay(int year, int month, int day, double hour, CalendarType calendar) {
        Objects.requireNonNull(calendar, "calendar");
        Validation.finite(hour, "hour");
        return call(bindings -> bindings.julianDay(year, month, day, hour, calendar.value()));
    }

    /** Converts a Julian day back to a civil date with {@code swe_revjul()}. */
    public CivilDate reverseJulianDay(double julianDay, CalendarType calendar) {
        Objects.requireNonNull(calendar, "calendar");
        Validation.julianDay(julianDay, "julianDay");
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment year = arena.allocate(JAVA_INT);
                MemorySegment month = arena.allocate(JAVA_INT);
                MemorySegment day = arena.allocate(JAVA_INT);
                MemorySegment hour = arena.allocate(JAVA_DOUBLE);
                bindings.reverseJulianDay(julianDay, calendar.value(), year, month, day, hour);
                return new CivilDate(
                        year.get(JAVA_INT, 0),
                        month.get(JAVA_INT, 0),
                        day.get(JAVA_INT, 0),
                        hour.get(JAVA_DOUBLE, 0));
            }
        });
    }

    /** Converts a UTC timestamp to Julian days in ephemeris and universal time. */
    public JulianDate utcToJulianDay(UtcDateTime utc, CalendarType calendar) {
        Objects.requireNonNull(utc, "utc");
        Objects.requireNonNull(calendar, "calendar");
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment result = arena.allocate(JAVA_DOUBLE, 2);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                int code = bindings.utcToJulianDay(utc.year(), utc.month(), utc.day(), utc.hour(),
                        utc.minute(), utc.second(), calendar.value(), result, error);
                if (code < 0) {
                    throw new SwissEphException(
                            "swe_utc_to_jd", code, NativeStrings.readBuffer(error));
                }
                return new JulianDate(
                        result.getAtIndex(JAVA_DOUBLE, 0),
                        result.getAtIndex(JAVA_DOUBLE, 1));
            }
        });
    }

    /** Converts a Julian day in ephemeris time to UTC with {@code swe_jdet_to_utc()}. */
    public UtcDateTime ephemerisTimeToUtc(double julianDayEt, CalendarType calendar) {
        return toUtc(julianDayEt, calendar, true);
    }

    /** Converts a Julian day in universal time to UTC with {@code swe_jdut1_to_utc()}. */
    public UtcDateTime universalTimeToUtc(double julianDayUt, CalendarType calendar) {
        return toUtc(julianDayUt, calendar, false);
    }

    private UtcDateTime toUtc(double julianDay, CalendarType calendar, boolean ephemerisTime) {
        Objects.requireNonNull(calendar, "calendar");
        Validation.julianDay(julianDay, "julianDay");
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment year = arena.allocate(JAVA_INT);
                MemorySegment month = arena.allocate(JAVA_INT);
                MemorySegment day = arena.allocate(JAVA_INT);
                MemorySegment hour = arena.allocate(JAVA_INT);
                MemorySegment minute = arena.allocate(JAVA_INT);
                MemorySegment second = arena.allocate(JAVA_DOUBLE);
                if (ephemerisTime) {
                    bindings.ephemerisTimeToUtc(julianDay, calendar.value(),
                            year, month, day, hour, minute, second);
                } else {
                    bindings.universalTimeToUtc(julianDay, calendar.value(),
                            year, month, day, hour, minute, second);
                }
                return new UtcDateTime(
                        year.get(JAVA_INT, 0),
                        month.get(JAVA_INT, 0),
                        day.get(JAVA_INT, 0),
                        hour.get(JAVA_INT, 0),
                        minute.get(JAVA_INT, 0),
                        second.get(JAVA_DOUBLE, 0));
            }
        });
    }

    /**
     * Shifts a timestamp by a time-zone offset with {@code swe_utc_time_zone()}.
     *
     * <p>The native code computes {@code output = input - offsetHours}. So to
     * turn a <em>local</em> time into UTC, pass the zone's offset east of UTC:
     * 12:00 in UTC+2 with {@code offsetHours = 2} gives 10:00 UTC. To go the
     * other way, from UTC to local time, pass the negation.</p>
     *
     * @param offsetHours the zone's offset east of UTC, to convert local time to
     *                    UTC; negate it to convert UTC to local time
     */
    public UtcDateTime applyTimeZone(UtcDateTime time, double offsetHours) {
        Objects.requireNonNull(time, "time");
        Validation.finite(offsetHours, "offsetHours");
        if (offsetHours < -24.0 || offsetHours > 24.0) {
            throw new IllegalArgumentException(
                    "offsetHours must be between -24 and 24, but was " + offsetHours);
        }
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment year = arena.allocate(JAVA_INT);
                MemorySegment month = arena.allocate(JAVA_INT);
                MemorySegment day = arena.allocate(JAVA_INT);
                MemorySegment hour = arena.allocate(JAVA_INT);
                MemorySegment minute = arena.allocate(JAVA_INT);
                MemorySegment second = arena.allocate(JAVA_DOUBLE);
                bindings.applyTimeZone(time.year(), time.month(), time.day(), time.hour(),
                        time.minute(), time.second(), offsetHours,
                        year, month, day, hour, minute, second);
                return new UtcDateTime(
                        year.get(JAVA_INT, 0),
                        month.get(JAVA_INT, 0),
                        day.get(JAVA_INT, 0),
                        hour.get(JAVA_INT, 0),
                        minute.get(JAVA_INT, 0),
                        second.get(JAVA_DOUBLE, 0));
            }
        });
    }

    /**
     * Returns ET minus UT in days, using the ephemeris currently in force.
     *
     * <p>Calls {@code swe_deltat()} rather than {@code swe_deltat_ex()} with a
     * flag of zero. The two are not the same: {@code swe_deltat()} asks
     * {@code swi_guess_ephe_flag()} which ephemeris is actually open and derives
     * the tidal acceleration from that file's DE number, while a flag of zero
     * falls back to the built-in constants. The difference is silent and grows
     * with distance from the present -- around 0.3 s in 1800 and 11 s in the
     * year 1000 against a DE441 file -- so the promise in the first line only
     * holds with the guessing form.</p>
     */
    public double deltaT(double julianDay) {
        Validation.julianDay(julianDay, "julianDay");
        return call(bindings -> bindings.deltaT(julianDay));
    }

    /** Returns ET minus UT in days as computed for a specific ephemeris. */
    public double deltaT(double julianDay, Ephemeris ephemeris) {
        return deltaT(julianDay, Objects.requireNonNull(ephemeris, "ephemeris").value());
    }

    /** Variant of {@code swe_deltat_ex()} taking a raw ephemeris flag. */
    public double deltaT(double julianDay, int ephemerisFlags) {
        Validation.julianDay(julianDay, "julianDay");
        // serr is passed as NULL on purpose. The only thing swe_deltat_ex reports
        // there is that no ephemeris path has been set, which a caller can read
        // directly and earlier from settings() and currentFiles(); allocating a
        // buffer only to drop its contents would suggest otherwise.
        return call(bindings -> bindings.deltaTEx(julianDay, ephemerisFlags, MemorySegment.NULL));
    }

    /** Greenwich mean sidereal time in hours, from {@code swe_sidtime()}. */
    public double siderealTime(double julianDayUt) {
        Validation.julianDay(julianDayUt, "julianDayUt");
        return call(bindings -> bindings.siderealTime(julianDayUt));
    }

    // ------------------------------------------------------------------
    // Positions
    // ------------------------------------------------------------------

    /** Calculates a body position for a Julian day in universal time. */
    public EphemerisPosition calculateUt(double julianDayUt, CelestialBody body,
                                         CalculationFlag... flags) {
        return calculateUt(julianDayUt, Objects.requireNonNull(body, "body").id(), flags);
    }

    /** Accepts asteroid and fictitious-body identifiers as well as the standard ones. */
    public EphemerisPosition calculateUt(double julianDayUt, int bodyId, CalculationFlag... flags) {
        return calculateUt(julianDayUt, bodyId, CalculationFlag.mask(flags));
    }

    /** Variant taking an already combined native {@code iflag} mask. */
    public EphemerisPosition calculateUt(double julianDayUt, int bodyId, int flags) {
        return calculate(true, julianDayUt, bodyId, flags);
    }

    /** Calculates a body position for a Julian day in ephemeris time. */
    public EphemerisPosition calculate(double julianDayEt, CelestialBody body,
                                       CalculationFlag... flags) {
        return calculate(julianDayEt, Objects.requireNonNull(body, "body").id(), flags);
    }

    /** Accepts asteroid and fictitious-body identifiers as well as the standard ones. */
    public EphemerisPosition calculate(double julianDayEt, int bodyId, CalculationFlag... flags) {
        return calculate(julianDayEt, bodyId, CalculationFlag.mask(flags));
    }

    /** Variant taking an already combined native {@code iflag} mask. */
    public EphemerisPosition calculate(double julianDayEt, int bodyId, int flags) {
        return calculate(false, julianDayEt, bodyId, flags);
    }

    private EphemerisPosition calculate(boolean universalTime, double julianDay, int bodyId,
                                        int flags) {
        Validation.julianDay(julianDay, "julianDay");
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment values = arena.allocate(JAVA_DOUBLE, POSITION_VALUE_COUNT);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                int returnedFlags = universalTime
                        ? bindings.calcUt(julianDay, bodyId, flags, values, error)
                        : bindings.calc(julianDay, bodyId, flags, values, error);
                String message = NativeStrings.readBuffer(error);
                if (returnedFlags < 0) {
                    throw new SwissEphException(
                            universalTime ? "swe_calc_ut" : "swe_calc", returnedFlags, message);
                }
                return position(values, returnedFlags, message);
            }
        });
    }

    /** Calculates a fixed-star position with {@code swe_fixstar_ut()}. */
    public FixedStarPosition fixedStarUt(double julianDayUt, String starName,
                                         CalculationFlag... flags) {
        return fixedStarUt(julianDayUt, starName, CalculationFlag.mask(flags));
    }

    /** Variant taking an already combined native {@code iflag} mask. */
    public FixedStarPosition fixedStarUt(double julianDayUt, String starName, int flags) {
        return fixedStar(julianDayUt, starName, flags, false);
    }

    /**
     * Calculates a fixed-star position with {@code swe_fixstar2_ut()}.
     *
     * <p>Prefer this over {@link #fixedStarUt} for repeated lookups: the
     * {@code fixstar2} family indexes {@code sefstars.txt} instead of scanning
     * it on every call.</p>
     */
    public FixedStarPosition fixedStar2Ut(double julianDayUt, String starName,
                                          CalculationFlag... flags) {
        return fixedStar2Ut(julianDayUt, starName, CalculationFlag.mask(flags));
    }

    /** Variant taking an already combined native {@code iflag} mask. */
    public FixedStarPosition fixedStar2Ut(double julianDayUt, String starName, int flags) {
        return fixedStar(julianDayUt, starName, flags, true);
    }

    private FixedStarPosition fixedStar(double julianDayUt, String starName, int flags,
                                        boolean indexed) {
        NativeStrings.requireNativeSafe(starName, "starName", NativeBindings.MAX_STAR_NAME);
        if (starName.isBlank()) {
            throw new IllegalArgumentException("starName must not be blank");
        }
        Validation.julianDay(julianDayUt, "julianDayUt");
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment star = allocateStarName(arena, starName);
                MemorySegment values = arena.allocate(JAVA_DOUBLE, POSITION_VALUE_COUNT);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                int returnedFlags = indexed
                        ? bindings.fixedStar2Ut(star, julianDayUt, flags, values, error)
                        : bindings.fixedStarUt(star, julianDayUt, flags, values, error);
                String message = NativeStrings.readBuffer(error);
                if (returnedFlags < 0) {
                    throw new SwissEphException(
                            indexed ? "swe_fixstar2_ut" : "swe_fixstar_ut", returnedFlags, message);
                }
                return new FixedStarPosition(
                        NativeStrings.readBuffer(star), position(values, returnedFlags, message));
            }
        });
    }

    /** The catalogue magnitude of a fixed star, from {@code swe_fixstar2_mag()}. */
    public double fixedStarMagnitude(String starName) {
        NativeStrings.requireNativeSafe(starName, "starName", NativeBindings.MAX_STAR_NAME);
        if (starName.isBlank()) {
            throw new IllegalArgumentException("starName must not be blank");
        }
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment star = allocateStarName(arena, starName);
                MemorySegment magnitude = arena.allocate(JAVA_DOUBLE);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                int code = bindings.fixedStar2Magnitude(star, magnitude, error);
                if (code < 0) {
                    throw new SwissEphException(
                            "swe_fixstar2_mag", code, NativeStrings.readBuffer(error));
                }
                return magnitude.get(JAVA_DOUBLE, 0);
            }
        });
    }

    /** Planetary phenomena for a Julian day in universal time. */
    public PlanetaryPhenomena phenomenaUt(double julianDayUt, CelestialBody body,
                                          CalculationFlag... flags) {
        return phenomenaUt(julianDayUt, Objects.requireNonNull(body, "body").id(), flags);
    }

    /** Accepts asteroid identifiers as well as the standard ones. */
    public PlanetaryPhenomena phenomenaUt(double julianDayUt, int bodyId, CalculationFlag... flags) {
        return phenomenaUt(julianDayUt, bodyId, CalculationFlag.mask(flags));
    }

    /** Variant taking an already combined native {@code iflag} mask. */
    public PlanetaryPhenomena phenomenaUt(double julianDayUt, int bodyId, int flags) {
        return phenomena(true, julianDayUt, bodyId, flags);
    }

    /** Planetary phenomena for a Julian day in ephemeris time. */
    public PlanetaryPhenomena phenomena(double julianDayEt, CelestialBody body,
                                        CalculationFlag... flags) {
        return phenomena(false, julianDayEt, Objects.requireNonNull(body, "body").id(),
                CalculationFlag.mask(flags));
    }

    private PlanetaryPhenomena phenomena(boolean universalTime, double julianDay, int bodyId,
                                         int flags) {
        Validation.safeBodyIdentifier(bodyId, "a phenomena calculation");
        Validation.julianDay(julianDay, "julianDay");
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment attributes = arena.allocate(JAVA_DOUBLE, PHENOMENA_ATTRIBUTE_COUNT);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                int code = universalTime
                        ? bindings.phenomenaUt(julianDay, bodyId, flags, attributes, error)
                        : bindings.phenomena(julianDay, bodyId, flags, attributes, error);
                String message = NativeStrings.readBuffer(error);
                if (code < 0) {
                    throw new SwissEphException(
                            universalTime ? "swe_pheno_ut" : "swe_pheno", code, message);
                }
                return new PlanetaryPhenomena(
                        attributes.toArray(JAVA_DOUBLE), message);
            }
        });
    }

    /** The ayanamsha for a Julian day in universal time, from {@code swe_get_ayanamsa_ex_ut()}. */
    public double ayanamsaUt(double julianDayUt, CalculationFlag... flags) {
        return ayanamsa(true, julianDayUt, CalculationFlag.mask(flags));
    }

    /** The ayanamsha for a Julian day in ephemeris time, from {@code swe_get_ayanamsa_ex()}. */
    public double ayanamsa(double julianDayEt, CalculationFlag... flags) {
        return ayanamsa(false, julianDayEt, CalculationFlag.mask(flags));
    }

    private double ayanamsa(boolean universalTime, double julianDay, int flags) {
        Validation.julianDay(julianDay, "julianDay");
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment result = arena.allocate(JAVA_DOUBLE);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                int code = universalTime
                        ? bindings.ayanamsaUt(julianDay, flags, result, error)
                        : bindings.ayanamsa(julianDay, flags, result, error);
                if (code < 0) {
                    throw new SwissEphException(
                            universalTime ? "swe_get_ayanamsa_ex_ut" : "swe_get_ayanamsa_ex",
                            code, NativeStrings.readBuffer(error));
                }
                return result.get(JAVA_DOUBLE, 0);
            }
        });
    }

    // ------------------------------------------------------------------
    // Houses
    // ------------------------------------------------------------------

    /**
     * Calculates house cusps with {@code swe_houses_ex()}.
     *
     * <p>Takes a {@link GeographicPosition} rather than two bare doubles: the C
     * function takes latitude before longitude, the opposite of every other
     * observer-dependent call, and that asymmetry is a well-known source of
     * silently wrong charts.</p>
     *
     * <p>Only {@link CalculationFlag#SIDEREAL}, {@link CalculationFlag#RADIANS},
     * and {@link CalculationFlag#NO_NUTATION} affect this call.</p>
     */
    public HouseCusps houses(double julianDayUt, GeographicPosition observer,
                             HouseSystem houseSystem, CalculationFlag... flags) {
        return houses(julianDayUt, observer, houseSystem, CalculationFlag.mask(flags));
    }

    /** Variant taking an already combined native {@code iflag} mask. */
    public HouseCusps houses(double julianDayUt, GeographicPosition observer,
                             HouseSystem houseSystem, int flags) {
        Objects.requireNonNull(observer, "observer");
        Objects.requireNonNull(houseSystem, "houseSystem");
        Validation.julianDay(julianDayUt, "julianDayUt");
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                int cuspCount = houseSystem.cuspArrayLength();
                MemorySegment cusps = arena.allocate(JAVA_DOUBLE, cuspCount);
                MemorySegment additionalPoints =
                        arena.allocate(JAVA_DOUBLE, HOUSE_ADDITIONAL_POINT_COUNT);
                int code = bindings.housesEx(julianDayUt, flags,
                        observer.latitude(), observer.longitude(), houseSystem.code(),
                        cusps, additionalPoints);
                // A negative code does not mean "no result": the library fills the
                // cusps from a substitute system, most often Porphyry standing in
                // for Placidus beyond the polar circles.
                return new HouseCusps(cusps.toArray(JAVA_DOUBLE),
                        additionalPoints.toArray(JAVA_DOUBLE), houseSystem, code >= 0);
            }
        });
    }

    /**
     * Calculates house cusps from a sidereal time with {@code swe_houses_armc()}.
     *
     * <p>The Sunshine systems are not accepted here. Unlike every other system,
     * they need the Sun's declination, which {@code swe_houses_armc()} reads
     * <em>in</em> through {@code ascmc[9]} rather than deriving it: the
     * timestamp-based entry point computes it from {@code tjd_ut}, this one
     * cannot. Passing an unset buffer would be read as a declination of exactly
     * zero and produce plausible but wrong cusps, so the request is refused and
     * {@link #housesFromArmc(double, double, double, HouseSystem, double)} takes
     * the declination explicitly.</p>
     *
     * @param armc              right ascension of the midheaven, in degrees
     * @param latitude          geographic latitude in degrees
     * @param eclipticObliquity true obliquity of the ecliptic, in degrees
     * @throws IllegalArgumentException if {@code houseSystem} is a Sunshine system
     */
    public HouseCusps housesFromArmc(double armc, double latitude, double eclipticObliquity,
                                     HouseSystem houseSystem) {
        Objects.requireNonNull(houseSystem, "houseSystem");
        if (houseSystem.needsSolarDeclination()) {
            throw new IllegalArgumentException(houseSystem + " needs the Sun's declination, which "
                    + "swe_houses_armc() cannot derive from an ARMC. Use the overload that takes "
                    + "sunDeclination, or houses() with a Julian day.");
        }
        return housesFromArmc(armc, latitude, eclipticObliquity, houseSystem, Double.NaN);
    }

    /**
     * Calculates house cusps from a sidereal time and the Sun's declination.
     *
     * <p>The declination is only read for the Sunshine systems; every other
     * system ignores it.</p>
     *
     * @param armc              right ascension of the midheaven, in degrees
     * @param latitude          geographic latitude in degrees
     * @param eclipticObliquity true obliquity of the ecliptic, in degrees
     * @param sunDeclination    declination of the Sun in degrees, between -24 and
     *                          24; ignored by non-Sunshine systems, where
     *                          {@code NaN} is accepted
     */
    public HouseCusps housesFromArmc(double armc, double latitude, double eclipticObliquity,
                                     HouseSystem houseSystem, double sunDeclination) {
        Objects.requireNonNull(houseSystem, "houseSystem");
        Validation.degrees(armc, "armc");
        Validation.latitude(latitude);
        Validation.degrees(eclipticObliquity, "eclipticObliquity");
        boolean needsDeclination = houseSystem.needsSolarDeclination();
        if (needsDeclination) {
            Validation.finite(sunDeclination, "sunDeclination");
            // The native code rejects anything outside this band, but only through
            // a return code that would otherwise reach the caller as a substituted
            // house system rather than a bad argument.
            if (sunDeclination < -24.0 || sunDeclination > 24.0) {
                throw new IllegalArgumentException(
                        "sunDeclination must be between -24 and 24 degrees, but was "
                                + sunDeclination);
            }
        }
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                int cuspCount = houseSystem.cuspArrayLength();
                MemorySegment cusps = arena.allocate(JAVA_DOUBLE, cuspCount);
                MemorySegment additionalPoints =
                        arena.allocate(JAVA_DOUBLE, HOUSE_ADDITIONAL_POINT_COUNT);
                // ascmc[9] is an input for Sunshine houses. Index 9 is untouched
                // output for every other system, so writing it is harmless there.
                additionalPoints.setAtIndex(JAVA_DOUBLE, 9,
                        needsDeclination ? sunDeclination : SOLAR_DECLINATION_UNKNOWN);
                int code = bindings.housesArmc(armc, latitude, eclipticObliquity,
                        houseSystem.code(), cusps, additionalPoints);
                return new HouseCusps(cusps.toArray(JAVA_DOUBLE),
                        additionalPoints.toArray(JAVA_DOUBLE), houseSystem, code >= 0);
            }
        });
    }

    /**
     * The house a body falls in, from {@code swe_house_pos()}.
     *
     * <p>The Sunshine systems are refused. {@code swe_house_pos()} has nowhere to
     * take the Sun's declination from, so it writes the {@code ascmc[9] == 99}
     * sentinel and the library falls back to a declination cached from whatever
     * call happened to run before -- upstream's own comment there says this "can
     * lead to bugs". The same request would then answer differently depending on
     * unrelated history, so it is rejected rather than served.</p>
     *
     * @return a value from 1.0 to just under 13.0, where the fractional part is
     *         the progress through the house; 1.0 to just under 37.0 for
     *         {@link HouseSystem#GAUQUELIN}
     * @throws IllegalArgumentException if {@code houseSystem} is a Sunshine system
     */
    public double housePosition(double armc, double latitude, double eclipticObliquity,
                                HouseSystem houseSystem, double eclipticLongitude,
                                double eclipticLatitude) {
        Objects.requireNonNull(houseSystem, "houseSystem");
        if (houseSystem.needsSolarDeclination()) {
            throw new IllegalArgumentException(houseSystem + " needs the Sun's declination, which "
                    + "swe_house_pos() cannot be given: it would silently reuse a declination "
                    + "cached from an earlier, unrelated call.");
        }
        Validation.degrees(armc, "armc");
        Validation.latitude(latitude);
        Validation.degrees(eclipticObliquity, "eclipticObliquity");
        Validation.degrees(eclipticLongitude, "eclipticLongitude");
        Validation.degrees(eclipticLatitude, "eclipticLatitude");
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment input = arena.allocate(JAVA_DOUBLE, 2);
                input.setAtIndex(JAVA_DOUBLE, 0, eclipticLongitude);
                input.setAtIndex(JAVA_DOUBLE, 1, eclipticLatitude);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                double result = bindings.housePosition(armc, latitude, eclipticObliquity,
                        houseSystem.code(), input, error);
                String message = NativeStrings.readBuffer(error);
                if (result <= 0.0) {
                    throw new SwissEphException("swe_house_pos", 0, message);
                }
                return result;
            }
        });
    }

    // ------------------------------------------------------------------
    // Horizon
    // ------------------------------------------------------------------

    /** Converts ecliptic or equatorial coordinates to azimuth and altitude. */
    public HorizontalCoordinates azimuthAltitude(
            double julianDayUt,
            HorizontalCoordinateType coordinateType,
            GeographicPosition observer,
            AtmosphericConditions atmosphere,
            double firstCoordinate,
            double secondCoordinate,
            double distance) {
        Objects.requireNonNull(coordinateType, "coordinateType");
        Objects.requireNonNull(observer, "observer");
        Objects.requireNonNull(atmosphere, "atmosphere");
        Validation.julianDay(julianDayUt, "julianDayUt");
        Validation.pressureModel(observer, atmosphere);
        Validation.degrees(firstCoordinate, "firstCoordinate");
        Validation.degrees(secondCoordinate, "secondCoordinate");
        Validation.finite(distance, "distance");
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment geographicPosition = allocateObserver(arena, observer);
                MemorySegment input = arena.allocate(JAVA_DOUBLE, 3);
                input.setAtIndex(JAVA_DOUBLE, 0, firstCoordinate);
                input.setAtIndex(JAVA_DOUBLE, 1, secondCoordinate);
                input.setAtIndex(JAVA_DOUBLE, 2, distance);
                MemorySegment result = arena.allocate(JAVA_DOUBLE, 3);
                bindings.azalt(julianDayUt, coordinateType.value(), geographicPosition,
                        atmosphere.pressureMillibar(), atmosphere.temperatureCelsius(),
                        input, result);
                return new HorizontalCoordinates(
                        result.getAtIndex(JAVA_DOUBLE, 0),
                        result.getAtIndex(JAVA_DOUBLE, 1),
                        result.getAtIndex(JAVA_DOUBLE, 2));
            }
        });
    }

    /**
     * Converts azimuth and true altitude back to ecliptic or equatorial
     * coordinates with {@code swe_azalt_rev()}.
     *
     * @param coordinateType {@link HorizontalCoordinateType#ECLIPTIC} selects
     *                       {@code SE_HOR2ECL}, {@link HorizontalCoordinateType#EQUATORIAL}
     *                       selects {@code SE_HOR2EQU}
     * @param trueAltitude   the geometric altitude, refraction excluded
     */
    public SphericalCoordinates azimuthAltitudeReverse(
            double julianDayUt,
            HorizontalCoordinateType coordinateType,
            GeographicPosition observer,
            double azimuth,
            double trueAltitude) {
        Objects.requireNonNull(coordinateType, "coordinateType");
        Objects.requireNonNull(observer, "observer");
        Validation.julianDay(julianDayUt, "julianDayUt");
        Validation.degrees(azimuth, "azimuth");
        Validation.degrees(trueAltitude, "trueAltitude");
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment geographicPosition = allocateObserver(arena, observer);
                MemorySegment input = arena.allocate(JAVA_DOUBLE, 2);
                input.setAtIndex(JAVA_DOUBLE, 0, azimuth);
                input.setAtIndex(JAVA_DOUBLE, 1, trueAltitude);
                MemorySegment result = arena.allocate(JAVA_DOUBLE, 2);
                bindings.azaltReverse(julianDayUt, coordinateType.value(), geographicPosition,
                        input, result);
                return new SphericalCoordinates(
                        result.getAtIndex(JAVA_DOUBLE, 0),
                        result.getAtIndex(JAVA_DOUBLE, 1),
                        coordinateType);
            }
        });
    }

    /** Searches for a rise, set, or transit of a standard body. */
    public RiseTransitResult riseTransit(double startJulianDayUt, CelestialBody body,
                                         GeographicPosition observer,
                                         AtmosphericConditions atmosphere,
                                         RiseTransitFlag... eventFlags) {
        Objects.requireNonNull(body, "body");
        return riseTransit(startJulianDayUt, body.id(), 0, RiseTransitFlag.mask(eventFlags),
                observer, atmosphere);
    }

    /** Searches for a rise, set, or transit of any body identifier. */
    public RiseTransitResult riseTransit(double startJulianDayUt, int bodyId, int ephemerisFlags,
                                         int eventFlags, GeographicPosition observer,
                                         AtmosphericConditions atmosphere) {
        return riseTransit(startJulianDayUt, bodyId, null, ephemerisFlags, eventFlags, observer,
                atmosphere);
    }

    /** Searches for a rise, set, or transit of a fixed star. */
    public RiseTransitResult riseTransit(double startJulianDayUt, String starName,
                                         int ephemerisFlags, int eventFlags,
                                         GeographicPosition observer,
                                         AtmosphericConditions atmosphere) {
        NativeStrings.requireNativeSafe(starName, "starName", NativeBindings.MAX_STAR_NAME);
        if (starName.isBlank()) {
            throw new IllegalArgumentException("starName must not be blank");
        }
        return riseTransit(startJulianDayUt, 0, starName, ephemerisFlags, eventFlags, observer,
                atmosphere);
    }

    /**
     * Rejects disc options the native code would accept and then not apply.
     *
     * <p>Each case is read straight off {@code swe_rise_trans_true_hor()}:
     * {@code SE_BIT_FIXED_DISC_SIZE} only rewrites the distance for the Sun and
     * the Moon; a fixed star takes {@code dd = 0}, which makes the disc radius
     * zero and {@code SE_BIT_DISC_BOTTOM} a no-op; {@code SE_BIT_DISC_CENTER}
     * sets {@code dd = 0} for the same reason, so a fixed disc size has nothing
     * left to act on; and a twilight search ORs in
     * {@code SE_BIT_DISC_CENTER | SE_BIT_NO_REFRACTION} itself, overriding
     * whichever disc option was asked for.</p>
     */
    private static void validateDiscOptions(int bodyId, String starName, int eventFlags,
                                            boolean twilight) {
        boolean fixedDiscSize = (eventFlags & RiseTransitFlag.FIXED_DISC_SIZE.value()) != 0;
        boolean discBottom = (eventFlags & RiseTransitFlag.DISC_BOTTOM.value()) != 0;
        boolean discCenter = (eventFlags & RiseTransitFlag.DISC_CENTER.value()) != 0;
        boolean isStar = starName != null;
        boolean hasDisc = !isStar && Validation.hasNativeDisc(bodyId);

        if (fixedDiscSize && (isStar
                || (bodyId != CelestialBody.SUN.id() && bodyId != CelestialBody.MOON.id()))) {
            throw new IllegalArgumentException("FIXED_DISC_SIZE is only applied to the Sun and "
                    + "the Moon; for anything else the native code accepts it and ignores it");
        }
        if ((discBottom || discCenter) && !hasDisc) {
            // pla_diam holds zero for the nodes and apogees, and anything past the
            // end of the table that is not a numbered asteroid takes the
            // "else dd = 0" branch, as does every fixed star. With a disc radius
            // of zero, asking for its bottom or its centre changes nothing.
            throw new IllegalArgumentException((discBottom ? "DISC_BOTTOM" : "DISC_CENTER")
                    + " has no meaning for a target the native code gives no disc: "
                    + (isStar ? "a fixed star" : "body " + bodyId)
                    + " is computed with a disc radius of zero");
        }
        if (discCenter && fixedDiscSize) {
            throw new IllegalArgumentException("DISC_CENTER already reduces the disc to a point, "
                    + "so FIXED_DISC_SIZE has nothing left to act on");
        }
        if (twilight && (discBottom || fixedDiscSize)) {
            throw new IllegalArgumentException("a twilight search forces DISC_CENTER and "
                    + "NO_REFRACTION, so DISC_BOTTOM and FIXED_DISC_SIZE would be overridden");
        }
    }

    /** Options a meridian transit never consults. */
    private static final int TRANSIT_IGNORED_MASK =
            RiseTransitFlag.DISC_CENTER.value() | RiseTransitFlag.DISC_BOTTOM.value()
                    | RiseTransitFlag.NO_REFRACTION.value()
                    | RiseTransitFlag.FIXED_DISC_SIZE.value();

    /** The three mutually exclusive twilight options. */
    private static final int TWILIGHT_MASK =
            RiseTransitFlag.CIVIL_TWILIGHT.value() | RiseTransitFlag.NAUTICAL_TWILIGHT.value()
                    | RiseTransitFlag.ASTRONOMICAL_TWILIGHT.value();

    /** The four {@code SE_CALC_*} event bits; the rest are options. */
    private static final int RISE_TRANSIT_EVENT_MASK =
            RiseTransitFlag.RISE.value() | RiseTransitFlag.SET.value()
                    | RiseTransitFlag.UPPER_MERIDIAN_TRANSIT.value()
                    | RiseTransitFlag.LOWER_MERIDIAN_TRANSIT.value();

    private RiseTransitResult riseTransit(double startJulianDayUt, int bodyId, String starName,
                                          int ephemerisFlags, int eventFlags,
                                          GeographicPosition observer,
                                          AtmosphericConditions atmosphere) {
        // Canonical from here on: the disc checks and the native call must both
        // see the identifier the library will actually resolve the body from.
        int target = starName == null ? Validation.riseTransitTarget(bodyId) : bodyId;
        Validation.eclipseObserver(Objects.requireNonNull(observer, "observer"));
        Objects.requireNonNull(atmosphere, "atmosphere");
        Validation.julianDay(startJulianDayUt, "startJulianDayUt");
        int events = eventFlags & RISE_TRANSIT_EVENT_MASK;
        if (events == 0) {
            // The native code treats "no event requested" as a rise. Answering a
            // question that was never asked is worse than refusing it.
            throw new IllegalArgumentException("one of RISE, SET, UPPER_MERIDIAN_TRANSIT or "
                    + "LOWER_MERIDIAN_TRANSIT must be requested");
        }
        boolean isTransit = (events & (RiseTransitFlag.UPPER_MERIDIAN_TRANSIT.value()
                | RiseTransitFlag.LOWER_MERIDIAN_TRANSIT.value())) != 0;
        if (isTransit
                && (eventFlags & RiseTransitFlag.GEOCENTRIC_NO_ECLIPTIC_LATITUDE.value()) != 0) {
            // That bit sends swe_rise_trans_true_hor() down the branch that skips
            // its own swe_set_topo(), and the transit path then computes with
            // SEFLG_TOPOCTR anyway. The observer it would use is whatever the
            // library happens to hold, not the one passed here.
            throw new IllegalArgumentException("GEOCENTRIC_NO_ECLIPTIC_LATITUDE cannot be used "
                    + "with a meridian transit: the native code would compute topocentrically "
                    + "against whatever observer was last configured, not this one");
        }
        if (isTransit && (eventFlags & TRANSIT_IGNORED_MASK) != 0) {
            // A transit returns straight into calc_mer_trans(), which reads rsmi
            // only to tell an upper crossing from a lower one. Everything about
            // the disc and the atmosphere is dropped on the floor.
            throw new IllegalArgumentException("a meridian transit ignores DISC_CENTER, "
                    + "DISC_BOTTOM, NO_REFRACTION and FIXED_DISC_SIZE; they describe where on "
                    + "the horizon a body appears, which a transit does not consider");
        }
        int twilight = eventFlags & TWILIGHT_MASK;
        if (Integer.bitCount(twilight) > 1) {
            // The native code checks them in a fixed order and uses the first it
            // finds, so a combination quietly answers a different question.
            throw new IllegalArgumentException("at most one of CIVIL_TWILIGHT, "
                    + "NAUTICAL_TWILIGHT and ASTRONOMICAL_TWILIGHT may be requested");
        }
        if (twilight != 0) {
            // The twilight block sits behind an ipl == SE_SUN test and after the
            // transit branch has already returned, so anything else is accepted
            // and then quietly ignored.
            if (target != CelestialBody.SUN.id() || starName != null) {
                throw new IllegalArgumentException(
                        "twilight is only defined for the Sun; the native code ignores it "
                                + "for any other body");
            }
            if (isTransit) {
                throw new IllegalArgumentException(
                        "twilight applies to rise and set, not to a meridian transit");
            }
        }
        validateDiscOptions(target, starName, eventFlags, twilight != 0);
        if ((eventFlags & RiseTransitFlag.DISC_CENTER.value()) != 0
                && (eventFlags & RiseTransitFlag.DISC_BOTTOM.value()) != 0) {
            // Upstream tests for the centre first and never reaches the other.
            throw new IllegalArgumentException(
                    "DISC_CENTER and DISC_BOTTOM describe different reference points "
                            + "and cannot be combined");
        }
        Validation.pressureModel(observer, atmosphere);
        if (Integer.bitCount(events) > 1) {
            // Upstream resolves a combination by internal priority, so the caller
            // would get one of them without being told which.
            throw new IllegalArgumentException(
                    "exactly one event may be requested at a time, but the mask asked for "
                            + Integer.bitCount(events));
        }
        return callResettingObserver(observer, bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment star = starName == null
                        ? MemorySegment.NULL
                        : allocateStarName(arena, starName);
                MemorySegment geographicPosition = allocateObserver(arena, observer);
                MemorySegment result = arena.allocate(JAVA_DOUBLE, RISE_TRANSIT_VALUE_COUNT);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                int code = bindings.riseTransit(startJulianDayUt, target, star, ephemerisFlags,
                        eventFlags, geographicPosition, atmosphere.pressureMillibar(),
                        atmosphere.temperatureCelsius(), result, error);
                String message = NativeStrings.readBuffer(error);
                if (code == -2) {
                    // The event does not occur, for instance during a polar night.
                    return new RiseTransitResult(false, Double.NaN, message);
                }
                if (code < 0) {
                    throw new SwissEphException("swe_rise_trans", code, message);
                }
                return new RiseTransitResult(true, result.getAtIndex(JAVA_DOUBLE, 0), message);
            }
        });
    }

    // ------------------------------------------------------------------
    // Eclipses
    // ------------------------------------------------------------------

    /** Finds the next or previous solar eclipse anywhere on Earth. */
    public GlobalSolarEclipse solarEclipseWhenGlobal(double startJulianDayUt,
                                                     int ephemerisFlags,
                                                     java.util.Collection<EclipseType> types,
                                                     boolean backward) {
        Validation.julianDay(startJulianDayUt, "startJulianDayUt");
        int typeMask = EclipseType.mask(requireEclipseTypes(types, true));
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment times = arena.allocate(JAVA_DOUBLE, ECLIPSE_TIME_COUNT);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                int flags = bindings.solarEclipseWhenGlobal(startJulianDayUt, ephemerisFlags,
                        typeMask, times, backward ? 1 : 0, error);
                String message = NativeStrings.readBuffer(error);
                if (flags < 0) {
                    throw new SwissEphException("swe_sol_eclipse_when_glob", flags, message);
                }
                return new GlobalSolarEclipse(
                        new EclipseFlags(flags), times.toArray(JAVA_DOUBLE), message);
            }
        });
    }

    /** Finds the next or previous solar eclipse of any kind. */
    public GlobalSolarEclipse solarEclipseWhenGlobal(double startJulianDayUt, Ephemeris ephemeris,
                                                     boolean backward) {
        Objects.requireNonNull(ephemeris, "ephemeris");
        return solarEclipseWhenGlobal(startJulianDayUt, ephemeris.value(),
                java.util.Set.of(), backward);
    }

    /** Finds the next or previous solar eclipse visible from an observer position. */
    public LocalSolarEclipse solarEclipseWhenLocal(double startJulianDayUt, int ephemerisFlags,
                                                   GeographicPosition observer, boolean backward) {
        Validation.eclipseObserver(Objects.requireNonNull(observer, "observer"));
        Validation.julianDay(startJulianDayUt, "startJulianDayUt");
        return callResettingObserver(observer, bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment geographicPosition = allocateObserver(arena, observer);
                MemorySegment times = arena.allocate(JAVA_DOUBLE, ECLIPSE_TIME_COUNT);
                MemorySegment attributes = arena.allocate(JAVA_DOUBLE, ECLIPSE_ATTRIBUTE_COUNT);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                int flags = bindings.solarEclipseWhenLocal(startJulianDayUt, ephemerisFlags,
                        geographicPosition, times, attributes, backward ? 1 : 0, error);
                String message = NativeStrings.readBuffer(error);
                if (flags < 0) {
                    throw new SwissEphException("swe_sol_eclipse_when_loc", flags, message);
                }
                return new LocalSolarEclipse(new EclipseFlags(flags), times.toArray(JAVA_DOUBLE),
                        new SolarEclipseAttributes(attributes.toArray(JAVA_DOUBLE)), message);
            }
        });
    }

    /** Where a solar eclipse falls on Earth at a given moment. */
    public SolarEclipsePosition solarEclipseWhere(double julianDayUt, int ephemerisFlags) {
        Validation.julianDay(julianDayUt, "julianDayUt");
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment positions =
                        arena.allocate(JAVA_DOUBLE, ECLIPSE_GEOGRAPHIC_POSITION_COUNT);
                MemorySegment attributes = arena.allocate(JAVA_DOUBLE, ECLIPSE_ATTRIBUTE_COUNT);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                int flags = bindings.solarEclipseWhere(
                        julianDayUt, ephemerisFlags, positions, attributes, error);
                String message = NativeStrings.readBuffer(error);
                if (flags < 0) {
                    throw new SwissEphException("swe_sol_eclipse_where", flags, message);
                }
                return new SolarEclipsePosition(new EclipseFlags(flags),
                        positions.toArray(JAVA_DOUBLE),
                        new SolarEclipseAttributes(attributes.toArray(JAVA_DOUBLE)), message);
            }
        });
    }

    /**
     * Solar-eclipse circumstances for a moment and an observer position.
     *
     * <p>A result whose {@code isEclipsed()} is false means no eclipse is visible
     * from there. That is an answer, not a failure, which is why the native
     * return value is reported rather than reduced to a success check.</p>
     */
    public SolarEclipseCircumstances solarEclipseHow(double julianDayUt, int ephemerisFlags,
                                                     GeographicPosition observer) {
        Validation.eclipseObserver(Objects.requireNonNull(observer, "observer"));
        Validation.julianDay(julianDayUt, "julianDayUt");
        return callResettingObserver(observer, bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment geographicPosition = allocateObserver(arena, observer);
                MemorySegment attributes = arena.allocate(JAVA_DOUBLE, ECLIPSE_ATTRIBUTE_COUNT);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                int flags = bindings.solarEclipseHow(
                        julianDayUt, ephemerisFlags, geographicPosition, attributes, error);
                String message = NativeStrings.readBuffer(error);
                if (flags < 0) {
                    throw new SwissEphException("swe_sol_eclipse_how", flags, message);
                }
                return new SolarEclipseCircumstances(new EclipseFlags(flags),
                        new SolarEclipseAttributes(attributes.toArray(JAVA_DOUBLE)), message);
            }
        });
    }

    /** Finds the next or previous lunar eclipse anywhere on Earth. */
    public GlobalLunarEclipse lunarEclipseWhen(double startJulianDayUt, int ephemerisFlags,
                                               java.util.Collection<EclipseType> types,
                                               boolean backward) {
        Validation.julianDay(startJulianDayUt, "startJulianDayUt");
        int typeMask = EclipseType.mask(requireEclipseTypes(types, false));
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment times = arena.allocate(JAVA_DOUBLE, ECLIPSE_TIME_COUNT);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                int flags = bindings.lunarEclipseWhen(startJulianDayUt, ephemerisFlags, typeMask,
                        times, backward ? 1 : 0, error);
                String message = NativeStrings.readBuffer(error);
                if (flags < 0) {
                    throw new SwissEphException("swe_lun_eclipse_when", flags, message);
                }
                return new GlobalLunarEclipse(
                        new EclipseFlags(flags), times.toArray(JAVA_DOUBLE), message);
            }
        });
    }

    /** Finds the next or previous lunar eclipse of any kind. */
    public GlobalLunarEclipse lunarEclipseWhen(double startJulianDayUt, Ephemeris ephemeris,
                                               boolean backward) {
        Objects.requireNonNull(ephemeris, "ephemeris");
        return lunarEclipseWhen(startJulianDayUt, ephemeris.value(), java.util.Set.of(), backward);
    }

    /** Finds the next or previous lunar eclipse visible from an observer position. */
    public LocalLunarEclipse lunarEclipseWhenLocal(double startJulianDayUt, int ephemerisFlags,
                                                   GeographicPosition observer, boolean backward) {
        Validation.eclipseObserver(Objects.requireNonNull(observer, "observer"));
        Validation.julianDay(startJulianDayUt, "startJulianDayUt");
        return callResettingObserver(observer, bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment geographicPosition = allocateObserver(arena, observer);
                MemorySegment times = arena.allocate(JAVA_DOUBLE, ECLIPSE_TIME_COUNT);
                MemorySegment attributes = arena.allocate(JAVA_DOUBLE, ECLIPSE_ATTRIBUTE_COUNT);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                int flags = bindings.lunarEclipseWhenLocal(startJulianDayUt, ephemerisFlags,
                        geographicPosition, times, attributes, backward ? 1 : 0, error);
                String message = NativeStrings.readBuffer(error);
                if (flags < 0) {
                    throw new SwissEphException("swe_lun_eclipse_when_loc", flags, message);
                }
                return new LocalLunarEclipse(new EclipseFlags(flags), times.toArray(JAVA_DOUBLE),
                        new LunarEclipseAttributes(attributes.toArray(JAVA_DOUBLE)), message);
            }
        });
    }

    /**
     * Lunar-eclipse circumstances for a moment and an observer position.
     *
     * <p>A result whose {@code isVisible()} is false means the eclipse is not
     * visible from this place, which upstream reports by zeroing the flags while
     * still filling in the magnitudes. It does not mean there is no eclipse: read
     * {@link LunarEclipseAttributes#umbralMagnitude()} for that.</p>
     */
    public LunarEclipseCircumstances lunarEclipseHow(double julianDayUt, int ephemerisFlags,
                                                     GeographicPosition observer) {
        Validation.eclipseObserver(Objects.requireNonNull(observer, "observer"));
        Validation.julianDay(julianDayUt, "julianDayUt");
        return callResettingObserver(observer, bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment geographicPosition = allocateObserver(arena, observer);
                MemorySegment attributes = arena.allocate(JAVA_DOUBLE, ECLIPSE_ATTRIBUTE_COUNT);
                MemorySegment error = arena.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                int flags = bindings.lunarEclipseHow(
                        julianDayUt, ephemerisFlags, geographicPosition, attributes, error);
                String message = NativeStrings.readBuffer(error);
                if (flags < 0) {
                    throw new SwissEphException("swe_lun_eclipse_how", flags, message);
                }
                return new LunarEclipseCircumstances(new EclipseFlags(flags),
                        new LunarEclipseAttributes(attributes.toArray(JAVA_DOUBLE)), message);
            }
        });
    }

    // ------------------------------------------------------------------
    // Utilities
    // ------------------------------------------------------------------

    /** Splits a decimal degree into components with {@code swe_split_deg()}. */
    public DegreeParts splitDegrees(double degrees, DegreeSplitOption... options) {
        return splitDegrees(degrees, DegreeSplitOption.mask(options));
    }

    /** Variant taking an already combined native {@code roundflag} mask. */
    public DegreeParts splitDegrees(double degrees, int roundFlags) {
        Validation.finite(degrees, "degrees");
        if ((roundFlags & DegreeSplitOption.ZODIACAL.value()) != 0
                && (roundFlags & DegreeSplitOption.NAKSHATRA.value()) != 0) {
            // The native code takes the nakshatra branch and returns before it
            // ever looks at the zodiacal bit, so asking for both would leave the
            // result claiming to be a division it is not.
            throw new IllegalArgumentException(
                    "ZODIACAL and NAKSHATRA divide the circle differently and cannot be combined");
        }
        return call(bindings -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment degreePart = arena.allocate(JAVA_INT);
                MemorySegment minutePart = arena.allocate(JAVA_INT);
                MemorySegment secondPart = arena.allocate(JAVA_INT);
                MemorySegment secondFraction = arena.allocate(JAVA_DOUBLE);
                MemorySegment sign = arena.allocate(JAVA_INT);
                bindings.splitDegrees(degrees, roundFlags, degreePart, minutePart, secondPart,
                        secondFraction, sign);
                return new DegreeParts(
                        degreePart.get(JAVA_INT, 0),
                        minutePart.get(JAVA_INT, 0),
                        secondPart.get(JAVA_INT, 0),
                        secondFraction.get(JAVA_DOUBLE, 0),
                        sign.get(JAVA_INT, 0),
                        roundFlags);
            }
        });
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private <T> T call(NativeContext.NativeTask<T> task) {
        ensureOpen();
        return context.call(task);
    }

    /**
     * Runs a task whose native routine calls {@code swe_set_topo()} behind our
     * back, and leaves the observer as it found it.
     *
     * <p>{@code swe_sol_eclipse_how()}, {@code swe_lun_eclipse_how()}, the local
     * eclipse searches and {@code swe_rise_trans()} all set the process-wide
     * topocentric observer to the position they were handed. Without this, asking
     * about an eclipse at one place would silently re-aim every later
     * {@link CalculationFlag#TOPOCENTRIC} calculation at it, while
     * {@link #settings()} went on reporting the old one.</p>
     *
     * <p>When an observer was configured it is put back. When none ever was,
     * there is nothing to restore, so the snapshot records the position the
     * library now holds instead of pretending it is still unset.</p>
     *
     * @param used the observer passed to the native routine
     */
    private <T> T callResettingObserver(GeographicPosition used,
                                        NativeContext.NativeTask<T> task) {
        ensureOpen();
        return context.call(bindings -> {
            SwissEphSettings before = context.settings();
            GeographicPosition previous = before.topocentricObserver();
            try {
                return task.run(bindings);
            } finally {
                // Unconditionally, and on the failure path too: the native routines
                // validate their arguments before calling swe_set_topo(), so on an
                // error the observer may or may not have moved. Writing it here
                // makes the native state and the snapshot agree either way, rather
                // than leaving the answer to depend on where the call gave up.
                GeographicPosition settled = previous != null ? previous : used;
                bindings.setTopocentricPosition(settled.longitude(), settled.latitude(),
                        settled.altitudeMeters());
                if (previous == null) {
                    context.settings(before.withTopocentricObserver(settled));
                }
            }
        });
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("this SwissEph handle is closed");
        }
    }

    private static EphemerisPosition position(MemorySegment values, int returnedFlags,
                                              String message) {
        return new EphemerisPosition(
                values.getAtIndex(JAVA_DOUBLE, 0),
                values.getAtIndex(JAVA_DOUBLE, 1),
                values.getAtIndex(JAVA_DOUBLE, 2),
                values.getAtIndex(JAVA_DOUBLE, 3),
                values.getAtIndex(JAVA_DOUBLE, 4),
                values.getAtIndex(JAVA_DOUBLE, 5),
                new ReturnedFlags(returnedFlags),
                message);
    }

    /**
     * Rejects eclipse kinds the requested search can never produce.
     *
     * <p>Asking a solar search for a penumbral eclipse, or a lunar one for an
     * annular eclipse, is not an error to the native code. It simply keeps
     * searching for a candidate that cannot exist.</p>
     */
    private static java.util.Collection<EclipseType> requireEclipseTypes(
            java.util.Collection<EclipseType> types, boolean solar) {
        Objects.requireNonNull(types, "types");
        for (EclipseType type : types) {
            Objects.requireNonNull(type, "type");
            if (solar ? !type.isSolar() : !type.isLunar()) {
                throw new IllegalArgumentException(type + " cannot describe a "
                        + (solar ? "solar" : "lunar") + " eclipse");
            }
        }
        return types;
    }

    private static MemorySegment allocateObserver(Arena arena, GeographicPosition observer) {
        MemorySegment result = arena.allocate(JAVA_DOUBLE, 3);
        result.setAtIndex(JAVA_DOUBLE, 0, observer.longitude());
        result.setAtIndex(JAVA_DOUBLE, 1, observer.latitude());
        result.setAtIndex(JAVA_DOUBLE, 2, observer.altitudeMeters());
        return result;
    }

    private static MemorySegment allocateStarName(Arena arena, String starName) {
        // Swiss Ephemeris writes the resolved name back into this buffer, and
        // documents that it must hold twice SE_MAX_STNAME for that reason.
        MemorySegment result = arena.allocate(NativeBindings.STAR_BUFFER_SIZE);
        result.setString(0, starName);
        return result;
    }
}
