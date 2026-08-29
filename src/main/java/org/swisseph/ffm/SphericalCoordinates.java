package org.swisseph.ffm;

import java.util.Objects;

/**
 * A pair of spherical coordinates returned by {@code swe_azalt_rev()}.
 *
 * <p>Which system the two numbers belong to depends on what the caller asked
 * for, so the system travels with them and the accessors refuse to answer under
 * the wrong name. Returning a type called "ecliptic" for a conversion that was
 * asked to produce right ascension and declination would be right numbers with
 * wrong labels, which is the failure mode this binding tries hardest to
 * avoid.</p>
 */
public record SphericalCoordinates(double first, double second, HorizontalCoordinateType system) {

    public SphericalCoordinates {
        Objects.requireNonNull(system, "system");
    }

    /** Ecliptic longitude in degrees. */
    public double eclipticLongitude() {
        require(HorizontalCoordinateType.ECLIPTIC, "eclipticLongitude");
        return first;
    }

    /** Ecliptic latitude in degrees. */
    public double eclipticLatitude() {
        require(HorizontalCoordinateType.ECLIPTIC, "eclipticLatitude");
        return second;
    }

    /** Right ascension in degrees. */
    public double rightAscension() {
        require(HorizontalCoordinateType.EQUATORIAL, "rightAscension");
        return first;
    }

    /** Declination in degrees. */
    public double declination() {
        require(HorizontalCoordinateType.EQUATORIAL, "declination");
        return second;
    }

    private void require(HorizontalCoordinateType expected, String accessor) {
        if (system != expected) {
            throw new IllegalStateException("these are " + system + " coordinates, so " + accessor
                    + "() does not apply; the conversion was asked for " + system);
        }
    }
}
