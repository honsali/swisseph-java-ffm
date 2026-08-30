package org.swisseph.ffm;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * How to open a Swiss Ephemeris library, and what to configure once it is open.
 *
 * <p>Everything a caller can decide up front lives here rather than in a
 * sequence of setter calls, because the native settings are shared by every
 * handle on the same library: applying them as one step, on the thread that
 * will use them, is what makes the result predictable.</p>
 *
 * <pre>{@code
 * SwissEphConfig config = SwissEphConfig.builder()
 *         .library(Path.of("/opt/swisseph/libswe.so"))
 *         .ephemerisPath(Path.of("/opt/swisseph/ephe"))
 *         .build();
 * }</pre>
 */
public final class SwissEphConfig {
    /** Upstream versions this binding is written against. */
    public static final Set<String> DEFAULT_SUPPORTED_VERSIONS = Set.of("2.10.03");

    private final Path libraryPath;
    private final String librarySource;
    private final Set<String> supportedVersions;
    private final NativeVersionPolicy versionPolicy;
    private final String ephemerisPath;
    private final String jplFile;
    private final GeographicPosition topocentricObserver;
    private final Integer siderealMode;
    private final double siderealT0;
    private final double siderealAyanamsaAtT0;

    private SwissEphConfig(Builder builder) {
        this.libraryPath = builder.libraryPath;
        this.librarySource = builder.librarySource;
        this.supportedVersions = Set.copyOf(builder.supportedVersions);
        this.versionPolicy = builder.versionPolicy;
        this.ephemerisPath = builder.ephemerisPath;
        this.jplFile = builder.jplFile;
        this.topocentricObserver = builder.topocentricObserver;
        this.siderealMode = builder.siderealMode;
        this.siderealT0 = builder.siderealT0;
        this.siderealAyanamsaAtT0 = builder.siderealAyanamsaAtT0;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A configuration for {@code libraryPath}, with everything else left at its default. */
    public static SwissEphConfig of(Path libraryPath) {
        return builder().library(libraryPath).build();
    }

    /**
     * A configuration whose library is resolved through {@link NativeLibraryLocator}.
     *
     * @throws IllegalStateException if no library can be found, with a message
     *                               listing every candidate that was tried
     */
    public static SwissEphConfig fromEnvironment() {
        return builder().build();
    }

    public Path libraryPath() {
        return libraryPath;
    }

    /** Where {@link #libraryPath()} came from, for logs and diagnostics. */
    public String librarySource() {
        return librarySource;
    }

    public Set<String> supportedVersions() {
        return supportedVersions;
    }

    public NativeVersionPolicy versionPolicy() {
        return versionPolicy;
    }

    public Optional<String> ephemerisPath() {
        return Optional.ofNullable(ephemerisPath);
    }

    public Optional<String> jplFile() {
        return Optional.ofNullable(jplFile);
    }

    public Optional<GeographicPosition> topocentricObserver() {
        return Optional.ofNullable(topocentricObserver);
    }

    public Optional<Integer> siderealMode() {
        return Optional.ofNullable(siderealMode);
    }

    public double siderealT0() {
        return siderealT0;
    }

    public double siderealAyanamsaAtT0() {
        return siderealAyanamsaAtT0;
    }

    @Override
    public String toString() {
        return "SwissEphConfig[library=" + libraryPath + " (" + librarySource + ")"
                + ", versionPolicy=" + versionPolicy
                + ", supportedVersions=" + supportedVersions
                + ", ephemerisPath=" + ephemerisPath + "]";
    }

    /** Builder for {@link SwissEphConfig}. */
    public static final class Builder {
        private Path libraryPath;
        private String librarySource;
        private Set<String> supportedVersions = new LinkedHashSet<>(DEFAULT_SUPPORTED_VERSIONS);
        private NativeVersionPolicy versionPolicy = NativeVersionPolicy.REJECT;
        private String ephemerisPath;
        private String jplFile;
        private GeographicPosition topocentricObserver;
        private Integer siderealMode;
        private double siderealT0;
        private double siderealAyanamsaAtT0;

        private Builder() {
        }

        /** Uses this exact shared library, skipping the search order entirely. */
        public Builder library(Path path) {
            this.libraryPath = Objects.requireNonNull(path, "path");
            this.librarySource = "explicit path";
            return this;
        }

        /** Accepts these native versions in addition to, or instead of, the defaults. */
        public Builder supportedVersions(String... versions) {
            Objects.requireNonNull(versions, "versions");
            LinkedHashSet<String> replacement = new LinkedHashSet<>();
            for (String version : versions) {
                replacement.add(Objects.requireNonNull(version, "version"));
            }
            if (replacement.isEmpty()) {
                throw new IllegalArgumentException("at least one supported version is required");
            }
            this.supportedVersions = replacement;
            return this;
        }

        /** What to do when the loaded library reports an unsupported version. */
        public Builder versionPolicy(NativeVersionPolicy policy) {
            this.versionPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        /** Applies {@code swe_set_ephe_path()} as part of opening. */
        public Builder ephemerisPath(Path path) {
            Objects.requireNonNull(path, "path");
            return ephemerisPath(path.toAbsolutePath().normalize().toString());
        }

        /**
         * Applies {@code swe_set_ephe_path()} as part of opening.
         *
         * <p>Accepts the platform-specific list of directories that
         * {@code swe_set_ephe_path()} understands, not just a single one.</p>
         */
        public Builder ephemerisPath(String path) {
            this.ephemerisPath = Objects.requireNonNull(path, "path");
            return this;
        }

        /** Applies {@code swe_set_jpl_file()} as part of opening. */
        public Builder jplFile(String fileName) {
            this.jplFile = Objects.requireNonNull(fileName, "fileName");
            return this;
        }

        /** Applies {@code swe_set_topo()} as part of opening. */
        public Builder topocentricObserver(GeographicPosition observer) {
            this.topocentricObserver = Objects.requireNonNull(observer, "observer");
            return this;
        }

        /** Applies a predefined ayanamsha through {@code swe_set_sid_mode()}. */
        public Builder siderealMode(SiderealMode mode, SiderealOption... options) {
            Objects.requireNonNull(mode, "mode");
            if (mode == SiderealMode.USER) {
                throw new IllegalArgumentException("SiderealMode.USER defines its ayanamsha from "
                        + "t0 and ayanamsaAtT0; use siderealMode(int, double, double) to supply "
                        + "them");
            }
            this.siderealMode = Validation.siderealMode(
                    mode.value() | SiderealOption.mask(options));
            this.siderealT0 = 0.0;
            this.siderealAyanamsaAtT0 = 0.0;
            return this;
        }

        /** Applies a user-defined ayanamsha through {@code swe_set_sid_mode()}. */
        public Builder siderealMode(int mode, double t0, double ayanamsaAtT0) {
            // The same check the setter runs: open(config) pushes these values
            // into the library itself, so a rule enforced only there would be
            // bypassed by the configuration path.
            this.siderealMode = Validation.siderealMode(mode);
            this.siderealT0 = Validation.finite(t0, "t0");
            this.siderealAyanamsaAtT0 = Validation.finite(ayanamsaAtT0, "ayanamsaAtT0");
            return this;
        }

        public SwissEphConfig build() {
            if (libraryPath == null) {
                List<String> attempts = new ArrayList<>();
                NativeLibraryLocator.Resolution resolution = NativeLibraryLocator.locate(attempts)
                        .orElseThrow(() -> new IllegalStateException(
                                NativeLibraryLocator.describeFailure(attempts)));
                libraryPath = resolution.path();
                librarySource = resolution.source();
            }
            return new SwissEphConfig(this);
        }
    }
}
