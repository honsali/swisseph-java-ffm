package org.swisseph.ffm;

/**
 * Argument checks applied before anything reaches native memory.
 *
 * <p>Swiss Ephemeris is written in C and does not defend itself: a {@code NaN}
 * Julian day makes its search loops run to their iteration cap, an out-of-range
 * latitude produces silently wrong houses, and an absurd magnitude makes an
 * eclipse search spin. Rejecting these in Java turns a confusing result into an
 * immediate {@link IllegalArgumentException}.</p>
 */
final class Validation {
    /**
     * Lowest Julian day accepted. Below the start of the Swiss Ephemeris data
     * range, which begins at JD -3027215.5 in year -13200, so the library itself
     * still gets to report genuine range errors through {@code serr}.
     */
    static final double MIN_JULIAN_DAY = -3_100_000.0;
    /**
     * Highest Julian day accepted. Above the end of the data range, which stops
     * at JD 7857131.5 in year 17191.
     */
    static final double MAX_JULIAN_DAY = 8_000_000.0;

    /** Absolute zero: no temperature below this is physically meaningful. */
    static final double MIN_TEMPERATURE_CELSIUS = -273.15;
    /** Generous upper bound; real atmospheric readings stay far below it. */
    static final double MAX_TEMPERATURE_CELSIUS = 1_000.0;
    /** Highest atmospheric pressure accepted, in millibar. */
    static final double MAX_PRESSURE_MILLIBAR = 100_000.0;

    /** {@code SEI_ECL_GEOALT_MIN}: the eclipse routines reject anything lower. */
    static final double MIN_ECLIPSE_ALTITUDE_METERS = -500.0;
    /** {@code SEI_ECL_GEOALT_MAX}: the eclipse routines reject anything higher. */
    static final double MAX_ECLIPSE_ALTITUDE_METERS = 25_000.0;

    /**
     * Where the barometric model behind {@code atpress == 0} breaks down.
     *
     * <p>Swiss Ephemeris derives the pressure as
     * {@code 1013.25 * pow(1 - 0.0065 * altitude / 288, 5.255)}. The base turns
     * negative above {@code 288 / 0.0065}, and a negative base with a
     * fractional exponent is {@code NaN}, which then flows silently into the
     * refracted altitude.</p>
     */
    static final double MAX_DERIVED_PRESSURE_ALTITUDE_METERS = 288.0 / 0.0065;

    /**
     * Rejects a raw {@code sid_mode} the library would silently replace.
     *
     * <p>{@code swe_set_sid_mode()} clamps a negative value to zero and, for any
     * base it does not define, falls back to Fagan/Bradley without saying so.
     * The snapshot in {@code settings()} would go on reporting the value that
     * was asked for.</p>
     */
    static int siderealMode(int mode) {
        if (mode < 0) {
            throw new IllegalArgumentException(
                    "sidereal mode must not be negative, but was " + mode);
        }
        int base = mode & 0xFF;
        int options = mode & ~0xFF;
        if (SiderealMode.of(base).isEmpty()) {
            throw new IllegalArgumentException("sidereal mode " + base
                    + " is not one this library defines; upstream would silently substitute "
                    + "Fagan/Bradley. See SiderealMode for the defined values.");
        }
        int knownOptions = 0;
        for (SiderealOption option : SiderealOption.values()) {
            knownOptions |= option.value();
        }
        if ((options & ~knownOptions) != 0) {
            throw new IllegalArgumentException("sidereal mode carries unknown option bits 0x"
                    + Integer.toHexString(options & ~knownOptions));
        }
        siderealOptions(SiderealMode.of(base).orElseThrow(), options);
        return mode;
    }

    /** The three mutually exclusive sidereal projections. */
    private static final int SIDEREAL_PROJECTION_MASK =
            SiderealOption.ECLIPTIC_AT_T0.value() | SiderealOption.SOLAR_SYSTEM_PLANE.value()
                    | SiderealOption.ECLIPTIC_OF_DATE.value();

    /**
     * Rejects option bits the chosen ayanamsha would neutralise.
     *
     * <p>Every case here is one where {@code swe_set_sid_mode()} accepts the
     * request and then computes something else, leaving the snapshot in
     * {@code settings()} describing a configuration that is not in force.</p>
     */
    private static void siderealOptions(SiderealMode base, int options) {
        if ((options & SiderealOption.ORIGINAL_PRECESSION.value()) != 0) {
            // Upstream calls this a test feature, and it is not an option of the
            // sidereal mode at all: it overwrites swed.astro_models, which is
            // process-global, one way. Clearing the bit later does not put the
            // old models back, and swe_set_ephe_path() wipes them through
            // swi_close_keep_topo_etc() without the bit changing. Whichever way
            // it is used, settings() ends up describing a configuration that is
            // not the one in force.
            throw new IllegalArgumentException("ORIGINAL_PRECESSION changes the global "
                    + "precession and nutation models rather than this sidereal mode, and "
                    + "nothing restores them; this binding cannot report that state honestly, "
                    + "so it refuses the option");
        }
        int projections = options & SIDEREAL_PROJECTION_MASK;
        if (Integer.bitCount(projections) > 1) {
            // The projection is chosen by an if / else-if / else chain that tests
            // ECL_T0, then SSY_PLANE, and only reads ECL_DATE in the last branch.
            throw new IllegalArgumentException("ECLIPTIC_AT_T0, SOLAR_SYSTEM_PLANE and "
                    + "ECLIPTIC_OF_DATE select different projections and are mutually "
                    + "exclusive; upstream would silently keep the first of them");
        }
        if ((options & SiderealOption.USER_T0_IN_UT.value()) != 0
                && base != SiderealMode.USER) {
            // Only read inside the SE_SIDM_USER branch.
            throw new IllegalArgumentException(
                    "USER_T0_IN_UT only means anything with SiderealMode.USER, but the mode is "
                            + base);
        }
        if (base.ignoresOptions() && options != 0) {
            throw new IllegalArgumentException(base + " is computed directly and upstream "
                    + "discards its option bits; requesting any would describe a configuration "
                    + "that is not in force");
        }
        if (base.forcesEclipticAtT0()
                && (options & ~SiderealOption.ECLIPTIC_AT_T0.value()) != 0) {
            throw new IllegalArgumentException(base + " is defined by its reference frame and "
                    + "upstream replaces its options with ECLIPTIC_AT_T0, so no other option "
                    + "can be honoured");
        }
    }

    /** {@code NDIAM}: the length of the native {@code pla_diam} table, {@code SE_VESTA + 1}. */
    private static final int DIAMETER_TABLE_LENGTH = CelestialBody.VESTA.id() + 1;

    /**
     * Rejects an identifier that would index the native diameter table out of
     * bounds.
     *
     * <p>{@code swe_rise_trans()} and {@code swe_pheno()} both reach
     * {@code if (ipl &lt; NDIAM) dd = pla_diam[ipl];}, and that guard only bounds
     * the top. {@code SE_ECL_NUT} is {@code -1}, {@code swe_calc()} accepts it as
     * a request for obliquity and nutation so nothing fails earlier, and the read
     * lands before the start of the array.</p>
     *
     * <p>Asking for the obliquity through {@code calculate()} stays legal: that
     * is what {@code -1} means there.</p>
     */
    static int safeBodyIdentifier(int bodyId, String what) {
        if (bodyId < 0) {
            throw new IllegalArgumentException(what + " needs a real body, but was given "
                    + bodyId + ". Negative identifiers such as CelestialBody.ECLIPTIC_NUTATION "
                    + "index the native diameter table out of bounds; they are only meaningful "
                    + "to calculate() and calculateUt().");
        }
        return bodyId;
    }

    /**
     * Checks a rise, set, or transit target and returns the identifier to use.
     *
     * <p>{@code swe_calc()} answers a geocentric request for the Earth by
     * zeroing all 24 values and reporting success, so the distance is zero and
     * the rise calculation divides by it. Nothing fails; the times just come
     * back meaningless.</p>
     *
     * <p>The returned value is {@linkplain #canonicalBodyId canonical} and must
     * be the one passed on to the native call.</p>
     */
    static int riseTransitTarget(int bodyId) {
        safeBodyIdentifier(bodyId, "a rise, set or transit search");
        int canonical = canonicalBodyId(bodyId);
        if (canonical == CelestialBody.EARTH.id()) {
            throw new IllegalArgumentException("the Earth cannot rise or set from the Earth; "
                    + "swe_calc() returns a zero vector for it geocentrically, which the rise "
                    + "calculation then divides by");
        }
        return canonical;
    }

    /**
     * Resolves the four asteroid aliases onto the constants they duplicate.
     *
     * <p>{@code SE_AST_OFFSET + 1..4} name Ceres, Pallas, Juno and Vesta, which
     * are also {@code SE_CERES..SE_VESTA}. {@code swe_calc()} treats them as
     * equivalent, but only by rewriting its own local {@code ipl};
     * {@code swe_rise_trans_true_hor()} keeps whatever it was handed, and an
     * alias then takes the {@code ipl > SE_AST_OFFSET} branch and reads its
     * diameter from {@code swed.ast_diam}. That field is filled only while
     * parsing an individual asteroid file, which the remapped calculation never
     * opens: it is zero on a cold context and stale after some other asteroid
     * has been computed. Passing the canonical identifier instead puts the body
     * back on {@code pla_diam} and takes the native history out of the answer.
     * </p>
     *
     * <p>The effect on a rise time is small, since these bodies subtend well
     * under an arcsecond. The point is that it should not depend on what was
     * calculated before.</p>
     */
    static int canonicalBodyId(int bodyId) {
        int alias = bodyId - CelestialBody.ASTEROID_OFFSET;
        if (alias >= 1 && alias <= 4) {
            return CelestialBody.CERES.id() + alias - 1;
        }
        return bodyId;
    }

    /**
     * Whether the native code gives this body a disc.
     *
     * <p>Follows {@code pla_diam} exactly: the Sun through Pluto and the Earth
     * have diameters, the four node and apogee slots hold zero, Chiron through
     * Vesta have diameters, and anything past the end of the table takes the
     * {@code else dd = 0} branch unless it is a numbered asteroid. A body with
     * no disc makes {@code DISC_CENTER} and {@code DISC_BOTTOM} no-ops.</p>
     */
    static boolean hasNativeDisc(int bodyId) {
        if (bodyId > CelestialBody.ASTEROID_OFFSET) {
            return true;
        }
        if (bodyId < 0 || bodyId >= DIAMETER_TABLE_LENGTH) {
            return false;
        }
        // The four zero entries: mean node, true node, mean apogee, osculating apogee.
        return bodyId < CelestialBody.MEAN_NODE.id()
                || bodyId > CelestialBody.OSCULATING_APOGEE.id();
    }

    /** Rejects an observer the eclipse routines would refuse. */
    static GeographicPosition eclipseObserver(GeographicPosition observer) {
        double altitude = observer.altitudeMeters();
        if (altitude < MIN_ECLIPSE_ALTITUDE_METERS || altitude > MAX_ECLIPSE_ALTITUDE_METERS) {
            throw new IllegalArgumentException("the eclipse routines accept altitudes between "
                    + MIN_ECLIPSE_ALTITUDE_METERS + " and " + MAX_ECLIPSE_ALTITUDE_METERS
                    + " metres, but was " + altitude);
        }
        return observer;
    }

    /**
     * Rejects the pairing of a very high observer with a derived pressure, which
     * the native barometric model turns into {@code NaN}.
     */
    static void pressureModel(GeographicPosition observer, AtmosphericConditions atmosphere) {
        // Strictly above: at exactly the limit the base is zero and pow() is
        // still finite.
        if (atmosphere.derivesPressureFromAltitude()
                && observer.altitudeMeters() > MAX_DERIVED_PRESSURE_ALTITUDE_METERS) {
            throw new IllegalArgumentException("deriving the pressure from an altitude of "
                    + observer.altitudeMeters() + " m is not possible: the native barometric "
                    + "model returns NaN above " + MAX_DERIVED_PRESSURE_ALTITUDE_METERS
                    + " m. Give an explicit pressure instead.");
        }
    }

    private Validation() {
    }

    static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number, but was " + value);
        }
        return value;
    }

    static double julianDay(double value, String name) {
        finite(value, name);
        if (value < MIN_JULIAN_DAY || value > MAX_JULIAN_DAY) {
            throw new IllegalArgumentException(name + " must be between " + MIN_JULIAN_DAY
                    + " and " + MAX_JULIAN_DAY + ", but was " + value);
        }
        return value;
    }

    static double longitude(double value) {
        finite(value, "longitude");
        if (value < -180.0 || value > 180.0) {
            throw new IllegalArgumentException(
                    "longitude must be between -180 and 180 degrees, but was " + value);
        }
        return value;
    }

    static double latitude(double value) {
        finite(value, "latitude");
        if (value < -90.0 || value > 90.0) {
            throw new IllegalArgumentException(
                    "latitude must be between -90 and 90 degrees, but was " + value);
        }
        return value;
    }

    static double altitude(double value) {
        return finite(value, "altitudeMeters");
    }

    static double pressure(double value) {
        finite(value, "pressureMillibar");
        if (value < 0.0 || value > MAX_PRESSURE_MILLIBAR) {
            throw new IllegalArgumentException("pressureMillibar must be between 0 and "
                    + MAX_PRESSURE_MILLIBAR + ", but was " + value);
        }
        return value;
    }

    static double temperature(double value) {
        finite(value, "temperatureCelsius");
        if (value <= MIN_TEMPERATURE_CELSIUS || value > MAX_TEMPERATURE_CELSIUS) {
            throw new IllegalArgumentException("temperatureCelsius must be above "
                    + MIN_TEMPERATURE_CELSIUS + " and at most " + MAX_TEMPERATURE_CELSIUS
                    + ", but was " + value);
        }
        return value;
    }

    static double degrees(double value, String name) {
        return finite(value, name);
    }

    /**
     * Checks the reference epoch and offset against the mode they accompany.
     *
     * <p>{@code swe_set_sid_mode()} reads them only inside its
     * {@code SE_SIDM_USER} branch and takes the table values otherwise, so a
     * non-zero pair with any other mode is discarded while the snapshot keeps
     * reporting it.</p>
     */
    static void siderealReference(int mode, double t0, double ayanamsaAtT0) {
        finite(t0, "t0");
        finite(ayanamsaAtT0, "ayanamsaAtT0");
        if ((mode & 0xFF) == SiderealMode.USER.value()) {
            julianDay(t0, "t0");
            return;
        }
        if (t0 != 0.0 || ayanamsaAtT0 != 0.0) {
            throw new IllegalArgumentException("t0 and ayanamsaAtT0 only apply to "
                    + "SiderealMode.USER; upstream takes them from its own table for every "
                    + "other mode and would discard these");
        }
    }

    static int inRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and "
                    + maximum + ", but was " + value);
        }
        return value;
    }
}
