package org.swisseph.ffm;

/**
 * Standard body identifiers used by Swiss Ephemeris.
 *
 * <p>Asteroids, planetary moons, and fictitious bodies are addressed by number
 * rather than by constant; {@link #asteroid(int)} and {@link #planetaryMoon(int)}
 * apply the offsets the C API expects.</p>
 */
public enum CelestialBody {
    /** {@code SE_ECL_NUT}: obliquity and nutation rather than a body. */
    ECLIPTIC_NUTATION(-1),
    SUN(0),
    MOON(1),
    MERCURY(2),
    VENUS(3),
    MARS(4),
    JUPITER(5),
    SATURN(6),
    URANUS(7),
    NEPTUNE(8),
    PLUTO(9),
    MEAN_NODE(10),
    TRUE_NODE(11),
    MEAN_APOGEE(12),
    OSCULATING_APOGEE(13),
    EARTH(14),
    CHIRON(15),
    PHOLUS(16),
    CERES(17),
    PALLAS(18),
    JUNO(19),
    VESTA(20),
    INTERPOLATED_APOGEE(21),
    INTERPOLATED_PERIGEE(22);

    /** {@code SE_AST_OFFSET}. */
    public static final int ASTEROID_OFFSET = 10_000;
    /** {@code SE_PLMOON_OFFSET}. */
    public static final int PLANETARY_MOON_OFFSET = 9_000;

    private final int id;

    CelestialBody(int id) {
        this.id = id;
    }

    /** Returns the {@code ipl} value expected by the C API. */
    public int id() {
        return id;
    }

    /**
     * Returns the {@code ipl} value for a numbered asteroid.
     *
     * @param minorPlanetNumber the MPC number, for example 433 for Eros
     */
    public static int asteroid(int minorPlanetNumber) {
        if (minorPlanetNumber <= 0 || minorPlanetNumber > Integer.MAX_VALUE - ASTEROID_OFFSET) {
            throw new IllegalArgumentException("minorPlanetNumber must be between 1 and "
                    + (Integer.MAX_VALUE - ASTEROID_OFFSET) + ", but was " + minorPlanetNumber);
        }
        return ASTEROID_OFFSET + minorPlanetNumber;
    }

    /** Returns the {@code ipl} value for a planetary moon. */
    public static int planetaryMoon(int moonNumber) {
        if (moonNumber <= 0 || moonNumber > Integer.MAX_VALUE - PLANETARY_MOON_OFFSET) {
            throw new IllegalArgumentException("moonNumber must be between 1 and "
                    + (Integer.MAX_VALUE - PLANETARY_MOON_OFFSET) + ", but was " + moonNumber);
        }
        return PLANETARY_MOON_OFFSET + moonNumber;
    }
}
