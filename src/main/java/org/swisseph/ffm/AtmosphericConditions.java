package org.swisseph.ffm;

/**
 * Air pressure and temperature, used by the refraction model behind
 * {@code swe_azalt()} and {@code swe_rise_trans()}.
 */
public record AtmosphericConditions(double pressureMillibar, double temperatureCelsius) {
    /** Standard sea-level atmosphere: 1013.25 mbar at 15 degrees Celsius. */
    public static final AtmosphericConditions STANDARD = new AtmosphericConditions(1013.25, 15.0);

    /**
     * Pressure 0, which tells Swiss Ephemeris to derive it from the observer
     * altitude instead of using a fixed value.
     */
    public static final AtmosphericConditions FROM_ALTITUDE = new AtmosphericConditions(0.0, 15.0);

    public AtmosphericConditions {
        Validation.pressure(pressureMillibar);
        Validation.temperature(temperatureCelsius);
    }

    /** Returns whether the pressure will be derived from the observer altitude. */
    public boolean derivesPressureFromAltitude() {
        return pressureMillibar == 0.0;
    }
}
