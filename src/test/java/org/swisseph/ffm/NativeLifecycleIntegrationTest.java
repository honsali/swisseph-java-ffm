package org.swisseph.ffm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.swisseph.ffm.internal.NativeContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The execution model and the lifecycle, which is where the portability
 * problems live.
 */
class NativeLifecycleIntegrationTest {

    private static final double J2000 = 2_451_545.0;

    @Test
    @DisplayName("Configuration applied on one thread is visible to calls made from any other")
    void configurationCrossesThreadsIntact() throws Exception {
        Path ephemeris = NativeTestSupport.requireEphemerisDirectory();
        try (SwissEph swe = NativeTestSupport.open()) {
            // This is the test the dedicated native thread exists for. Swiss
            // Ephemeris stores swed in thread-local memory on GCC, Clang and MSVC
            // builds, so a library configured on the main thread would look
            // unconfigured to a worker thread, silently downgrading it to Moshier.
            // Everything is funnelled onto one thread precisely so that cannot
            // happen, and the observable proof is that the ephemeris the library
            // reports is the same everywhere.
            swe.setEphemerisPath(ephemeris);
            EphemerisPosition reference =
                    swe.calculateUt(J2000, CelestialBody.SUN, CalculationFlag.SWISS_EPHEMERIS);
            assertTrue(reference.returnedFlags().used(Ephemeris.SWISS),
                    "the reference call itself must not be downgraded: " + reference.warning());

            List<Callable<EphemerisPosition>> calls = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                calls.add(() -> swe.calculateUt(
                        J2000, CelestialBody.SUN, CalculationFlag.SWISS_EPHEMERIS));
            }

            ExecutorService workers = Executors.newFixedThreadPool(8);
            try {
                for (Future<EphemerisPosition> result : workers.invokeAll(calls)) {
                    EphemerisPosition fromWorker = result.get();
                    assertTrue(fromWorker.returnedFlags().used(Ephemeris.SWISS),
                            "a worker thread saw an unconfigured library: " + fromWorker.warning());
                    assertEquals(reference, fromWorker,
                            "every thread must observe the same configured library");
                }
            } finally {
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    @DisplayName("Concurrent callers get consistent results without corrupting each other")
    void concurrentCallersStayConsistent() throws Exception {
        NativeTestSupport.requireLibrary();
        try (SwissEph swe = NativeTestSupport.open()) {
            EphemerisPosition reference =
                    swe.calculateUt(J2000, CelestialBody.MARS, CalculationFlag.MOSHIER_EPHEMERIS);

            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int worker = 0; worker < 16; worker++) {
                int seed = worker;
                calls.add(() -> {
                    for (int round = 0; round < 40; round++) {
                        // Interleave settings changes with calculations, which is
                        // exactly the pattern that corrupts an unsynchronised binding.
                        if ((seed + round) % 7 == 0) {
                            swe.setTopocentricObserver(GeographicPosition.of(seed, round % 60));
                        }
                        EphemerisPosition mars = swe.calculateUt(
                                J2000, CelestialBody.MARS, CalculationFlag.MOSHIER_EPHEMERIS);
                        if (!reference.equals(mars)) {
                            return false;
                        }
                    }
                    return true;
                });
            }

            ExecutorService workers = Executors.newFixedThreadPool(16);
            try {
                for (Future<Boolean> result : workers.invokeAll(calls)) {
                    assertTrue(result.get(), "a concurrent caller observed a different position");
                }
            } finally {
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    @DisplayName("Closing one handle leaves the others working")
    void closingOneHandleDoesNotDisturbTheOthers() {
        Path library = NativeTestSupport.requireLibrary();

        SwissEph first = SwissEph.open(library);
        SwissEph second = SwissEph.open(library);
        try {
            assertEquals(2, first.handleCount());
            assertEquals(2, second.handleCount());

            first.close();

            // Before reference counting, this is where the second handle broke:
            // the first one had already called swe_close() and unloaded the library.
            assertFalse(first.isOpen());
            assertTrue(second.isOpen());
            assertEquals(1, second.handleCount());
            assertEquals(2_451_545.0,
                    second.julianDay(2000, 1, 1, 12.0, CalendarType.GREGORIAN), 1.0e-9);
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    @DisplayName("Handles on the same file share one native context")
    void handlesOnTheSameFileShareOneContext() {
        Path library = NativeTestSupport.requireLibrary();

        try (SwissEph first = SwissEph.open(library);
             SwissEph second = SwissEph.open(library)) {
            // Settings live in the C library, so they are shared whether we like it
            // or not. Making that visible is the point of settings().
            first.setJplFile("de441.eph");
            assertEquals("de441.eph", second.settings().jplFileIfSet().orElseThrow());
            assertEquals(first.libraryPath(), second.libraryPath());
        }
    }

    @Test
    @DisplayName("Closing is idempotent and a closed handle refuses to work")
    void closedHandlesRefuseToWork() {
        Path library = NativeTestSupport.requireLibrary();

        SwissEph swe = SwissEph.open(library);
        swe.close();
        swe.close();

        assertFalse(swe.isOpen());
        assertThrows(IllegalStateException.class,
                () -> swe.julianDay(2000, 1, 1, 12.0, CalendarType.GREGORIAN));
        assertThrows(IllegalStateException.class, swe::version);
    }

    @Test
    @DisplayName("Reopening after a full close loads the library again")
    void reopeningAfterAFullCloseWorks() {
        Path library = NativeTestSupport.requireLibrary();

        String firstVersion;
        try (SwissEph swe = SwissEph.open(library)) {
            firstVersion = swe.version();
            assertEquals(1, swe.handleCount());
        }
        try (SwissEph swe = SwissEph.open(library)) {
            assertEquals(firstVersion, swe.version());
            assertEquals(1, swe.handleCount());
        }
    }

    @Test
    @DisplayName("An unsupported native version is refused before anything is computed")
    void unsupportedVersionsAreRefused() {
        Path library = NativeTestSupport.requireLibrary();

        SwissEphConfig rejecting = SwissEphConfig.builder()
                .library(library)
                .supportedVersions("0.0.0-not-a-real-version")
                .build();

        IllegalStateException refusal =
                assertThrows(IllegalStateException.class, () -> SwissEph.open(rejecting));
        assertTrue(refusal.getMessage().contains("does not support"),
                "the refusal must explain itself: " + refusal.getMessage());

        // The same library opens once the caller says the build is acceptable.
        SwissEphConfig accepting = SwissEphConfig.builder()
                .library(library)
                .supportedVersions("0.0.0-not-a-real-version")
                .versionPolicy(NativeVersionPolicy.ACCEPT)
                .build();
        try (SwissEph swe = SwissEph.open(accepting)) {
            assertFalse(swe.version().isBlank());
        }
    }

    @Test
    @DisplayName("A rejected version leaves no library loaded behind")
    void aRejectedVersionLeavesNothingLoaded() {
        Path library = NativeTestSupport.requireLibrary();

        assertThrows(IllegalStateException.class, () -> SwissEph.open(SwissEphConfig.builder()
                .library(library)
                .supportedVersions("0.0.0-not-a-real-version")
                .build()));

        // If the failed attempt had leaked a context, this handle would report two.
        try (SwissEph swe = SwissEph.open(library)) {
            assertEquals(1, swe.handleCount());
        }
    }

    @Test
    @DisplayName("The reported version is one this binding was written against")
    void reportedVersionIsSupported() {
        try (SwissEph swe = NativeTestSupport.open()) {
            assertTrue(SwissEphConfig.DEFAULT_SUPPORTED_VERSIONS.contains(swe.version()),
                    "tested against " + swe.version() + ", expected one of "
                            + SwissEphConfig.DEFAULT_SUPPORTED_VERSIONS);
            assertFalse(swe.nativeLibraryPath().isBlank(),
                    "swe_get_library_path() should report where the binary came from");
        }
    }

    @Test
    @DisplayName("Configured settings are applied by open, not left to the caller")
    void configuredSettingsAreAppliedOnOpen() {
        Path ephemeris = NativeTestSupport.requireEphemerisDirectory();

        SwissEphConfig config = SwissEphConfig.builder()
                .library(NativeTestSupport.requireLibrary())
                .ephemerisPath(ephemeris)
                .siderealMode(SiderealMode.LAHIRI)
                .build();

        try (SwissEph swe = SwissEph.open(config)) {
            SwissEphSettings settings = swe.settings();
            assertEquals(ephemeris.toString(), settings.ephemerisPathIfSet().orElseThrow());
            assertEquals(SiderealMode.LAHIRI, settings.siderealAyanamsa().orElseThrow());

            // Applied means applied: a sidereal calculation works with no further setup.
            EphemerisPosition sun = swe.calculateUt(J2000, CelestialBody.SUN,
                    CalculationFlag.SWISS_EPHEMERIS, CalculationFlag.SIDEREAL);
            assertTrue(sun.returnedFlags().has(CalculationFlag.SIDEREAL));
        }
    }

    @Test
    @DisplayName("An over-long ephemeris path is refused rather than silently ignored")
    void overLongEphemerisPathsAreRefused() {
        try (SwissEph swe = NativeTestSupport.open()) {
            String tooLong = "/" + "x".repeat(SwissEph.MAX_EPHEMERIS_PATH_BYTES);
            // swe_set_ephe_path() would substitute its compiled-in default here and
            // then quietly read files from somewhere else entirely.
            assertThrows(IllegalArgumentException.class, () -> swe.setEphemerisPath(tooLong));
            assertThrows(IllegalArgumentException.class,
                    () -> swe.setEphemerisPath("/ephe\0/hidden"));
        }
    }

    @Test
    @DisplayName("Sunshine houses from an ARMC demand the Sun's declination")
    void sunshineHousesFromArmcDemandTheDeclination() {
        try (SwissEph swe = NativeTestSupport.open()) {
            // swe_houses_armc() reads ascmc[9] as an input for the Sunshine
            // systems. An unset buffer holds 0.0, which the library would accept
            // as a real declination and turn into plausible but wrong cusps, so
            // the four-argument form refuses the request outright.
            for (HouseSystem sunshine : new HouseSystem[] {
                    HouseSystem.SUNSHINE_TREINDL, HouseSystem.SUNSHINE_MAKRANSKY }) {
                assertTrue(sunshine.needsSolarDeclination());
                IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                        () -> swe.housesFromArmc(100.0, 48.0, 23.44, sunshine));
                assertTrue(refusal.getMessage().contains("declination"));

                assertThrows(IllegalArgumentException.class,
                        () -> swe.housesFromArmc(100.0, 48.0, 23.44, sunshine, 25.0));
                assertThrows(IllegalArgumentException.class,
                        () -> swe.housesFromArmc(100.0, 48.0, 23.44, sunshine, Double.NaN));

                HouseCusps cusps = swe.housesFromArmc(100.0, 48.0, 23.44, sunshine, 20.0);
                assertTrue(cusps.requestedSystemUsed());
                assertTrue(Double.isFinite(cusps.cusp(1)));
            }
        }
    }

    @Test
    @DisplayName("The declination actually changes the Sunshine cusps")
    void theDeclinationReachesTheSunshineCalculation() {
        try (SwissEph swe = NativeTestSupport.open()) {
            // If ascmc[9] were not being passed through, these two would agree.
            HouseCusps summer = swe.housesFromArmc(
                    100.0, 48.0, 23.44, HouseSystem.SUNSHINE_TREINDL, 23.0);
            HouseCusps winter = swe.housesFromArmc(
                    100.0, 48.0, 23.44, HouseSystem.SUNSHINE_TREINDL, -23.0);

            // Compare the whole set rather than one nominated cusp: the claim is
            // that the declination reaches the calculation at all, not which
            // particular cusp it moves.
            assertFalse(java.util.Arrays.equals(summer.cusps(), winter.cusps()),
                    "the Sun's declination must reach the Sunshine house calculation");
        }
    }

    @Test
    @DisplayName("A local eclipse call leaves the topocentric observer where it found it")
    void localCallsRestoreTheTopocentricObserver() {
        try (SwissEph swe = NativeTestSupport.open()) {
            GeographicPosition configured = GeographicPosition.of(2.3522, 48.8566);
            swe.setTopocentricObserver(configured);

            EphemerisPosition before = swe.calculateUt(J2000, CelestialBody.MOON,
                    CalculationFlag.MOSHIER_EPHEMERIS, CalculationFlag.TOPOCENTRIC);

            // swe_sol_eclipse_how(), the local searches and swe_rise_trans() all
            // call swe_set_topo() themselves. Without restoration, this far-away
            // observer would silently become the one every later topocentric
            // calculation uses.
            GeographicPosition elsewhere = GeographicPosition.of(-149.9003, -17.5516);
            swe.solarEclipseHow(J2000, CalculationFlag.MOSHIER_EPHEMERIS.value(), elsewhere);
            swe.lunarEclipseHow(J2000, CalculationFlag.MOSHIER_EPHEMERIS.value(), elsewhere);
            swe.riseTransit(J2000, CelestialBody.SUN, elsewhere,
                    AtmosphericConditions.STANDARD, RiseTransitFlag.RISE);

            EphemerisPosition after = swe.calculateUt(J2000, CelestialBody.MOON,
                    CalculationFlag.MOSHIER_EPHEMERIS, CalculationFlag.TOPOCENTRIC);

            assertEquals(before, after,
                    "a local eclipse or rise call must not re-aim later topocentric work");
            assertEquals(configured, swe.settings().topocentricObserverIfSet().orElseThrow());
        }
    }

    @Test
    @DisplayName("With no observer configured, settings() records the one the library kept")
    void withoutAConfiguredObserverTheSnapshotStaysTruthful() {
        try (SwissEph swe = NativeTestSupport.openWithoutData()) {
            assertTrue(swe.settings().topocentricObserverIfSet().isEmpty());

            GeographicPosition used = GeographicPosition.of(-149.9003, -17.5516);
            swe.solarEclipseHow(J2000, CalculationFlag.MOSHIER_EPHEMERIS.value(), used);

            // There is no way to un-set an observer in the C library, so the honest
            // move is to report the one it now holds rather than keep claiming none.
            assertEquals(used, swe.settings().topocentricObserverIfSet().orElseThrow());
        }
    }

    @Test
    @DisplayName("Sidereal and ayanamsha names refuse arguments that read out of bounds")
    void siderealArgumentsAreBounded() {
        try (SwissEph swe = NativeTestSupport.open()) {
            // swe_get_ayanamsa_name() reduces with isidmode %= 256 and then checks
            // only the upper bound, so -1 would index ayanamsa_name[-1].
            assertThrows(IllegalArgumentException.class, () -> swe.ayanamsaName(-1));
            assertThrows(IllegalArgumentException.class, () -> swe.ayanamsaName(Integer.MIN_VALUE));
            assertThrows(IllegalArgumentException.class,
                    () -> swe.setSiderealMode(-1, 0.0, 0.0));
            // USER defines its ayanamsha from t0 and the offset, which the enum
            // overload has no way to supply.
            assertThrows(IllegalArgumentException.class,
                    () -> swe.setSiderealMode(SiderealMode.USER));

            // A mode above the table is simply unknown, not dangerous.
            assertEquals("", swe.ayanamsaName(60));
        }
    }

    @Test
    @DisplayName("A long library path does not overflow the buffer we hand the library")
    void libraryPathFitsInItsBuffer() {
        try (SwissEph swe = NativeTestSupport.open()) {
            // swe_get_library_path() writes its terminator at index AS_MAXCH, one
            // past a 256-byte allocation. If the buffer were still that size this
            // would corrupt the arena rather than return a string.
            String path = swe.nativeLibraryPath();
            assertNotNull(path);
            assertTrue(path.length() <= 256, "unexpected length " + path.length());
        }
    }

    @Test
    @DisplayName("Sunshine house positions are refused rather than served from stale state")
    void sunshineHousePositionsAreRefused() {
        try (SwissEph swe = NativeTestSupport.open()) {
            for (HouseSystem sunshine : new HouseSystem[] {
                    HouseSystem.SUNSHINE_TREINDL, HouseSystem.SUNSHINE_MAKRANSKY }) {
                // swe_house_pos() writes the ascmc[9] == 99 sentinel and the library
                // then reuses a declination cached from an earlier, unrelated call.
                assertThrows(IllegalArgumentException.class,
                        () -> swe.housePosition(100.0, 48.0, 23.44, sunshine, 120.0, 0.0));
            }
            assertTrue(swe.housePosition(100.0, 48.0, 23.44, HouseSystem.PLACIDUS, 120.0, 0.0) > 0);
        }
    }

    @Test
    @DisplayName("Star names are bounded before they reach the native buffer")
    void starNamesAreBounded() {
        try (SwissEph swe = NativeTestSupport.open()) {
            assertThrows(IllegalArgumentException.class,
                    () -> swe.fixedStar2Ut(J2000, "x".repeat(300)));
            assertThrows(IllegalArgumentException.class, () -> swe.fixedStar2Ut(J2000, "  "));
            assertThrows(IllegalArgumentException.class,
                    () -> swe.fixedStar2Ut(J2000, "Sirius\0extra"));
            assertThrows(NullPointerException.class, () -> swe.fixedStar2Ut(J2000, null));
        }
    }

    @Test
    @DisplayName("Native failures surface as SwissEphException with the C diagnostics")
    void nativeFailuresCarryTheirDiagnostics() {
        try (SwissEph swe = NativeTestSupport.open()) {
            SwissEphException failure = assertThrows(SwissEphException.class,
                    () -> swe.fixedStar2Ut(J2000, "NoSuchStarExistsAnywhere"));

            assertEquals("swe_fixstar2_ut", failure.function());
            assertTrue(failure.returnCode() < 0);
            assertFalse(failure.nativeMessage().isBlank(),
                    "the contents of serr must reach the caller");
        }
    }

    @Test
    @DisplayName("A failure raised on the native thread still names the calling thread")
    void failuresIdentifyTheCallingThread() {
        try (SwissEph swe = NativeTestSupport.open()) {
            SwissEphException failure = assertThrows(SwissEphException.class,
                    () -> swe.fixedStar2Ut(J2000, "NoSuchStarExistsAnywhere"));

            // Running everything on one thread would otherwise leave the caller with
            // a stack trace containing nothing but swisseph-native-1 frames.
            List<String> suppressed = new ArrayList<>();
            for (Throwable marker : failure.getSuppressed()) {
                suppressed.add(marker.getMessage());
            }
            assertTrue(suppressed.stream().anyMatch(
                            message -> message != null && message.contains("dispatched from thread")),
                    "expected the caller's dispatch site to be attached, got " + suppressed);
        }
    }

    @Test
    @DisplayName("Closing waits for a native call that is already in flight")
    void closingWaitsForCallsAlreadyInFlight() throws Exception {
        Path library = NativeTestSupport.requireLibrary();
        NativeContext context = NativeContext.acquire(library, version -> { });

        CountDownLatch started = new CountDownLatch(1);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<String> slow = caller.submit(() -> context.call(bindings -> {
                started.countDown();
                try {
                    // Occupy the native thread long enough for the close below to
                    // land in the middle of this call.
                    Thread.sleep(750);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return "finished";
            }));
            assertTrue(started.await(30, TimeUnit.SECONDS), "the slow call never started");

            // Releases the last handle, so this tears the context down. It must
            // not unload the library from under the call still running on it.
            context.release();

            assertEquals("finished", slow.get(30, TimeUnit.SECONDS),
                    "an in-flight call must complete rather than be torn out from under");
            assertFalse(context.isOpen());
        } finally {
            caller.shutdownNow();
            assertTrue(caller.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("Calls racing a close either succeed or fail cleanly, never crash")
    void callsRacingACloseFailCleanly() throws Exception {
        Path library = NativeTestSupport.requireLibrary();

        // Repeated so the close lands at varying points relative to the callers.
        for (int attempt = 0; attempt < 20; attempt++) {
            NativeContext context = NativeContext.acquire(library, version -> { });
            CountDownLatch release = new CountDownLatch(1);
            List<Callable<Boolean>> callers = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                callers.add(() -> {
                    release.await();
                    for (int round = 0; round < 25; round++) {
                        try {
                            String version = context.call(bindings -> {
                                try (java.lang.foreign.Arena arena =
                                             java.lang.foreign.Arena.ofConfined()) {
                                    var buffer = arena.allocate(256);
                                    bindings.version(buffer);
                                    return buffer.getString(0);
                                }
                            });
                            // A call that got through must have produced a real
                            // answer, not garbage read out of freed memory.
                            if (version == null || version.isBlank()) {
                                return false;
                            }
                        } catch (IllegalStateException expected) {
                            // The context closed underneath us. This is the only
                            // failure mode a caller should ever observe.
                            return true;
                        }
                    }
                    return true;
                });
            }

            ExecutorService workers = Executors.newFixedThreadPool(8);
            try {
                List<Future<Boolean>> results = new ArrayList<>();
                for (Callable<Boolean> call : callers) {
                    results.add(workers.submit(call));
                }
                release.countDown();
                context.release();
                for (Future<Boolean> result : results) {
                    assertTrue(result.get(60, TimeUnit.SECONDS),
                            "a caller racing the close saw something other than a clean refusal");
                }
            } finally {
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS));
            }
            assertFalse(context.isOpen());
        }
    }

    @Test
    @DisplayName("Every native call runs on the same dedicated platform thread")
    void everyNativeCallRunsOnOneDedicatedThread() throws Exception {
        Path library = NativeTestSupport.requireLibrary();

        // Reaching into the internal package is only possible from inside the
        // module, which is the point: application code cannot do this, but the
        // test can ask the native thread to identify itself.
        NativeContext context = NativeContext.acquire(library, version -> { });
        try {
            String mainThreadObservation =
                    context.call(bindings -> Thread.currentThread().getName());
            assertTrue(mainThreadObservation.startsWith("swisseph-native-"),
                    "native calls must not run on the caller thread, but ran on "
                            + mainThreadObservation);
            assertFalse(mainThreadObservation.equals(Thread.currentThread().getName()));

            List<Callable<String>> calls = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                calls.add(() -> context.call(bindings -> Thread.currentThread().getName()));
            }

            ExecutorService workers = Executors.newFixedThreadPool(8);
            Set<String> observed = Collections.synchronizedSet(new java.util.HashSet<>());
            try {
                for (Future<String> result : workers.invokeAll(calls)) {
                    observed.add(result.get());
                }
            } finally {
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS));
            }

            assertEquals(Set.of(mainThreadObservation), observed,
                    "all eight callers must land on the one thread that owns swed");
            assertTrue(context.isOpen());
        } finally {
            context.release();
        }
    }
}
