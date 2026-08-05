package org.swisseph.ffm.internal;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/** Exact FFM downcall handles for the supported subset of swephexp.h. */
public final class NativeBindings {
    private final Linker linker = Linker.nativeLinker();
    private final SymbolLookup symbols;

    private final MethodHandle sweVersion;
    private final MethodHandle sweCalc;
    private final MethodHandle sweCalcUt;
    private final MethodHandle sweClose;
    private final MethodHandle sweSetEphePath;
    private final MethodHandle sweSetJplFile;
    private final MethodHandle sweGetPlanetName;
    private final MethodHandle sweSetTopo;
    private final MethodHandle sweSetSidMode;
    private final MethodHandle sweJulday;
    private final MethodHandle sweRevjul;
    private final MethodHandle sweUtcToJd;
    private final MethodHandle sweDeltat;
    private final MethodHandle sweHousesEx;
    private final MethodHandle sweAzalt;
    private final MethodHandle sweFixstarUt;
    private final MethodHandle sweRiseTrans;
    private final MethodHandle sweSolEclipseWhenGlob;
    private final MethodHandle sweSolEclipseWhenLoc;
    private final MethodHandle sweSolEclipseWhere;
    private final MethodHandle sweSolEclipseHow;
    private final MethodHandle sweLunEclipseWhen;
    private final MethodHandle sweLunEclipseWhenLoc;
    private final MethodHandle sweLunEclipseHow;
    private final MethodHandle swePhenoUt;

    public NativeBindings(SymbolLookup symbols) {
        this.symbols = symbols;
        sweVersion = bind("swe_version", FunctionDescriptor.of(ADDRESS, ADDRESS));
        sweCalc = bind("swe_calc", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
        sweCalcUt = bind("swe_calc_ut", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
        sweClose = bind("swe_close", FunctionDescriptor.ofVoid());
        sweSetEphePath = bind("swe_set_ephe_path", FunctionDescriptor.ofVoid(ADDRESS));
        sweSetJplFile = bind("swe_set_jpl_file", FunctionDescriptor.ofVoid(ADDRESS));
        sweGetPlanetName = bind("swe_get_planet_name", FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS));
        sweSetTopo = bind("swe_set_topo", FunctionDescriptor.ofVoid(
                JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE));
        sweSetSidMode = bind("swe_set_sid_mode", FunctionDescriptor.ofVoid(
                JAVA_INT, JAVA_DOUBLE, JAVA_DOUBLE));
        sweJulday = bind("swe_julday", FunctionDescriptor.of(
                JAVA_DOUBLE, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_DOUBLE, JAVA_INT));
        sweRevjul = bind("swe_revjul", FunctionDescriptor.ofVoid(
                JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        sweUtcToJd = bind("swe_utc_to_jd", FunctionDescriptor.of(
                JAVA_INT,
                JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_DOUBLE, JAVA_INT,
                ADDRESS, ADDRESS));
        sweDeltat = bind("swe_deltat", FunctionDescriptor.of(JAVA_DOUBLE, JAVA_DOUBLE));
        sweHousesEx = bind("swe_houses_ex", FunctionDescriptor.of(
                JAVA_INT,
                JAVA_DOUBLE, JAVA_INT, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS));
        sweAzalt = bind("swe_azalt", FunctionDescriptor.ofVoid(
                JAVA_DOUBLE, JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, ADDRESS, ADDRESS));
        sweFixstarUt = bind("swe_fixstar_ut", FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS));
        sweRiseTrans = bind("swe_rise_trans", FunctionDescriptor.of(
                JAVA_INT,
                JAVA_DOUBLE, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS,
                JAVA_DOUBLE, JAVA_DOUBLE, ADDRESS, ADDRESS));
        sweSolEclipseWhenGlob = bind("swe_sol_eclipse_when_glob", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        sweSolEclipseWhenLoc = bind("swe_sol_eclipse_when_loc", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
        sweSolEclipseWhere = bind("swe_sol_eclipse_where", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        sweSolEclipseHow = bind("swe_sol_eclipse_how", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        sweLunEclipseWhen = bind("swe_lun_eclipse_when", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        sweLunEclipseWhenLoc = bind("swe_lun_eclipse_when_loc", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
        sweLunEclipseHow = bind("swe_lun_eclipse_how", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        swePhenoUt = bind("swe_pheno_ut", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
    }

    public void version(MemorySegment versionBuffer) {
        invokeVoidResultIgnored(sweVersion, versionBuffer);
    }

    public int calc(double julianDay, int bodyId, int flags, MemorySegment result, MemorySegment error) {
        try {
            return (int) sweCalc.invokeExact(julianDay, bodyId, flags, result, error);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_calc", throwable);
        }
    }

    public int calcUt(double julianDay, int bodyId, int flags, MemorySegment result, MemorySegment error) {
        try {
            return (int) sweCalcUt.invokeExact(julianDay, bodyId, flags, result, error);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_calc_ut", throwable);
        }
    }

    public void close() {
        invokeVoid(sweClose, "swe_close");
    }

    public void setEphemerisPath(MemorySegment path) {
        invokeVoid(sweSetEphePath, "swe_set_ephe_path", path);
    }

    public void setJplFile(MemorySegment fileName) {
        invokeVoid(sweSetJplFile, "swe_set_jpl_file", fileName);
    }

    public void planetName(int bodyId, MemorySegment nameBuffer) {
        try {
            MemorySegment ignored = (MemorySegment) sweGetPlanetName.invokeExact(bodyId, nameBuffer);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_get_planet_name", throwable);
        }
    }

    public void setTopocentricPosition(double longitude, double latitude, double altitudeMeters) {
        try {
            sweSetTopo.invokeExact(longitude, latitude, altitudeMeters);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_set_topo", throwable);
        }
    }

    public void setSiderealMode(int mode, double t0, double ayanamsaAtT0) {
        try {
            sweSetSidMode.invokeExact(mode, t0, ayanamsaAtT0);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_set_sid_mode", throwable);
        }
    }

    public double julianDay(int year, int month, int day, double hour, int calendar) {
        try {
            return (double) sweJulday.invokeExact(year, month, day, hour, calendar);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_julday", throwable);
        }
    }

    public void reverseJulianDay(double julianDay, int calendar, MemorySegment year,
                                 MemorySegment month, MemorySegment day, MemorySegment hour) {
        try {
            sweRevjul.invokeExact(julianDay, calendar, year, month, day, hour);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_revjul", throwable);
        }
    }

    public int utcToJulianDay(int year, int month, int day, int hour, int minute, double second,
                              int calendar, MemorySegment result, MemorySegment error) {
        try {
            return (int) sweUtcToJd.invokeExact(
                    year, month, day, hour, minute, second, calendar, result, error);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_utc_to_jd", throwable);
        }
    }

    public double deltaT(double julianDayUt) {
        try {
            return (double) sweDeltat.invokeExact(julianDayUt);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_deltat", throwable);
        }
    }

    public int housesEx(double julianDayUt, int flags, double latitude, double longitude,
                        int houseSystem, MemorySegment cusps, MemorySegment additionalPoints) {
        try {
            return (int) sweHousesEx.invokeExact(
                    julianDayUt, flags, latitude, longitude, houseSystem, cusps, additionalPoints);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_houses_ex", throwable);
        }
    }

    public void azalt(double julianDayUt, int coordinateType, MemorySegment geographicPosition,
                      double pressure, double temperature, MemorySegment input,
                      MemorySegment result) {
        try {
            sweAzalt.invokeExact(
                    julianDayUt, coordinateType, geographicPosition, pressure, temperature, input, result);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_azalt", throwable);
        }
    }

    public int fixedStarUt(MemorySegment star, double julianDayUt, int flags,
                           MemorySegment result, MemorySegment error) {
        try {
            return (int) sweFixstarUt.invokeExact(star, julianDayUt, flags, result, error);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_fixstar_ut", throwable);
        }
    }

    public int riseTransit(double julianDayUt, int bodyId, MemorySegment star, int ephemerisFlags,
                           int eventFlags, MemorySegment geographicPosition, double pressure,
                           double temperature, MemorySegment result, MemorySegment error) {
        try {
            return (int) sweRiseTrans.invokeExact(
                    julianDayUt, bodyId, star, ephemerisFlags, eventFlags, geographicPosition,
                    pressure, temperature, result, error);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_rise_trans", throwable);
        }
    }

    public int solarEclipseWhenGlobal(double startJulianDayUt, int flags, int eclipseTypeFlags,
                                      MemorySegment times, int backward, MemorySegment error) {
        try {
            return (int) sweSolEclipseWhenGlob.invokeExact(
                    startJulianDayUt, flags, eclipseTypeFlags, times, backward, error);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_sol_eclipse_when_glob", throwable);
        }
    }

    public int solarEclipseWhenLocal(double startJulianDayUt, int flags,
                                     MemorySegment geographicPosition, MemorySegment times,
                                     MemorySegment attributes, int backward, MemorySegment error) {
        try {
            return (int) sweSolEclipseWhenLoc.invokeExact(
                    startJulianDayUt, flags, geographicPosition, times, attributes, backward, error);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_sol_eclipse_when_loc", throwable);
        }
    }

    public int solarEclipseWhere(double julianDayUt, int flags, MemorySegment geographicPosition,
                                 MemorySegment attributes, MemorySegment error) {
        try {
            return (int) sweSolEclipseWhere.invokeExact(
                    julianDayUt, flags, geographicPosition, attributes, error);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_sol_eclipse_where", throwable);
        }
    }

    public int solarEclipseHow(double julianDayUt, int flags, MemorySegment geographicPosition,
                               MemorySegment attributes, MemorySegment error) {
        try {
            return (int) sweSolEclipseHow.invokeExact(
                    julianDayUt, flags, geographicPosition, attributes, error);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_sol_eclipse_how", throwable);
        }
    }

    public int lunarEclipseWhen(double startJulianDayUt, int flags, int eclipseTypeFlags,
                                MemorySegment times, int backward, MemorySegment error) {
        try {
            return (int) sweLunEclipseWhen.invokeExact(
                    startJulianDayUt, flags, eclipseTypeFlags, times, backward, error);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_lun_eclipse_when", throwable);
        }
    }

    public int lunarEclipseWhenLocal(double startJulianDayUt, int flags,
                                     MemorySegment geographicPosition, MemorySegment times,
                                     MemorySegment attributes, int backward, MemorySegment error) {
        try {
            return (int) sweLunEclipseWhenLoc.invokeExact(
                    startJulianDayUt, flags, geographicPosition, times, attributes, backward, error);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_lun_eclipse_when_loc", throwable);
        }
    }

    public int lunarEclipseHow(double julianDayUt, int flags, MemorySegment geographicPosition,
                               MemorySegment attributes, MemorySegment error) {
        try {
            return (int) sweLunEclipseHow.invokeExact(
                    julianDayUt, flags, geographicPosition, attributes, error);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_lun_eclipse_how", throwable);
        }
    }

    public int phenomenaUt(double julianDayUt, int bodyId, int flags,
                           MemorySegment attributes, MemorySegment error) {
        try {
            return (int) swePhenoUt.invokeExact(julianDayUt, bodyId, flags, attributes, error);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_pheno_ut", throwable);
        }
    }

    private MethodHandle bind(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = symbols.find(name)
                .orElseThrow(() -> new UnsatisfiedLinkError("Missing Swiss Ephemeris symbol: " + name));
        return linker.downcallHandle(symbol, descriptor);
    }

    private static void invokeVoid(MethodHandle handle, String function, Object... arguments) {
        try {
            handle.invokeWithArguments(arguments);
        } catch (Throwable throwable) {
            throw invocationFailure(function, throwable);
        }
    }

    private static void invokeVoidResultIgnored(MethodHandle handle, MemorySegment argument) {
        try {
            MemorySegment ignored = (MemorySegment) handle.invokeExact(argument);
        } catch (Throwable throwable) {
            throw invocationFailure("swe_version", throwable);
        }
    }

    private static IllegalStateException invocationFailure(String function, Throwable throwable) {
        return new IllegalStateException("FFM invocation of " + function + " failed", throwable);
    }
}
