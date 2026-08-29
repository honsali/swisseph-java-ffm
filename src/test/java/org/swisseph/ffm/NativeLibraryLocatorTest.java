package org.swisseph.ffm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The documented search order for the native library. */
class NativeLibraryLocatorTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty(NativeLibraryLocator.LIBRARY_PATH_PROPERTY);
        System.clearProperty(NativeLibraryLocator.LIBRARY_DIRECTORY_PROPERTY);
    }

    @Test
    void explicitPathWinsOverEveryOtherSource(@TempDir Path directory) throws IOException {
        Path preferred = Files.createFile(directory.resolve("preferred.so"));
        System.setProperty(NativeLibraryLocator.LIBRARY_PATH_PROPERTY, preferred.toString());

        NativeLibraryLocator.Resolution resolution = NativeLibraryLocator.locate().orElseThrow();
        assertEquals(preferred.toAbsolutePath().normalize(), resolution.path());
        assertTrue(resolution.source().contains(NativeLibraryLocator.LIBRARY_PATH_PROPERTY));
    }

    @Test
    void aDirectoryIsSearchedForThePlatformFileNames(@TempDir Path directory) throws IOException {
        String fileName = NativeLibraryLocator.platformFileNames().getFirst();
        Path library = Files.createFile(directory.resolve(fileName));
        System.setProperty(NativeLibraryLocator.LIBRARY_DIRECTORY_PROPERTY, directory.toString());

        NativeLibraryLocator.Resolution resolution = NativeLibraryLocator.locate().orElseThrow();
        assertEquals(library.toAbsolutePath().normalize(), resolution.path());
    }

    @Test
    void aPathThatIsNotAFileIsSkippedAndRecorded(@TempDir Path directory) {
        System.setProperty(NativeLibraryLocator.LIBRARY_PATH_PROPERTY,
                directory.resolve("absent.so").toString());

        List<String> attempts = new ArrayList<>();
        Optional<NativeLibraryLocator.Resolution> resolution = NativeLibraryLocator.locate(attempts);

        assertTrue(resolution.isEmpty()
                || !resolution.orElseThrow().path().toString().contains("absent.so"));
        assertTrue(attempts.stream().anyMatch(line -> line.contains("absent.so")),
                "the failed candidate must be reported: " + attempts);
    }

    @Test
    void aConfiguredDirectoryThatDoesNotExistIsReported(@TempDir Path directory) {
        // A typo here is the likeliest cause of a failed lookup, so it has to
        // show up in the diagnostics instead of being skipped silently.
        Path missing = directory.resolve("no-such-directory");
        System.setProperty(NativeLibraryLocator.LIBRARY_DIRECTORY_PROPERTY, missing.toString());

        List<String> attempts = new ArrayList<>();
        NativeLibraryLocator.locate(attempts);

        assertTrue(attempts.stream().anyMatch(line ->
                        line.contains("no-such-directory") && line.contains("not a directory")),
                "the bad directory must be reported: " + attempts);
    }

    @Test
    void theFailureMessageNamesEveryWayToFixIt() {
        List<String> attempts = List.of("/nowhere/libswe.so (from java.library.path): not found");
        String message = NativeLibraryLocator.describeFailure(attempts);

        assertTrue(message.contains(NativeLibraryLocator.LIBRARY_PATH_PROPERTY));
        assertTrue(message.contains(NativeLibraryLocator.LIBRARY_PATH_ENVIRONMENT));
        assertTrue(message.contains("/nowhere/libswe.so"));
    }

    @Test
    void platformFileNamesAreNonEmptyAndPlatformShaped() {
        List<String> names = NativeLibraryLocator.platformFileNames();
        assertFalse(names.isEmpty());
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String expectedSuffix = os.contains("win") ? ".dll" : os.contains("mac") ? ".dylib" : ".so";
        assertTrue(names.getFirst().endsWith(expectedSuffix),
                "unexpected first candidate for " + os + ": " + names);
    }

    @Test
    void buildingAConfigWithoutALibraryExplainsWhatWasTried() {
        // Nothing is configured in this test JVM beyond java.library.path, which on
        // a build machine never holds a Swiss Ephemeris binary.
        if (NativeLibraryLocator.locate().isPresent()) {
            return;
        }
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> SwissEphConfig.builder().build());
        assertTrue(failure.getMessage().contains(NativeLibraryLocator.LIBRARY_PATH_PROPERTY));
    }
}
