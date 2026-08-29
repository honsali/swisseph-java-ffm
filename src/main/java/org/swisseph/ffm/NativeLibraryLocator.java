package org.swisseph.ffm;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The contract for finding the Swiss Ephemeris shared library.
 *
 * <p>This project does not ship native binaries. Swiss Ephemeris is dual
 * licensed, its build differs per platform and per compiler, and a JAR that
 * bundled one particular build would quietly decide both the licence and the
 * numerical behaviour on behalf of every user. The library is therefore
 * supplied by the application, and this class defines exactly how it is
 * found.</p>
 *
 * <p>Sources are tried in this order, first hit wins:</p>
 * <ol>
 *   <li>the path passed explicitly to {@link SwissEphConfig.Builder#library(Path)};</li>
 *   <li>the {@code swisseph.library.path} system property, a full file path;</li>
 *   <li>the {@code SWISSEPH_LIBRARY} environment variable, a full file path;</li>
 *   <li>the {@code swisseph.library.dir} system property or the
 *       {@code SWISSEPH_LIBRARY_DIR} environment variable, a directory searched
 *       for the platform file names below;</li>
 *   <li>each entry of {@code java.library.path}, searched for the same names.</li>
 * </ol>
 *
 * <p>Platform file names, in the order they are tried:</p>
 * <ul>
 *   <li>Windows: {@code swe.dll}, {@code swedll64.dll}, {@code libswe.dll}</li>
 *   <li>macOS: {@code libswe.dylib}, {@code libswe.so}</li>
 *   <li>other: {@code libswe.so}, {@code swe.so}</li>
 * </ul>
 */
public final class NativeLibraryLocator {
    /** System property naming a complete path to the shared library. */
    public static final String LIBRARY_PATH_PROPERTY = "swisseph.library.path";
    /** Environment variable naming a complete path to the shared library. */
    public static final String LIBRARY_PATH_ENVIRONMENT = "SWISSEPH_LIBRARY";
    /** System property naming a directory to search. */
    public static final String LIBRARY_DIRECTORY_PROPERTY = "swisseph.library.dir";
    /** Environment variable naming a directory to search. */
    public static final String LIBRARY_DIRECTORY_ENVIRONMENT = "SWISSEPH_LIBRARY_DIR";

    private NativeLibraryLocator() {
    }

    /** Where a resolved library came from, for logging and error messages. */
    public record Resolution(Path path, String source) {
    }

    /** Returns the file names this platform is searched for, in order. */
    public static List<String> platformFileNames() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        // macOS first: "darwin" contains "win", so testing for Windows ahead of it
        // would hand .dll names to every JVM that reports os.name as Darwin.
        if (os.contains("mac") || os.contains("darwin")) {
            return List.of("libswe.dylib", "libswe.so");
        }
        if (os.contains("win")) {
            return List.of("swe.dll", "swedll64.dll", "libswe.dll");
        }
        return List.of("libswe.so", "swe.so");
    }

    /**
     * Searches every configured source.
     *
     * @return the first library found, or empty when none of the sources yields one
     */
    public static Optional<Resolution> locate() {
        return locate(new ArrayList<>());
    }

    /**
     * Searches every configured source, recording each candidate that was tried.
     *
     * @param attempts filled with a human-readable line per candidate; useful for
     *                 the message of a failed lookup
     */
    public static Optional<Resolution> locate(List<String> attempts) {
        Optional<Resolution> fromProperty = fromFilePath(
                System.getProperty(LIBRARY_PATH_PROPERTY),
                "system property " + LIBRARY_PATH_PROPERTY, attempts);
        if (fromProperty.isPresent()) {
            return fromProperty;
        }
        Optional<Resolution> fromEnvironment = fromFilePath(
                System.getenv(LIBRARY_PATH_ENVIRONMENT),
                "environment variable " + LIBRARY_PATH_ENVIRONMENT, attempts);
        if (fromEnvironment.isPresent()) {
            return fromEnvironment;
        }
        Optional<Resolution> fromDirectoryProperty = fromDirectory(
                System.getProperty(LIBRARY_DIRECTORY_PROPERTY),
                "system property " + LIBRARY_DIRECTORY_PROPERTY, attempts);
        if (fromDirectoryProperty.isPresent()) {
            return fromDirectoryProperty;
        }
        Optional<Resolution> fromDirectoryEnvironment = fromDirectory(
                System.getenv(LIBRARY_DIRECTORY_ENVIRONMENT),
                "environment variable " + LIBRARY_DIRECTORY_ENVIRONMENT, attempts);
        if (fromDirectoryEnvironment.isPresent()) {
            return fromDirectoryEnvironment;
        }
        for (String entry : System.getProperty("java.library.path", "")
                .split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (entry.isBlank()) {
                continue;
            }
            Optional<Resolution> found = fromDirectory(entry, "java.library.path", attempts);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /** Builds the message used when no library could be found. */
    static String describeFailure(List<String> attempts) {
        StringBuilder message = new StringBuilder("No Swiss Ephemeris native library found. Set -D")
                .append(LIBRARY_PATH_PROPERTY)
                .append("=<path-to-library>, or the ")
                .append(LIBRARY_PATH_ENVIRONMENT)
                .append(" environment variable, or place one of ")
                .append(platformFileNames())
                .append(" on java.library.path.");
        if (!attempts.isEmpty()) {
            message.append(" Candidates tried:");
            for (String attempt : attempts) {
                message.append(System.lineSeparator()).append("  - ").append(attempt);
            }
        }
        return message.toString();
    }

    private static Optional<Resolution> fromFilePath(String value, String source,
                                                     List<String> attempts) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Path candidate = toPath(value, source, attempts);
        if (candidate == null) {
            return Optional.empty();
        }
        if (Files.isRegularFile(candidate)) {
            return Optional.of(new Resolution(candidate, source));
        }
        attempts.add(candidate + " (from " + source + "): not a regular file");
        return Optional.empty();
    }

    private static Optional<Resolution> fromDirectory(String value, String source,
                                                      List<String> attempts) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Path directory = toPath(value, source, attempts);
        if (directory == null) {
            return Optional.empty();
        }
        if (!Files.isDirectory(directory)) {
            // A typo in swisseph.library.dir is the likeliest reason a lookup
            // fails, so it has to appear in the failure message rather than be
            // skipped silently.
            attempts.add(directory + " (from " + source + "): not a directory");
            return Optional.empty();
        }
        for (String fileName : platformFileNames()) {
            Path candidate = directory.resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(new Resolution(candidate, source));
            }
            attempts.add(candidate + " (from " + source + "): not found");
        }
        return Optional.empty();
    }

    private static Path toPath(String value, String source, List<String> attempts) {
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (InvalidPathException invalid) {
            attempts.add(value + " (from " + source + "): not a valid path");
            return null;
        }
    }
}
