package org.swisseph.ffm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.swisseph.ffm.internal.NativeContext;

import java.nio.file.Path;
import java.util.Arrays;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
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
    @DisplayName("A failed local call still leaves Java and the library agreeing")
    void aFailedLocalCallLeavesTheObserverConsistent() {
        try (SwissEph swe = NativeTestSupport.open()) {
            GeographicPosition configured = GeographicPosition.of(2.3522, 48.8566);
            swe.setTopocentricObserver(configured);

            // The native routines check the altitude before they reach their own
            // swe_set_topo(), so this is refused before anything moves.
            assertThrows(IllegalArgumentException.class, () -> swe.solarEclipseHow(
                    J2000, CalculationFlag.MOSHIER_EPHEMERIS.value(),
                    new GeographicPosition(10.0, 40.0, 30_000.0)));

            assertEquals(configured, swe.settings().topocentricObserverIfSet().orElseThrow(),
                    "a refused call must not change what settings() reports");

            EphemerisPosition topocentric = swe.calculateUt(J2000, CelestialBody.MOON,
                    CalculationFlag.MOSHIER_EPHEMERIS, CalculationFlag.TOPOCENTRIC);
            assertTrue(topocentric.returnedFlags().has(CalculationFlag.TOPOCENTRIC));
        }
    }

    @Test
    @DisplayName("A twilight search accepts at most one definition of twilight")
    void twilightOptionsAreExclusive() {
        try (SwissEph swe = NativeTestSupport.open()) {
            GeographicPosition observer = GeographicPosition.of(2.3522, 48.8566);
            double start = swe.julianDay(2000, 3, 20, 0.0, CalendarType.GREGORIAN);

            // Upstream tests the three in a fixed order and takes the first, so a
            // combination silently answers a different question.
            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(
                    start, CelestialBody.SUN, observer, AtmosphericConditions.STANDARD,
                    RiseTransitFlag.RISE, RiseTransitFlag.CIVIL_TWILIGHT,
                    RiseTransitFlag.ASTRONOMICAL_TWILIGHT));

            assertTrue(swe.riseTransit(start, CelestialBody.SUN, observer,
                    AtmosphericConditions.STANDARD,
                    RiseTransitFlag.RISE, RiseTransitFlag.CIVIL_TWILIGHT).found());
        }
    }

    @Test
    @DisplayName("A library can be reopened straight after the last handle closed")
    void aLibraryCanBeReopenedImmediately() {
        Path library = NativeTestSupport.requireLibrary();

        // Teardown now runs off the registry lock, with a per-path gate holding
        // back only the acquires that target this same library.
        try (SwissEph first = SwissEph.open(library)) {
            assertEquals(1, first.handleCount());
        }
        try (SwissEph reopened = SwissEph.open(library)) {
            assertEquals(1, reopened.handleCount());
            assertFalse(reopened.version().isBlank());
        }
    }

    @Test
    @DisplayName("A native failure still leaves Java and the library agreeing on the observer")
    void aNativeFailureLeavesTheObserverConsistent() {
        try (SwissEph swe = NativeTestSupport.open()) {
            GeographicPosition configured = GeographicPosition.of(2.3522, 48.8566);
            swe.setTopocentricObserver(configured);

            // An unknown star makes swe_rise_trans() fail inside the call, after
            // the observer argument itself has passed every Java-side check. That
            // is the path where the restoration in the finally block matters.
            assertThrows(SwissEphException.class, () -> swe.riseTransit(J2000,
                    "NoSuchStarExistsAnywhere", CalculationFlag.MOSHIER_EPHEMERIS.value(),
                    RiseTransitFlag.RISE.value(), GeographicPosition.of(-149.9003, -17.5516),
                    AtmosphericConditions.STANDARD));

            assertEquals(configured, swe.settings().topocentricObserverIfSet().orElseThrow());

            // And the library agrees, not just the snapshot: a topocentric
            // position computed now must match one computed before the failure.
            EphemerisPosition moon = swe.calculateUt(J2000, CelestialBody.MOON,
                    CalculationFlag.MOSHIER_EPHEMERIS, CalculationFlag.TOPOCENTRIC);
            swe.setTopocentricObserver(configured);
            EphemerisPosition again = swe.calculateUt(J2000, CelestialBody.MOON,
                    CalculationFlag.MOSHIER_EPHEMERIS, CalculationFlag.TOPOCENTRIC);
            assertEquals(again, moon);
        }
    }

    @Test
    @DisplayName("A transit refuses the option that would skip its own swe_set_topo()")
    void transitsRefuseTheGeocentricOption() {
        try (SwissEph swe = NativeTestSupport.open()) {
            GeographicPosition observer = GeographicPosition.of(2.3522, 48.8566);

            // The bit sends the native code down the branch that never calls
            // swe_set_topo(), yet calc_mer_trans() then forces SEFLG_TOPOCTR.
            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(
                    J2000, CelestialBody.SUN, observer, AtmosphericConditions.STANDARD,
                    RiseTransitFlag.UPPER_MERIDIAN_TRANSIT,
                    RiseTransitFlag.GEOCENTRIC_NO_ECLIPTIC_LATITUDE));

            // It stays legal for a rise, which is the case it was designed for.
            assertTrue(swe.riseTransit(J2000, CelestialBody.SUN, observer,
                    AtmosphericConditions.STANDARD,
                    RiseTransitFlag.RISE,
                    RiseTransitFlag.GEOCENTRIC_NO_ECLIPTIC_LATITUDE).found());
        }
    }

    @Test
    @DisplayName("Twilight is refused where the native code would ignore it")
    void twilightIsRefusedWhereItIsIgnored() {
        try (SwissEph swe = NativeTestSupport.open()) {
            GeographicPosition observer = GeographicPosition.of(2.3522, 48.8566);

            // The twilight block is guarded by ipl == SE_SUN and sits after the
            // transit branch has already returned.
            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(
                    J2000, CelestialBody.MOON, observer, AtmosphericConditions.STANDARD,
                    RiseTransitFlag.RISE, RiseTransitFlag.CIVIL_TWILIGHT));
            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(
                    J2000, CelestialBody.SUN, observer, AtmosphericConditions.STANDARD,
                    RiseTransitFlag.UPPER_MERIDIAN_TRANSIT, RiseTransitFlag.CIVIL_TWILIGHT));

            // Upstream checks the disc centre first and never reaches the bottom.
            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(
                    J2000, CelestialBody.SUN, observer, AtmosphericConditions.STANDARD,
                    RiseTransitFlag.RISE, RiseTransitFlag.DISC_CENTER,
                    RiseTransitFlag.DISC_BOTTOM));

            // calc_mer_trans() reads rsmi only to tell an upper crossing from a
            // lower one, so everything about the disc and the atmosphere is
            // accepted and then dropped.
            for (RiseTransitFlag ignored : new RiseTransitFlag[] {
                    RiseTransitFlag.DISC_CENTER, RiseTransitFlag.DISC_BOTTOM,
                    RiseTransitFlag.NO_REFRACTION, RiseTransitFlag.FIXED_DISC_SIZE }) {
                assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(
                        J2000, CelestialBody.SUN, observer, AtmosphericConditions.STANDARD,
                        RiseTransitFlag.UPPER_MERIDIAN_TRANSIT, ignored),
                        ignored + " is ignored by a transit and must be refused");
            }
            assertTrue(swe.riseTransit(J2000, CelestialBody.SUN, observer,
                    AtmosphericConditions.STANDARD,
                    RiseTransitFlag.UPPER_MERIDIAN_TRANSIT).found());
        }
    }

    @Test
    @DisplayName("Delta T without a flag reproduces the ephemeris the library has open")
    void deltaTWithoutAFlagMatchesTheOpenEphemeris() {
        NativeTestSupport.requireEphemerisDirectory();
        try (SwissEph swe = NativeTestSupport.open()) {
            // Force the Swiss files open so swi_guess_ephe_flag() has something
            // to find, then compare against the explicit form.
            swe.calculateUt(J2000, CelestialBody.MOON, CalculationFlag.SWISS_EPHEMERIS);

            for (double julianDay : new double[] { J2000, 2_378_497.0, 2_086_308.0 }) {
                assertEquals(swe.deltaT(julianDay, Ephemeris.SWISS), swe.deltaT(julianDay), 1.0e-12,
                        "swe_deltat() derives the ephemeris itself; a flag of zero does not, "
                                + "and the two diverge for old dates at jd " + julianDay);
            }
        }
    }

    /** What the waiting thread saw, gathered before it let its context go. */
    private record GateEvidence(boolean sameInstanceAsClosing, int referenceCount,
                                double julianDay) {
    }

    @Test
    @DisplayName("An acquire during a teardown waits on the gate and then gets a fresh context")
    void acquireDuringTeardownWaitsForIt() throws Exception {
        Path library = NativeTestSupport.requireLibrary();
        NativeContext closing = NativeContext.acquire(library, version -> { });

        CountDownLatch inCall = new CountDownLatch(1);
        CountDownLatch letTheCallFinish = new CountDownLatch(1);
        AtomicReference<Object> seen = new AtomicReference<>();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Thread waiter = null;
        try {
            // Hold the native thread so the teardown cannot finish until we allow it.
            Future<?> slow = workers.submit(() -> closing.call(bindings -> {
                inCall.countDown();
                await(letTheCallFinish);
                return null;
            }));
            assertTrue(inCall.await(30, TimeUnit.SECONDS), "the slow call never started");

            Future<?> releasing = workers.submit(closing::release);
            awaitUntilClosed(closing);
            assertFalse(releasing.isDone(), "the teardown must still be waiting on the slow call");

            // The waiter owns whatever it acquires and hands back only evidence.
            waiter = new Thread(() -> {
                try {
                    NativeContext acquired = NativeContext.acquire(library, version -> { });
                    try {
                        seen.set(new GateEvidence(acquired == closing, acquired.referenceCount(),
                                acquired.call(bindings -> bindings.julianDay(
                                        2000, 1, 1, 12.0, CalendarType.GREGORIAN.value()))));
                    } finally {
                        acquired.release();
                    }
                } catch (Throwable thrown) {
                    seen.set(thrown);
                }
            }, "gate-waiter");
            // Daemon: a native load that hung after the gate opened would
            // otherwise hold the Maven fork open past every join.
            waiter.setDaemon(true);
            waiter.start();

            // Not "it has not finished yet", which would also be true of a thread
            // that had not started: park it on the gate and prove it is there.
            awaitParkedInAcquireGate(waiter);
            assertNull(seen.get(), "the acquire must not have completed while the gate is up");

            letTheCallFinish.countDown();
            slow.get(30, TimeUnit.SECONDS);
            releasing.get(30, TimeUnit.SECONDS);
            waiter.join(TimeUnit.SECONDS.toMillis(30));
            assertFalse(waiter.isAlive(), "the waiter never came back");

            Object outcome = seen.get();
            assertInstanceOf(GateEvidence.class, outcome, "the waiter failed: " + outcome);
            GateEvidence evidence = (GateEvidence) outcome;
            assertFalse(evidence.sameInstanceAsClosing(),
                    "the waiter must get a new context, not the one that was closing");
            assertEquals(1, evidence.referenceCount(),
                    "a fresh context, not one that overlapped with the closing one");
            assertEquals(2_451_545.0, evidence.julianDay(), 1.0e-9,
                    "and it must actually work, which a cached field would not prove");
        } finally {
            letTheCallFinish.countDown();
            if (waiter != null) {
                waiter.interrupt();
                waiter.join(TimeUnit.SECONDS.toMillis(30));
            }
            closing.release();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("A thread parked on the gate can be interrupted out of it")
    void aWaiterOnTheSamePathCanBeInterrupted() throws Exception {
        Path library = NativeTestSupport.requireLibrary();
        NativeContext closing = NativeContext.acquire(library, version -> { });

        CountDownLatch inCall = new CountDownLatch(1);
        CountDownLatch letTheCallFinish = new CountDownLatch(1);
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        AtomicBoolean interruptFlagRestored = new AtomicBoolean();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Thread waiter = null;
        try {
            Future<?> slow = workers.submit(() -> closing.call(bindings -> {
                inCall.countDown();
                await(letTheCallFinish);
                return null;
            }));
            assertTrue(inCall.await(30, TimeUnit.SECONDS));
            Future<?> releasing = workers.submit(closing::release);
            awaitUntilClosed(closing);

            waiter = new Thread(() -> {
                try {
                    NativeContext unexpected = NativeContext.acquire(library, version -> { });
                    unexpected.release();
                    outcome.set(new AssertionError("the acquire returned instead of unwinding"));
                } catch (Throwable thrown) {
                    outcome.set(thrown);
                    interruptFlagRestored.set(Thread.currentThread().isInterrupted());
                }
            }, "interrupted-waiter");
            waiter.setDaemon(true);
            waiter.start();

            // Interrupt only once it is demonstrably parked, so the test cannot
            // be satisfied by an interrupt that arrived before the wait began.
            awaitParkedInAcquireGate(waiter);
            waiter.interrupt();
            waiter.join(TimeUnit.SECONDS.toMillis(30));
            assertFalse(waiter.isAlive(), "an interrupted waiter must not stay on the gate");

            Throwable thrown = outcome.get();
            assertInstanceOf(SwissEphException.class, thrown,
                    "expected a SwissEphException but got " + thrown);
            assertInstanceOf(InterruptedException.class, thrown.getCause(),
                    "the interruption must be the stated cause");
            assertTrue(interruptFlagRestored.get(),
                    "the interrupt flag must be restored before unwinding");

            // The teardown itself was never abandoned.
            letTheCallFinish.countDown();
            slow.get(30, TimeUnit.SECONDS);
            releasing.get(30, TimeUnit.SECONDS);
            assertFalse(closing.isOpen());
        } finally {
            letTheCallFinish.countDown();
            if (waiter != null) {
                waiter.interrupt();
                waiter.join(TimeUnit.SECONDS.toMillis(30));
            }
            closing.release();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    /** Waits without letting an interrupt turn into a test failure. */
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for a test latch");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Waits until the teardown has been committed and the per-path gate is up. */
    private static void awaitUntilClosed(NativeContext context) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (context.isOpen() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertFalse(context.isOpen(), "the teardown never started");
    }

    /**
     * Waits until {@code thread} is actually parked on the per-path gate.
     *
     * <p>Signalling before the call and then checking that nothing has completed
     * proves nothing: the thread may simply not have been scheduled yet. The
     * only honest evidence is the thread sitting in
     * {@code NativeContext.acquire} inside {@code CountDownLatch.await}.</p>
     */
    private static void awaitParkedInAcquireGate(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.WAITING && isParkedInAcquireGate(thread)) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        fail("thread " + thread.getName() + " never parked on the acquire gate; state was "
                + thread.getState() + " and its stack was "
                + Arrays.toString(thread.getStackTrace()));
    }

    private static boolean isParkedInAcquireGate(Thread thread) {
        boolean insideAcquire = false;
        boolean waitingOnLatch = false;
        for (StackTraceElement frame : thread.getStackTrace()) {
            if (frame.getClassName().endsWith("NativeContext")
                    && "acquire".equals(frame.getMethodName())) {
                insideAcquire = true;
            }
            if (frame.getClassName().startsWith("java.util.concurrent.CountDownLatch")
                    && "await".equals(frame.getMethodName())) {
                waitingOnLatch = true;
            }
        }
        return insideAcquire && waitingOnLatch;
    }

    @Test
    @DisplayName("Rise and phenomena refuse the identifier that reads pla_diam[-1]")
    void negativeBodiesAreRefusedByRiseAndPhenomena() {
        try (SwissEph swe = NativeTestSupport.open()) {
            GeographicPosition observer = GeographicPosition.of(2.3522, 48.8566);

            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(J2000,
                    CelestialBody.ECLIPTIC_NUTATION, observer, AtmosphericConditions.STANDARD,
                    RiseTransitFlag.RISE));
            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(J2000, -1,
                    CalculationFlag.MOSHIER_EPHEMERIS.value(), RiseTransitFlag.RISE.value(),
                    observer, AtmosphericConditions.STANDARD));
            assertThrows(IllegalArgumentException.class,
                    () -> swe.phenomenaUt(J2000, CelestialBody.ECLIPTIC_NUTATION));

            // The same identifier stays legal where it actually means something.
            EphemerisPosition obliquity = swe.calculateUt(
                    J2000, CelestialBody.ECLIPTIC_NUTATION, CalculationFlag.MOSHIER_EPHEMERIS);
            assertTrue(obliquity.firstCoordinate() > 23.0 && obliquity.firstCoordinate() < 24.0,
                    "body -1 returns the true obliquity, which was 23.44 degrees at J2000");
        }
    }

    @Test
    @DisplayName("Disc options are refused where the native code would not apply them")
    void discOptionsAreRefusedWhereTheyAreIgnored() {
        NativeTestSupport.requireFixedStarCatalogue();
        try (SwissEph swe = NativeTestSupport.open()) {
            GeographicPosition observer = GeographicPosition.of(2.3522, 48.8566);
            int moshier = CalculationFlag.MOSHIER_EPHEMERIS.value();

            // FIXED_DISC_SIZE only rewrites the distance for the Sun and the Moon.
            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(J2000,
                    CelestialBody.MARS, observer, AtmosphericConditions.STANDARD,
                    RiseTransitFlag.RISE, RiseTransitFlag.FIXED_DISC_SIZE));
            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(J2000, "Sirius",
                    moshier,
                    RiseTransitFlag.mask(RiseTransitFlag.RISE, RiseTransitFlag.FIXED_DISC_SIZE),
                    observer, AtmosphericConditions.STANDARD));

            // A fixed star is given a disc radius of zero, so DISC_BOTTOM is a no-op.
            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(J2000, "Sirius",
                    moshier,
                    RiseTransitFlag.mask(RiseTransitFlag.RISE, RiseTransitFlag.DISC_BOTTOM),
                    observer, AtmosphericConditions.STANDARD));

            // DISC_CENTER already collapses the disc to a point.
            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(J2000,
                    CelestialBody.MOON, observer, AtmosphericConditions.STANDARD,
                    RiseTransitFlag.RISE, RiseTransitFlag.DISC_CENTER,
                    RiseTransitFlag.FIXED_DISC_SIZE));

            // A twilight search ORs in DISC_CENTER and NO_REFRACTION itself.
            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(J2000,
                    CelestialBody.SUN, observer, AtmosphericConditions.STANDARD,
                    RiseTransitFlag.RISE, RiseTransitFlag.CIVIL_TWILIGHT,
                    RiseTransitFlag.DISC_BOTTOM));

            // A fixed star is also given no disc centre to speak of.
            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(J2000, "Sirius",
                    moshier,
                    RiseTransitFlag.mask(RiseTransitFlag.RISE, RiseTransitFlag.DISC_CENTER),
                    observer, AtmosphericConditions.STANDARD));

            // pla_diam holds zero for the nodes and apogees, so both disc options
            // are no-ops there too.
            for (CelestialBody pointLike : new CelestialBody[] {
                    CelestialBody.MEAN_NODE, CelestialBody.TRUE_NODE,
                    CelestialBody.OSCULATING_APOGEE }) {
                assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(J2000,
                        pointLike, observer, AtmosphericConditions.STANDARD,
                        RiseTransitFlag.RISE, RiseTransitFlag.DISC_BOTTOM),
                        pointLike + " has no disc");
                assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(J2000,
                        pointLike, observer, AtmosphericConditions.STANDARD,
                        RiseTransitFlag.RISE, RiseTransitFlag.DISC_CENTER),
                        pointLike + " has no disc");
            }

            // The Earth is not a target at all: swe_calc() zeroes it geocentrically.
            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(J2000,
                    CelestialBody.EARTH, observer, AtmosphericConditions.STANDARD,
                    RiseTransitFlag.RISE));
            assertThrows(IllegalArgumentException.class, () -> swe.riseTransit(J2000,
                    CelestialBody.EARTH.id(), moshier, RiseTransitFlag.RISE.value(),
                    observer, AtmosphericConditions.STANDARD));

            // What remains legal still works, disc and all.
            assertTrue(swe.riseTransit(J2000, CelestialBody.SUN, observer,
                    AtmosphericConditions.STANDARD,
                    RiseTransitFlag.RISE, RiseTransitFlag.FIXED_DISC_SIZE).found());
            assertTrue(swe.riseTransit(J2000, CelestialBody.MEAN_NODE, observer,
                    AtmosphericConditions.STANDARD, RiseTransitFlag.RISE).found());
            assertTrue(swe.riseTransit(J2000, CelestialBody.MARS, observer,
                    AtmosphericConditions.STANDARD,
                    RiseTransitFlag.RISE, RiseTransitFlag.DISC_BOTTOM).found());
        }
    }

    @Test
    @DisplayName("A sidereal reference epoch is refused for a mode that would drop it")
    void siderealReferenceEpochIsRefusedOutsideUserMode() {
        try (SwissEph swe = NativeTestSupport.open()) {
            assertThrows(IllegalArgumentException.class,
                    () -> swe.setSiderealMode(SiderealMode.LAHIRI.value(), 2_451_545.0, 24.0));
            assertThrows(IllegalArgumentException.class, () -> swe.setSiderealMode(
                    SiderealMode.LAHIRI.value() | SiderealOption.ORIGINAL_PRECESSION.value(),
                    0.0, 0.0));

            // A user-defined ayanamsha does take them, and gives them back exactly
            // at t0 -- but only when the epoch and the time scale line up.
            //
            // swi_get_ayanamsa_ex() precesses the vernal point from tjd_et to
            // J2000 and then to t0; when the two coincide the precessions cancel
            // and the result is ayan_t0 to the last bit. get_aya_correction()
            // returns 0 outright for t0 == J2000. What remains is nutation, which
            // swe_get_ayanamsa_ex() adds unless NO_NUTATION is set.
            //
            // USER reads t0 as ephemeris time by default, so it is the ET entry
            // point that lands on it.
            swe.setSiderealMode(SiderealMode.USER.value(), J2000, 24.0);
            assertEquals(24.0, swe.ayanamsa(J2000,
                            CalculationFlag.MOSHIER_EPHEMERIS, CalculationFlag.NO_NUTATION),
                    1.0e-9, "at t0 in ET the ayanamsha is exactly the value supplied");

            // With USER_T0_IN_UT the epoch is universal time, and it is then the
            // UT entry point that lands on it: both sides add the same delta T.
            swe.setSiderealMode(
                    SiderealMode.USER.value() | SiderealOption.USER_T0_IN_UT.value(),
                    J2000, 24.0);
            assertEquals(24.0, swe.ayanamsaUt(J2000,
                            CalculationFlag.MOSHIER_EPHEMERIS, CalculationFlag.NO_NUTATION),
                    1.0e-9, "at t0 in UT the ayanamsha is exactly the value supplied");

            // Nutation is the whole of the difference at this epoch.
            double withNutation = swe.ayanamsaUt(J2000, CalculationFlag.MOSHIER_EPHEMERIS);
            assertNotEquals(24.0, withNutation);
            assertEquals(24.0, withNutation, 0.01,
                    "nutation in longitude stays well under a hundredth of a degree");
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
