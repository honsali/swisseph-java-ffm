package org.swisseph.ffm;

import org.swisseph.ffm.internal.NativeBindings;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Java 25 FFM facade for the core Swiss Ephemeris 2.10.03 API.
 *
 * <p>Swiss Ephemeris keeps process-wide mutable native state. Calls from all
 * instances are therefore serialized, but configuration changes still affect
 * every instance backed by the same native library.</p>
 */
public final class SwissEph implements AutoCloseable {
    public static final String EXPECTED_NATIVE_VERSION = "2.10.03";
    public static final String LIBRARY_PATH_PROPERTY = "swisseph.library.path";
    public static final String LIBRARY_PATH_ENVIRONMENT = "SWISSEPH_LIBRARY";

    private static final int TEXT_BUFFER_SIZE = 256;
    private static final int POSITION_VALUE_COUNT = 6;
    private static final ReentrantLock NATIVE_LOCK = new ReentrantLock();

    private final Arena libraryArena;
    private final NativeBindings nativeBindings;
    private final AtomicBoolean closed = new AtomicBoolean();

    private SwissEph(Arena libraryArena, NativeBindings nativeBindings) {
        this.libraryArena = libraryArena;
        this.nativeBindings = nativeBindings;
    }

    /**
     * Loads a native library from an explicit DLL, SO, or DYLIB path.
     */
    public static SwissEph load(Path libraryPath) {
        Objects.requireNonNull(libraryPath, "libraryPath");
        Path absolutePath = libraryPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolutePath)) {
            throw new IllegalArgumentException("Swiss Ephemeris native library does not exist: " + absolutePath);
        }

        Arena arena = Arena.ofShared();
        try {
            SymbolLookup symbols = SymbolLookup.libraryLookup(absolutePath, arena);
            return new SwissEph(arena, new NativeBindings(symbols));
        } catch (RuntimeException | LinkageError throwable) {
            arena.close();
            throw new SwissEphException("Cannot load Swiss Ephemeris native library: " + absolutePath, throwable);
        }
    }

    /**
     * Loads the path configured with {@value #LIBRARY_PATH_PROPERTY}, falling
     * back to the {@value #LIBRARY_PATH_ENVIRONMENT} environment variable.
     */
    public static SwissEph loadConfigured() {
        String configuredPath = System.getProperty(LIBRARY_PATH_PROPERTY);
        if (configuredPath == null || configuredPath.isBlank()) {
            configuredPath = System.getenv(LIBRARY_PATH_ENVIRONMENT);
        }
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalStateException("Set -D" + LIBRARY_PATH_PROPERTY
                    + "=<native-library> or " + LIBRARY_PATH_ENVIRONMENT);
        }
        return load(Path.of(configuredPath));
    }

    /** Returns the version reported by {@code swe_version()}. */
    public String version() {
        ensureOpen();
        return locked(() -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment buffer = arena.allocate(TEXT_BUFFER_SIZE);
                nativeBindings.version(buffer);
                return buffer.getString(0);
            }
        });
    }

    /** Configures a single directory containing Swiss Ephemeris data files. */
    public void setEphemerisPath(Path path) {
        Objects.requireNonNull(path, "path");
        setEphemerisPath(path.toAbsolutePath().normalize().toString());
    }

    /**
     * Configures the native ephemeris search path. A platform-specific list of
     * directories can be passed exactly as accepted by {@code swe_set_ephe_path()}.
     */
    public void setEphemerisPath(String path) {
        Objects.requireNonNull(path, "path");
        ensureOpen();
        locked(() -> {
            try (Arena arena = Arena.ofConfined()) {
                nativeBindings.setEphemerisPath(arena.allocateFrom(path));
            }
            return null;
        });
    }

    /** Configures the JPL ephemeris file name used by the native library. */
    public void setJplFile(String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        ensureOpen();
        locked(() -> {
            try (Arena arena = Arena.ofConfined()) {
                nativeBindings.setJplFile(arena.allocateFrom(fileName));
            }
            return null;
        });
    }

    /** Configures the observer used by topocentric calculations. */
    public void setTopocentricPosition(double longitude, double latitude, double altitudeMeters) {
        ensureOpen();
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude must be between -180 and 180 degrees");
        }
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude must be between -90 and 90 degrees");
        }
        locked(() -> {
            nativeBindings.setTopocentricPosition(longitude, latitude, altitudeMeters);
            return null;
        });
    }

    /** Configures a predefined or user-defined Swiss Ephemeris sidereal mode. */
    public void setSiderealMode(int mode, double t0, double ayanamsaAtT0) {
        ensureOpen();
        locked(() -> {
            nativeBindings.setSiderealMode(mode, t0, ayanamsaAtT0);
            return null;
        });
    }

    /** Returns a display name from {@code swe_get_planet_name()}. */
    public String bodyName(CelestialBody body) {
        return bodyName(Objects.requireNonNull(body, "body").id());
    }

    /** Supports standard bodies as well as asteroid and fictitious-body IDs. */
    public String bodyName(int bodyId) {
        ensureOpen();
        return locked(() -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment buffer = arena.allocate(TEXT_BUFFER_SIZE);
                nativeBindings.planetName(bodyId, buffer);
                return buffer.getString(0);
            }
        });
    }

    /** Converts a civil date to a Julian day with {@code swe_julday()}. */
    public double julianDay(int year, int month, int day, double hour, CalendarType calendar) {
        Objects.requireNonNull(calendar, "calendar");
        ensureOpen();
        return locked(() -> nativeBindings.julianDay(year, month, day, hour, calendar.nativeValue()));
    }

    /** Converts a Julian day to a civil date with {@code swe_revjul()}. */
    public CivilDate reverseJulianDay(double julianDay, CalendarType calendar) {
        Objects.requireNonNull(calendar, "calendar");
        ensureOpen();
        return locked(() -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment year = arena.allocate(JAVA_INT);
                MemorySegment month = arena.allocate(JAVA_INT);
                MemorySegment day = arena.allocate(JAVA_INT);
                MemorySegment hour = arena.allocate(JAVA_DOUBLE);
                nativeBindings.reverseJulianDay(
                        julianDay, calendar.nativeValue(), year, month, day, hour);
                return new CivilDate(
                        year.get(JAVA_INT, 0),
                        month.get(JAVA_INT, 0),
                        day.get(JAVA_INT, 0),
                        hour.get(JAVA_DOUBLE, 0));
            }
        });
    }

    /** Converts a UTC civil date to Julian dates in ET and UT. */
    public JulianDate utcToJulianDay(int year, int month, int day, int hour, int minute,
                                     double second, CalendarType calendar) {
        Objects.requireNonNull(calendar, "calendar");
        ensureOpen();
        return locked(() -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment result = arena.allocate(JAVA_DOUBLE, 2);
                MemorySegment error = arena.allocate(TEXT_BUFFER_SIZE);
                int code = nativeBindings.utcToJulianDay(
                        year, month, day, hour, minute, second, calendar.nativeValue(), result, error);
                if (code < 0) {
                    throw new SwissEphException("swe_utc_to_jd", code, error.getString(0));
                }
                return new JulianDate(
                        result.getAtIndex(JAVA_DOUBLE, 0),
                        result.getAtIndex(JAVA_DOUBLE, 1));
            }
        });
    }

    /** Calculates a body position for a Julian day in universal time. */
    public EphemerisPosition calculateUt(double julianDayUt, CelestialBody body,
                                         CalculationFlag... flags) {
        return calculateUt(julianDayUt, Objects.requireNonNull(body, "body").id(), flags);
    }

    /** Supports standard bodies as well as asteroid and fictitious-body IDs. */
    public EphemerisPosition calculateUt(double julianDayUt, int bodyId, CalculationFlag... flags) {
        return calculate(true, julianDayUt, bodyId, CalculationFlag.mask(flags));
    }

    /** Calculates a body position for a Julian day in ephemeris time. */
    public EphemerisPosition calculate(double julianDayEt, CelestialBody body,
                                       CalculationFlag... flags) {
        return calculate(julianDayEt, Objects.requireNonNull(body, "body").id(), flags);
    }

    /** Supports standard bodies as well as asteroid and fictitious-body IDs. */
    public EphemerisPosition calculate(double julianDayEt, int bodyId, CalculationFlag... flags) {
        return calculate(false, julianDayEt, bodyId, CalculationFlag.mask(flags));
    }

    private EphemerisPosition calculate(boolean universalTime, double julianDay, int bodyId, int flags) {
        ensureOpen();
        return locked(() -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment values = arena.allocate(JAVA_DOUBLE, POSITION_VALUE_COUNT);
                MemorySegment error = arena.allocate(TEXT_BUFFER_SIZE);
                int returnedFlags = universalTime
                        ? nativeBindings.calcUt(julianDay, bodyId, flags, values, error)
                        : nativeBindings.calc(julianDay, bodyId, flags, values, error);
                String message = error.getString(0);
                if (returnedFlags < 0) {
                    throw new SwissEphException(
                            universalTime ? "swe_calc_ut" : "swe_calc", returnedFlags, message);
                }
                return new EphemerisPosition(
                        values.getAtIndex(JAVA_DOUBLE, 0),
                        values.getAtIndex(JAVA_DOUBLE, 1),
                        values.getAtIndex(JAVA_DOUBLE, 2),
                        values.getAtIndex(JAVA_DOUBLE, 3),
                        values.getAtIndex(JAVA_DOUBLE, 4),
                        values.getAtIndex(JAVA_DOUBLE, 5),
                        returnedFlags,
                        message);
            }
        });
    }

    /** Calls {@code swe_close()} and unloads this native-library lookup. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        NATIVE_LOCK.lock();
        try {
            nativeBindings.close();
        } finally {
            try {
                libraryArena.close();
            } finally {
                NATIVE_LOCK.unlock();
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("SwissEph is closed");
        }
    }

    private <T> T locked(NativeOperation<T> operation) {
        NATIVE_LOCK.lock();
        try {
            ensureOpen();
            return operation.run();
        } finally {
            NATIVE_LOCK.unlock();
        }
    }

    @FunctionalInterface
    private interface NativeOperation<T> {
        T run();
    }
}
