package org.swisseph.ffm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Locates the native library and the ephemeris data the integration tests need.
 *
 * <p>Both are supplied by the build rather than bundled, so tests that need them
 * are skipped when they are absent. The CI workflow compiles Swiss Ephemeris
 * from the upstream tag and downloads the data files, so nothing is skipped
 * there.</p>
 */
final class NativeTestSupport {
    static final String LIBRARY_PROPERTY = "swisseph.integration.library";
    static final String EPHEMERIS_PROPERTY = "swisseph.integration.ephemeris";
    /**
     * Set by any build that must not ship untested bits.
     *
     * <p>With this on, a missing library or data directory fails the test rather
     * than skipping it. That is what keeps the release pipeline honest: checking
     * the surefire reports after {@code mvn deploy} would find a skipped suite
     * only once the immutable artifacts were already published.</p>
     */
    static final String REQUIRED_PROPERTY = "swisseph.integration.required";

    private static boolean nativeTestsAreRequired() {
        return Boolean.getBoolean(REQUIRED_PROPERTY);
    }

    /** Skips, or fails when the build declared the native suite mandatory. */
    private static void skipOrFail(String reason) {
        if (nativeTestsAreRequired()) {
            throw new IllegalStateException(reason + " (-D" + REQUIRED_PROPERTY
                    + " is set, so this must not be skipped)");
        }
        assumeTrue(false, reason);
    }

    private NativeTestSupport() {
    }

    static Optional<Path> library() {
        String configured = System.getProperty(LIBRARY_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            // Not the same thing as "no library configured". Treating a typo as
            // an absence would skip the whole native suite and still report
            // BUILD SUCCESS, which is exactly how untested bindings ship.
            throw new IllegalStateException(
                    LIBRARY_PROPERTY + " was set to " + path + ", which is not a file");
        }
        return Optional.of(path);
    }

    static Optional<Path> ephemerisDirectory() {
        String configured = System.getProperty(EPHEMERIS_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException(
                    EPHEMERIS_PROPERTY + " was set to " + path + ", which is not a directory");
        }
        return Optional.of(path);
    }

    /** Skips the calling test unless a native library was configured. */
    static Path requireLibrary() {
        Optional<Path> library = library();
        if (library.isEmpty()) {
            skipOrFail("Set -D" + LIBRARY_PROPERTY + "=<native-library> to run the native tests");
        }
        return library.orElseThrow();
    }

    /** Skips the calling test unless the Swiss Ephemeris data files were configured. */
    static Path requireEphemerisDirectory() {
        Optional<Path> directory = ephemerisDirectory();
        if (directory.isEmpty()) {
            skipOrFail("Set -D" + EPHEMERIS_PROPERTY + "=<ephe-directory> to run this test");
        }
        return directory.orElseThrow();
    }

    /** Skips unless the fixed-star catalogue is available. */
    static Path requireFixedStarCatalogue() {
        Path directory = requireEphemerisDirectory();
        if (!Files.isRegularFile(directory.resolve("sefstars.txt"))) {
            skipOrFail("sefstars.txt is missing from " + directory);
        }
        return directory;
    }

    /** Opens a handle, applying the ephemeris directory when one is configured. */
    static SwissEph open() {
        SwissEphConfig.Builder builder = SwissEphConfig.builder().library(requireLibrary());
        ephemerisDirectory().ifPresent(builder::ephemerisPath);
        return SwissEph.open(builder.build());
    }

    /** Opens a handle with no ephemeris path, so only Moshier is available. */
    static SwissEph openWithoutData() {
        return SwissEph.open(SwissEphConfig.builder().library(requireLibrary()).build());
    }

    /** Normalizes an angle into {@code [0, 360)}. */
    static double normalizeDegrees(double degrees) {
        double result = degrees % 360.0;
        return result < 0.0 ? result + 360.0 : result;
    }

    /** Shortest angular distance between two longitudes, in degrees. */
    static double angularDistance(double first, double second) {
        double difference = Math.abs(normalizeDegrees(first) - normalizeDegrees(second));
        return difference > 180.0 ? 360.0 - difference : difference;
    }

    /** Converts a count of minutes into a fraction of a day. */
    static double minutes(double count) {
        return count / (24.0 * 60.0);
    }
}
