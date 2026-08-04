package org.swisseph.ffm;

/** Standard body identifiers used by Swiss Ephemeris 2.10.03. */
public enum CelestialBody {
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

    private final int id;

    CelestialBody(int id) {
        this.id = id;
    }

    /** Returns the {@code ipl} value expected by the C API. */
    public int id() {
        return id;
    }
}
