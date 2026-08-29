package org.swisseph.ffm.internal;

import org.swisseph.ffm.SwissEphException;
import org.swisseph.ffm.SwissEphSettings;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Owns one loaded Swiss Ephemeris library, the single thread that is allowed to
 * touch it, and the reference count that decides when it may be unloaded.
 *
 * <h2>Why a dedicated thread</h2>
 * <p>Swiss Ephemeris declares its entire state as
 * {@code extern TLS struct swe_data swed}, where {@code TLS} expands to
 * {@code __thread} on GCC and Clang and to {@code __declspec(thread)} on MSVC.
 * The ephemeris path, the JPL file name, the topocentric observer, the sidereal
 * mode and every open file handle therefore live in <em>thread-local</em>
 * storage on the usual builds, and in process-global storage on builds where
 * {@code TLS} expands to nothing. Configuring from one thread and calculating
 * from another silently reads a different {@code swed} on the first kind of
 * build and the same one on the second: the behaviour is not portable.</p>
 *
 * <p>Pinning every call to one platform thread makes the two cases
 * indistinguishable, which is the only portable contract available. It also
 * subsumes the serialization that the library needs anyway, so no additional
 * lock is required around the native calls. A platform thread, not a virtual
 * one: the native code blocks on file I/O and would pin its carrier.</p>
 *
 * <h2>Why reference counting</h2>
 * <p>{@code swe_close()} frees the state shared by everything running against
 * the library. If each facade closed the library on its own, one caller closing
 * its handle would break every other caller. Contexts are therefore shared per
 * library path and torn down only when the last handle is released.</p>
 */
public final class NativeContext {
    private static final ReentrantLock REGISTRY_LOCK = new ReentrantLock();
    private static final Map<Path, NativeContext> REGISTRY = new HashMap<>();
    /**
     * Paths currently being loaded or torn down, each with a latch that opens
     * when the work is finished.
     *
     * <p>This exists so neither operation has to run under
     * {@link #REGISTRY_LOCK}. Holding the global lock across a native call would
     * let one stuck downcall freeze every acquire and release in the process,
     * including for unrelated libraries; dropping the lock without a gate would
     * let a second context be loaded for a path whose {@code swe_close()} is
     * still running, which on a build without thread-local state would wipe the
     * new context's {@code swed}.</p>
     */
    private static final Map<Path, CountDownLatch> IN_FLIGHT = new HashMap<>();
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30L;

    private final Path libraryPath;
    private final Arena arena;
    private final ExecutorService executor;
    private final NativeBindings bindings;
    private final String nativeVersion;

    /**
     * Serializes handing work to the native thread against tearing the context
     * down. Held only while submitting, never while waiting for a result.
     *
     * <p>Lock order is {@link #REGISTRY_LOCK} then this one; nothing ever takes
     * them the other way round.</p>
     */
    private final ReentrantLock submitLock = new ReentrantLock();

    /** Guarded by {@link #REGISTRY_LOCK}. */
    private int referenceCount;
    private volatile boolean closed;
    private volatile SwissEphSettings settings = SwissEphSettings.EMPTY;

    private NativeContext(Path libraryPath, Arena arena, ExecutorService executor,
                          NativeBindings bindings, String nativeVersion) {
        this.libraryPath = libraryPath;
        this.arena = arena;
        this.executor = executor;
        this.bindings = bindings;
        this.nativeVersion = nativeVersion;
    }

    /**
     * Returns the context for {@code libraryPath}, loading the library if this is
     * the first handle, and increments its reference count.
     *
     * @param versionValidator invoked with the value of {@code swe_version()};
     *                         it should throw to reject an unsupported build
     */
    public static NativeContext acquire(Path libraryPath, Consumer<String> versionValidator) {
        Objects.requireNonNull(libraryPath, "libraryPath");
        Objects.requireNonNull(versionValidator, "versionValidator");
        Path key = canonicalize(libraryPath);

        while (true) {
            CountDownLatch busy;
            REGISTRY_LOCK.lock();
            try {
                NativeContext existing = REGISTRY.get(key);
                if (existing != null) {
                    // Validate before handing out the handle, so a stricter caller
                    // cannot silently inherit a build it would have rejected.
                    versionValidator.accept(existing.nativeVersion);
                    existing.referenceCount++;
                    return existing;
                }
                busy = IN_FLIGHT.get(key);
                if (busy == null) {
                    // Claim the path, then do the slow work with the lock released.
                    IN_FLIGHT.put(key, new CountDownLatch(1));
                    break;
                }
            } finally {
                REGISTRY_LOCK.unlock();
            }
            // Another thread is loading or closing this library. Wait for it off
            // the lock, then look again.
            awaitUninterruptibly(busy);
        }

        NativeContext created;
        try {
            created = load(key);
        } catch (RuntimeException | Error failure) {
            openGate(key);
            throw failure;
        }
        try {
            versionValidator.accept(created.nativeVersion);
        } catch (RuntimeException | Error rejected) {
            try {
                created.tearDown();
            } finally {
                openGate(key);
            }
            throw rejected;
        }

        REGISTRY_LOCK.lock();
        try {
            created.referenceCount = 1;
            REGISTRY.put(key, created);
        } finally {
            REGISTRY_LOCK.unlock();
        }
        openGate(key);
        return created;
    }

    private static void openGate(Path key) {
        CountDownLatch gate;
        REGISTRY_LOCK.lock();
        try {
            gate = IN_FLIGHT.remove(key);
        } finally {
            REGISTRY_LOCK.unlock();
        }
        if (gate != null) {
            gate.countDown();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch gate) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    gate.await();
                    return;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Path canonicalize(Path libraryPath) {
        Path absolute = libraryPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new IllegalArgumentException(
                    "Swiss Ephemeris native library does not exist: " + absolute);
        }
        try {
            return absolute.toRealPath();
        } catch (java.io.IOException ignored) {
            // A symlink we cannot resolve only costs us registry sharing, not correctness.
            return absolute;
        }
    }

    @SuppressWarnings("restricted") // libraryLookup is the whole point of this class
    private static NativeContext load(Path libraryPath) {
        ThreadFactory factory = Thread.ofPlatform()
                .name("swisseph-native-" + THREAD_COUNTER.incrementAndGet())
                .daemon(true)
                .factory();
        ExecutorService executor = Executors.newSingleThreadExecutor(factory);
        Arena arena = Arena.ofShared();
        try {
            // The library is opened on the native thread too: on Windows the loader
            // runs DllMain there, and thread-local state must be initialised by the
            // same thread that will later use it.
            //
            // Uninterruptible, for the same reason the teardown is: giving up on
            // the wait would drop us into the failure path below, which unloads
            // the library while the call that is still running holds it.
            NativeBindings bindings = awaitUninterruptibly(submit(executor, () -> {
                SymbolLookup symbols = SymbolLookup.libraryLookup(libraryPath, arena);
                return new NativeBindings(symbols);
            }));
            String version = awaitUninterruptibly(submit(executor, () -> {
                try (Arena call = Arena.ofConfined()) {
                    MemorySegment buffer = call.allocate(NativeBindings.TEXT_BUFFER_SIZE);
                    bindings.version(buffer);
                    return NativeStrings.readBuffer(buffer);
                }
            }));
            return new NativeContext(libraryPath, arena, executor, bindings, version);
        } catch (RuntimeException | Error failure) {
            // Never shutdownNow() here: interrupting a thread that is inside a
            // downcall, then closing the arena, unloads the library beneath it.
            shutdownAndCloseArena(executor, arena, libraryPath);
            throw failure;
        }
    }

    /** Stops the native thread, waits for it to finish, and only then unloads. */
    private static void shutdownAndCloseArena(ExecutorService executor, Arena arena, Path path) {
        executor.shutdown();
        boolean interrupted = false;
        try {
            boolean terminated = false;
            while (!terminated) {
                try {
                    terminated = executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS);
                    if (!terminated) {
                        System.getLogger(NativeContext.class.getName()).log(
                                System.Logger.Level.WARNING,
                                "Swiss Ephemeris native thread for " + path
                                        + " has not stopped; still waiting before unloading");
                    }
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        } finally {
            arena.close();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Runs {@code task} on the native thread and returns its result.
     *
     * <p>A task must never take {@link #REGISTRY_LOCK}: {@link #release()} holds
     * it while waiting for this same thread to finish {@code swe_close()}.</p>
     */
    public <T> T call(NativeTask<T> task) {
        Objects.requireNonNull(task, "task");
        Future<T> future;
        // Reading `closed` and queueing the work have to be one step. Checking
        // first and submitting after is a time-of-check-to-time-of-use race with
        // tearDown(): the check passes, the last handle is released, and the work
        // is then queued behind swe_close() -- or worse, is still executing a
        // downcall when arena.close() unloads the library, which is a crash and
        // not an exception. Under this lock, any task that is accepted is
        // guaranteed to sit ahead of swe_close() in the queue.
        submitLock.lock();
        try {
            if (closed) {
                throw new IllegalStateException(
                        "Swiss Ephemeris native context for " + libraryPath + " is closed");
            }
            future = submit(executor, () -> task.run(bindings));
        } finally {
            submitLock.unlock();
        }
        // Waiting happens outside the lock, so concurrent callers queue up on the
        // native thread rather than on each other.
        return await(future);
    }

    private static <T> Future<T> submit(ExecutorService executor, Callable<T> task) {
        try {
            return executor.submit(task);
        } catch (RejectedExecutionException rejected) {
            throw new IllegalStateException("Swiss Ephemeris native context is closed", rejected);
        }
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            future.cancel(false);
            Thread.currentThread().interrupt();
            throw new SwissEphException(
                    "Interrupted while waiting for a Swiss Ephemeris native call", interrupted);
        } catch (ExecutionException wrapped) {
            throw unwrap(wrapped);
        }
    }

    private static <T> T runOn(ExecutorService executor, Callable<T> task) {
        return await(submit(executor, task));
    }

    /**
     * Waits for the closing task without letting an interrupt abandon it.
     *
     * <p>By the time this runs the context is already out of the registry and
     * marked closed, so there is nothing to retry with: giving up here would
     * leave {@code swe_close()} un-run and, worse, let {@code arena.close()}
     * unload the library while the task is still inside a downcall. The
     * interrupt is remembered and re-raised once the thread and the arena have
     * actually been released.</p>
     */
    private static <T> T awaitUninterruptibly(Future<T> future) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return future.get();
                } catch (InterruptedException ignored) {
                    interrupted = true;
                } catch (ExecutionException wrapped) {
                    throw unwrap(wrapped);
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Rethrows a failure raised on the native thread, keeping its own stack trace
     * and attaching the caller's as a suppressed exception.
     *
     * <p>Without the attachment, the trace would show only frames from the
     * {@code swisseph-native-*} thread and the application would have no way to
     * tell which of its call sites failed.</p>
     */
    private static RuntimeException unwrap(ExecutionException wrapped) {
        Throwable cause = wrapped.getCause();
        if (cause == null) {
            return new SwissEphException("Swiss Ephemeris native call failed", wrapped);
        }
        if (cause instanceof Error error) {
            // Never decorate an Error: it may be a shared, preallocated instance.
            throw error;
        }
        cause.addSuppressed(new CallSite(Thread.currentThread().getName()));
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        return new SwissEphException("Swiss Ephemeris native call failed", cause);
    }

    /** Marker carrying the stack of the thread that requested the native call. */
    private static final class CallSite extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CallSite(String threadName) {
            super("native call dispatched from thread " + threadName);
        }
    }

    /** Decrements the reference count and unloads the library when it reaches zero. */
    public void release() {
        REGISTRY_LOCK.lock();
        try {
            if (closed || referenceCount == 0) {
                return;
            }
            if (--referenceCount > 0) {
                return;
            }
            REGISTRY.remove(libraryPath, this);
            // Claim the path for the duration of the teardown. An acquire() that
            // arrives now waits on this gate instead of loading a second context
            // while swe_close() is still running against this one.
            IN_FLIGHT.put(libraryPath, new CountDownLatch(1));
        } finally {
            REGISTRY_LOCK.unlock();
        }
        try {
            tearDown();
        } finally {
            openGate(libraryPath);
        }
    }

    private void tearDown() {
        Future<Void> closeTask;
        submitLock.lock();
        try {
            closed = true;
            // Queued while no call() can slip in behind it. The executor is
            // single-threaded and FIFO, so once this task has run, every call
            // that was accepted before it has already finished -- which is what
            // makes arena.close() below safe.
            closeTask = submit(executor, () -> {
                bindings.close();
                return null;
            });
        } finally {
            submitLock.unlock();
        }
        try {
            awaitUninterruptibly(closeTask);
        } catch (RuntimeException failure) {
            // swe_close() only frees native buffers. Report it, but never let it
            // stop us from releasing the thread and the arena.
            System.getLogger(NativeContext.class.getName())
                    .log(System.Logger.Level.WARNING, "swe_close() failed for " + libraryPath, failure);
        } finally {
            shutdownAndCloseArena(executor, arena, libraryPath);
        }
    }

    public Path libraryPath() {
        return libraryPath;
    }

    public String nativeVersion() {
        return nativeVersion;
    }

    public boolean isOpen() {
        return !closed;
    }

    public SwissEphSettings settings() {
        return settings;
    }

    public void settings(SwissEphSettings updated) {
        this.settings = updated;
    }

    /** Number of live handles, for tests and diagnostics. */
    public int referenceCount() {
        REGISTRY_LOCK.lock();
        try {
            return referenceCount;
        } finally {
            REGISTRY_LOCK.unlock();
        }
    }

    /** A unit of work executed on the native thread. */
    @FunctionalInterface
    public interface NativeTask<T> {
        T run(NativeBindings bindings);
    }
}
