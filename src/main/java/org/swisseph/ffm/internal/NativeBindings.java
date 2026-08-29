package org.swisseph.ffm.internal;

import org.swisseph.ffm.SwissEphException;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Downcall handles for the supported subset of {@code swephexp.h}.
 *
 * <p>Every method must be invoked on the thread owned by {@link NativeContext}.
 * Swiss Ephemeris keeps its state in {@code TLS struct swe_data swed}, so
 * configuration and calculation only agree when they share a single thread.</p>
 *
 * <p>This class performs no validation and no allocation. It is an internal
 * implementation detail and is not exported by the module.</p>
 */
public final class NativeBindings {
    /** {@code AS_MAXCH}: the buffer size Swiss Ephemeris assumes for every {@code char *serr}. */
    public static final int AS_MAXCH = 256;
    /**
     * Size to allocate for any {@code char *} buffer we hand to the library.
     *
     * <p>One byte more than {@code AS_MAXCH}, because the C code does not always
     * keep the terminator inside it. {@code swe_get_library_path()} runs
     * {@code strncpy(s, name, AS_MAXCH)} and then {@code s[AS_MAXCH] = 0}, and
     * closes with {@code s[bytes] = 0} where {@code bytes} can itself reach
     * {@code AS_MAXCH}. Both write the 257th byte of a 256-byte allocation.</p>
     */
    public static final int TEXT_BUFFER_SIZE = AS_MAXCH + 1;
    /** {@code SE_MAX_STNAME}: the longest fixed-star name accepted as input. */
    public static final int MAX_STAR_NAME = 256;
    /**
     * Star buffers must hold twice {@code SE_MAX_STNAME}, as upstream documents,
     * because the resolved name is written back into the same buffer.
     *
     * <p>The write itself is far smaller: both write-back paths in {@code sweph.c}
     * emit {@code "<name>,<bayer>"} from fields clamped to
     * {@code SWI_STAR_LENGTH} (40), so at most 82 bytes ever land here. The
     * documented size is kept anyway, since it is the published contract.</p>
     */
    public static final int STAR_BUFFER_SIZE = 2 * MAX_STAR_NAME;

    private static final Linker LINKER = Linker.nativeLinker();

    private final MethodHandle sweVersion;
    private final MethodHandle sweGetLibraryPath;
    private final MethodHandle sweCalc;
    private final MethodHandle sweCalcUt;
    private final MethodHandle sweClose;
    private final MethodHandle sweSetEphePath;
    private final MethodHandle sweSetJplFile;
    private final MethodHandle sweGetPlanetName;
    private final MethodHandle sweSetTopo;
    private final MethodHandle sweSetSidMode;
    private final MethodHandle sweGetAyanamsaExUt;
    private final MethodHandle sweGetAyanamsaEx;
    private final MethodHandle sweGetAyanamsaName;
    private final MethodHandle sweGetCurrentFileData;
    private final MethodHandle sweJulday;
    private final MethodHandle sweRevjul;
    private final MethodHandle sweUtcToJd;
    private final MethodHandle sweJdetToUtc;
    private final MethodHandle sweJdut1ToUtc;
    private final MethodHandle sweUtcTimeZone;
    private final MethodHandle sweDeltatEx;
    private final MethodHandle sweSidtime;
    private final MethodHandle sweHousesEx;
    private final MethodHandle sweHousesArmc;
    private final MethodHandle sweHousePos;
    private final MethodHandle sweHouseName;
    private final MethodHandle sweAzalt;
    private final MethodHandle sweAzaltRev;
    private final MethodHandle sweFixstarUt;
    private final MethodHandle sweFixstar2Ut;
    private final MethodHandle sweFixstar2Mag;
    private final MethodHandle sweRiseTrans;
    private final MethodHandle sweSolEclipseWhenGlob;
    private final MethodHandle sweSolEclipseWhenLoc;
    private final MethodHandle sweSolEclipseWhere;
    private final MethodHandle sweSolEclipseHow;
    private final MethodHandle sweLunEclipseWhen;
    private final MethodHandle sweLunEclipseWhenLoc;
    private final MethodHandle sweLunEclipseHow;
    private final MethodHandle swePhenoUt;
    private final MethodHandle swePheno;
    private final MethodHandle sweSplitDeg;

    public NativeBindings(SymbolLookup symbols) {
        sweVersion = bind(symbols, "swe_version", FunctionDescriptor.of(ADDRESS, ADDRESS));
        sweGetLibraryPath = bind(symbols, "swe_get_library_path",
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        sweCalc = bind(symbols, "swe_calc", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
        sweCalcUt = bind(symbols, "swe_calc_ut", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
        sweClose = bind(symbols, "swe_close", FunctionDescriptor.ofVoid());
        sweSetEphePath = bind(symbols, "swe_set_ephe_path", FunctionDescriptor.ofVoid(ADDRESS));
        sweSetJplFile = bind(symbols, "swe_set_jpl_file", FunctionDescriptor.ofVoid(ADDRESS));
        sweGetPlanetName = bind(symbols, "swe_get_planet_name",
                FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS));
        sweSetTopo = bind(symbols, "swe_set_topo", FunctionDescriptor.ofVoid(
                JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE));
        sweSetSidMode = bind(symbols, "swe_set_sid_mode", FunctionDescriptor.ofVoid(
                JAVA_INT, JAVA_DOUBLE, JAVA_DOUBLE));
        sweGetAyanamsaExUt = bind(symbols, "swe_get_ayanamsa_ex_ut", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS));
        sweGetAyanamsaEx = bind(symbols, "swe_get_ayanamsa_ex", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS));
        sweGetAyanamsaName = bind(symbols, "swe_get_ayanamsa_name",
                FunctionDescriptor.of(ADDRESS, JAVA_INT));
        sweGetCurrentFileData = bind(symbols, "swe_get_current_file_data", FunctionDescriptor.of(
                ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        sweJulday = bind(symbols, "swe_julday", FunctionDescriptor.of(
                JAVA_DOUBLE, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_DOUBLE, JAVA_INT));
        sweRevjul = bind(symbols, "swe_revjul", FunctionDescriptor.ofVoid(
                JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        sweUtcToJd = bind(symbols, "swe_utc_to_jd", FunctionDescriptor.of(
                JAVA_INT,
                JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_DOUBLE, JAVA_INT,
                ADDRESS, ADDRESS));
        sweJdetToUtc = bind(symbols, "swe_jdet_to_utc", FunctionDescriptor.ofVoid(
                JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        sweJdut1ToUtc = bind(symbols, "swe_jdut1_to_utc", FunctionDescriptor.ofVoid(
                JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        sweUtcTimeZone = bind(symbols, "swe_utc_time_zone", FunctionDescriptor.ofVoid(
                JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_DOUBLE, JAVA_DOUBLE,
                ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        sweDeltatEx = bind(symbols, "swe_deltat_ex", FunctionDescriptor.of(
                JAVA_DOUBLE, JAVA_DOUBLE, JAVA_INT, ADDRESS));
        sweSidtime = bind(symbols, "swe_sidtime", FunctionDescriptor.of(JAVA_DOUBLE, JAVA_DOUBLE));
        sweHousesEx = bind(symbols, "swe_houses_ex", FunctionDescriptor.of(
                JAVA_INT,
                JAVA_DOUBLE, JAVA_INT, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS));
        sweHousesArmc = bind(symbols, "swe_houses_armc", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS));
        sweHousePos = bind(symbols, "swe_house_pos", FunctionDescriptor.of(
                JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS));
        sweHouseName = bind(symbols, "swe_house_name", FunctionDescriptor.of(ADDRESS, JAVA_INT));
        sweAzalt = bind(symbols, "swe_azalt", FunctionDescriptor.ofVoid(
                JAVA_DOUBLE, JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, ADDRESS, ADDRESS));
        sweAzaltRev = bind(symbols, "swe_azalt_rev", FunctionDescriptor.ofVoid(
                JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        sweFixstarUt = bind(symbols, "swe_fixstar_ut", FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS));
        sweFixstar2Ut = bind(symbols, "swe_fixstar2_ut", FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS));
        sweFixstar2Mag = bind(symbols, "swe_fixstar2_mag", FunctionDescriptor.of(
                JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        sweRiseTrans = bind(symbols, "swe_rise_trans", FunctionDescriptor.of(
                JAVA_INT,
                JAVA_DOUBLE, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS,
                JAVA_DOUBLE, JAVA_DOUBLE, ADDRESS, ADDRESS));
        sweSolEclipseWhenGlob = bind(symbols, "swe_sol_eclipse_when_glob", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        sweSolEclipseWhenLoc = bind(symbols, "swe_sol_eclipse_when_loc", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
        sweSolEclipseWhere = bind(symbols, "swe_sol_eclipse_where", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        sweSolEclipseHow = bind(symbols, "swe_sol_eclipse_how", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        sweLunEclipseWhen = bind(symbols, "swe_lun_eclipse_when", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        sweLunEclipseWhenLoc = bind(symbols, "swe_lun_eclipse_when_loc", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
        sweLunEclipseHow = bind(symbols, "swe_lun_eclipse_how", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        swePhenoUt = bind(symbols, "swe_pheno_ut", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
        swePheno = bind(symbols, "swe_pheno", FunctionDescriptor.of(
                JAVA_INT, JAVA_DOUBLE, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
        sweSplitDeg = bind(symbols, "swe_split_deg", FunctionDescriptor.ofVoid(
                JAVA_DOUBLE, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    }

    public void version(MemorySegment buffer) {
        try {
            MemorySegment ignored = (MemorySegment) sweVersion.invokeExact(buffer);
        } catch (Throwable throwable) {
            throw fail("swe_version", throwable);
        }
    }

    public void libraryPath(MemorySegment buffer) {
        try {
            MemorySegment ignored = (MemorySegment) sweGetLibraryPath.invokeExact(buffer);
        } catch (Throwable throwable) {
            throw fail("swe_get_library_path", throwable);
        }
    }

    public int calc(double julianDay, int bodyId, int flags, MemorySegment result, MemorySegment error) {
        try {
            return (int) sweCalc.invokeExact(julianDay, bodyId, flags, result, error);
        } catch (Throwable throwable) {
            throw fail("swe_calc", throwable);
        }
    }

    public int calcUt(double julianDay, int bodyId, int flags, MemorySegment result, MemorySegment error) {
        try {
            return (int) sweCalcUt.invokeExact(julianDay, bodyId, flags, result, error);
        } catch (Throwable throwable) {
            throw fail("swe_calc_ut", throwable);
        }
    }

    public void close() {
        try {
            sweClose.invokeExact();
        } catch (Throwable throwable) {
            throw fail("swe_close", throwable);
        }
    }

    public void setEphemerisPath(MemorySegment path) {
        try {
            sweSetEphePath.invokeExact(path);
        } catch (Throwable throwable) {
            throw fail("swe_set_ephe_path", throwable);
        }
    }

    public void setJplFile(MemorySegment fileName) {
        try {
            sweSetJplFile.invokeExact(fileName);
        } catch (Throwable throwable) {
            throw fail("swe_set_jpl_file", throwable);
        }
    }

    public void planetName(int bodyId, MemorySegment nameBuffer) {
        try {
            MemorySegment ignored = (MemorySegment) sweGetPlanetName.invokeExact(bodyId, nameBuffer);
        } catch (Throwable throwable) {
            throw fail("swe_get_planet_name", throwable);
        }
    }

    public void setTopocentricPosition(double longitude, double latitude, double altitudeMeters) {
        try {
            sweSetTopo.invokeExact(longitude, latitude, altitudeMeters);
        } catch (Throwable throwable) {
            throw fail("swe_set_topo", throwable);
        }
    }

    public void setSiderealMode(int mode, double t0, double ayanamsaAtT0) {
        try {
            sweSetSidMode.invokeExact(mode, t0, ayanamsaAtT0);
        } catch (Throwable throwable) {
            throw fail("swe_set_sid_mode", throwable);
        }
    }

    public int ayanamsaUt(double julianDayUt, int flags, MemorySegment result, MemorySegment error) {
        try {
            return (int) sweGetAyanamsaExUt.invokeExact(julianDayUt, flags, result, error);
        } catch (Throwable throwable) {
            throw fail("swe_get_ayanamsa_ex_ut", throwable);
        }
    }

    public int ayanamsa(double julianDayEt, int flags, MemorySegment result, MemorySegment error) {
        try {
            return (int) sweGetAyanamsaEx.invokeExact(julianDayEt, flags, result, error);
        } catch (Throwable throwable) {
            throw fail("swe_get_ayanamsa_ex", throwable);
        }
    }

    public MemorySegment ayanamsaName(int siderealMode) {
        try {
            return (MemorySegment) sweGetAyanamsaName.invokeExact(siderealMode);
        } catch (Throwable throwable) {
            throw fail("swe_get_ayanamsa_name", throwable);
        }
    }

    public MemorySegment currentFileData(int slot, MemorySegment start, MemorySegment end,
                                         MemorySegment jplNumber) {
        try {
            return (MemorySegment) sweGetCurrentFileData.invokeExact(slot, start, end, jplNumber);
        } catch (Throwable throwable) {
            throw fail("swe_get_current_file_data", throwable);
        }
    }

    public double julianDay(int year, int month, int day, double hour, int calendar) {
        try {
            return (double) sweJulday.invokeExact(year, month, day, hour, calendar);
        } catch (Throwable throwable) {
            throw fail("swe_julday", throwable);
        }
    }

    public void reverseJulianDay(double julianDay, int calendar, MemorySegment year,
                                 MemorySegment month, MemorySegment day, MemorySegment hour) {
        try {
            sweRevjul.invokeExact(julianDay, calendar, year, month, day, hour);
        } catch (Throwable throwable) {
            throw fail("swe_revjul", throwable);
        }
    }

    public int utcToJulianDay(int year, int month, int day, int hour, int minute, double second,
                              int calendar, MemorySegment result, MemorySegment error) {
        try {
            return (int) sweUtcToJd.invokeExact(
                    year, month, day, hour, minute, second, calendar, result, error);
        } catch (Throwable throwable) {
            throw fail("swe_utc_to_jd", throwable);
        }
    }

    public void ephemerisTimeToUtc(double julianDayEt, int calendar, MemorySegment year,
                                   MemorySegment month, MemorySegment day, MemorySegment hour,
                                   MemorySegment minute, MemorySegment second) {
        try {
            sweJdetToUtc.invokeExact(julianDayEt, calendar, year, month, day, hour, minute, second);
        } catch (Throwable throwable) {
            throw fail("swe_jdet_to_utc", throwable);
        }
    }

    public void universalTimeToUtc(double julianDayUt, int calendar, MemorySegment year,
                                   MemorySegment month, MemorySegment day, MemorySegment hour,
                                   MemorySegment minute, MemorySegment second) {
        try {
            sweJdut1ToUtc.invokeExact(julianDayUt, calendar, year, month, day, hour, minute, second);
        } catch (Throwable throwable) {
            throw fail("swe_jdut1_to_utc", throwable);
        }
    }

    public void applyTimeZone(int year, int month, int day, int hour, int minute, double second,
                              double timeZoneOffsetHours, MemorySegment yearOut,
                              MemorySegment monthOut, MemorySegment dayOut, MemorySegment hourOut,
                              MemorySegment minuteOut, MemorySegment secondOut) {
        try {
            sweUtcTimeZone.invokeExact(year, month, day, hour, minute, second, timeZoneOffsetHours,
                    yearOut, monthOut, dayOut, hourOut, minuteOut, secondOut);
        } catch (Throwable throwable) {
            throw fail("swe_utc_time_zone", throwable);
        }
    }

    public double deltaTEx(double julianDay, int ephemerisFlags, MemorySegment error) {
        try {
            return (double) sweDeltatEx.invokeExact(julianDay, ephemerisFlags, error);
        } catch (Throwable throwable) {
            throw fail("swe_deltat_ex", throwable);
        }
    }

    public double siderealTime(double julianDayUt) {
        try {
            return (double) sweSidtime.invokeExact(julianDayUt);
        } catch (Throwable throwable) {
            throw fail("swe_sidtime", throwable);
        }
    }

    public int housesEx(double julianDayUt, int flags, double latitude, double longitude,
                        int houseSystem, MemorySegment cusps, MemorySegment additionalPoints) {
        try {
            return (int) sweHousesEx.invokeExact(
                    julianDayUt, flags, latitude, longitude, houseSystem, cusps, additionalPoints);
        } catch (Throwable throwable) {
            throw fail("swe_houses_ex", throwable);
        }
    }

    public int housesArmc(double armc, double latitude, double eclipticObliquity, int houseSystem,
                          MemorySegment cusps, MemorySegment additionalPoints) {
        try {
            return (int) sweHousesArmc.invokeExact(
                    armc, latitude, eclipticObliquity, houseSystem, cusps, additionalPoints);
        } catch (Throwable throwable) {
            throw fail("swe_houses_armc", throwable);
        }
    }

    public double housePosition(double armc, double latitude, double eclipticObliquity,
                                int houseSystem, MemorySegment input, MemorySegment error) {
        try {
            return (double) sweHousePos.invokeExact(
                    armc, latitude, eclipticObliquity, houseSystem, input, error);
        } catch (Throwable throwable) {
            throw fail("swe_house_pos", throwable);
        }
    }

    public MemorySegment houseName(int houseSystem) {
        try {
            return (MemorySegment) sweHouseName.invokeExact(houseSystem);
        } catch (Throwable throwable) {
            throw fail("swe_house_name", throwable);
        }
    }

    public void azalt(double julianDayUt, int coordinateType, MemorySegment geographicPosition,
                      double pressure, double temperature, MemorySegment input,
                      MemorySegment result) {
        try {
            sweAzalt.invokeExact(julianDayUt, coordinateType, geographicPosition, pressure,
                    temperature, input, result);
        } catch (Throwable throwable) {
            throw fail("swe_azalt", throwable);
        }
    }

    public void azaltReverse(double julianDayUt, int coordinateType,
                             MemorySegment geographicPosition, MemorySegment input,
                             MemorySegment result) {
        try {
            sweAzaltRev.invokeExact(julianDayUt, coordinateType, geographicPosition, input, result);
        } catch (Throwable throwable) {
            throw fail("swe_azalt_rev", throwable);
        }
    }

    public int fixedStarUt(MemorySegment star, double julianDayUt, int flags,
                           MemorySegment result, MemorySegment error) {
        try {
            return (int) sweFixstarUt.invokeExact(star, julianDayUt, flags, result, error);
        } catch (Throwable throwable) {
            throw fail("swe_fixstar_ut", throwable);
        }
    }

    public int fixedStar2Ut(MemorySegment star, double julianDayUt, int flags,
                            MemorySegment result, MemorySegment error) {
        try {
            return (int) sweFixstar2Ut.invokeExact(star, julianDayUt, flags, result, error);
        } catch (Throwable throwable) {
            throw fail("swe_fixstar2_ut", throwable);
        }
    }

    public int fixedStar2Magnitude(MemorySegment star, MemorySegment magnitude, MemorySegment error) {
        try {
            return (int) sweFixstar2Mag.invokeExact(star, magnitude, error);
        } catch (Throwable throwable) {
            throw fail("swe_fixstar2_mag", throwable);
        }
    }

    public int riseTransit(double julianDayUt, int bodyId, MemorySegment star, int ephemerisFlags,
                           int eventFlags, MemorySegment geographicPosition, double pressure,
                           double temperature, MemorySegment result, MemorySegment error) {
        try {
            return (int) sweRiseTrans.invokeExact(julianDayUt, bodyId, star, ephemerisFlags,
                    eventFlags, geographicPosition, pressure, temperature, result, error);
        } catch (Throwable throwable) {
            throw fail("swe_rise_trans", throwable);
        }
    }

    public int solarEclipseWhenGlobal(double startJulianDayUt, int flags, int eclipseTypeFlags,
                                      MemorySegment times, int backward, MemorySegment error) {
        try {
            return (int) sweSolEclipseWhenGlob.invokeExact(
                    startJulianDayUt, flags, eclipseTypeFlags, times, backward, error);
        } catch (Throwable throwable) {
            throw fail("swe_sol_eclipse_when_glob", throwable);
        }
    }

    public int solarEclipseWhenLocal(double startJulianDayUt, int flags,
                                     MemorySegment geographicPosition, MemorySegment times,
                                     MemorySegment attributes, int backward, MemorySegment error) {
        try {
            return (int) sweSolEclipseWhenLoc.invokeExact(
                    startJulianDayUt, flags, geographicPosition, times, attributes, backward, error);
        } catch (Throwable throwable) {
            throw fail("swe_sol_eclipse_when_loc", throwable);
        }
    }

    public int solarEclipseWhere(double julianDayUt, int flags, MemorySegment geographicPositions,
                                 MemorySegment attributes, MemorySegment error) {
        try {
            return (int) sweSolEclipseWhere.invokeExact(
                    julianDayUt, flags, geographicPositions, attributes, error);
        } catch (Throwable throwable) {
            throw fail("swe_sol_eclipse_where", throwable);
        }
    }

    public int solarEclipseHow(double julianDayUt, int flags, MemorySegment geographicPosition,
                               MemorySegment attributes, MemorySegment error) {
        try {
            return (int) sweSolEclipseHow.invokeExact(
                    julianDayUt, flags, geographicPosition, attributes, error);
        } catch (Throwable throwable) {
            throw fail("swe_sol_eclipse_how", throwable);
        }
    }

    public int lunarEclipseWhen(double startJulianDayUt, int flags, int eclipseTypeFlags,
                                MemorySegment times, int backward, MemorySegment error) {
        try {
            return (int) sweLunEclipseWhen.invokeExact(
                    startJulianDayUt, flags, eclipseTypeFlags, times, backward, error);
        } catch (Throwable throwable) {
            throw fail("swe_lun_eclipse_when", throwable);
        }
    }

    public int lunarEclipseWhenLocal(double startJulianDayUt, int flags,
                                     MemorySegment geographicPosition, MemorySegment times,
                                     MemorySegment attributes, int backward, MemorySegment error) {
        try {
            return (int) sweLunEclipseWhenLoc.invokeExact(
                    startJulianDayUt, flags, geographicPosition, times, attributes, backward, error);
        } catch (Throwable throwable) {
            throw fail("swe_lun_eclipse_when_loc", throwable);
        }
    }

    public int lunarEclipseHow(double julianDayUt, int flags, MemorySegment geographicPosition,
                               MemorySegment attributes, MemorySegment error) {
        try {
            return (int) sweLunEclipseHow.invokeExact(
                    julianDayUt, flags, geographicPosition, attributes, error);
        } catch (Throwable throwable) {
            throw fail("swe_lun_eclipse_how", throwable);
        }
    }

    public int phenomenaUt(double julianDayUt, int bodyId, int flags, MemorySegment attributes,
                           MemorySegment error) {
        try {
            return (int) swePhenoUt.invokeExact(julianDayUt, bodyId, flags, attributes, error);
        } catch (Throwable throwable) {
            throw fail("swe_pheno_ut", throwable);
        }
    }

    public int phenomena(double julianDayEt, int bodyId, int flags, MemorySegment attributes,
                         MemorySegment error) {
        try {
            return (int) swePheno.invokeExact(julianDayEt, bodyId, flags, attributes, error);
        } catch (Throwable throwable) {
            throw fail("swe_pheno", throwable);
        }
    }

    public void splitDegrees(double degrees, int roundFlags, MemorySegment degreePart,
                             MemorySegment minutePart, MemorySegment secondPart,
                             MemorySegment secondFraction, MemorySegment sign) {
        try {
            sweSplitDeg.invokeExact(degrees, roundFlags, degreePart, minutePart, secondPart,
                    secondFraction, sign);
        } catch (Throwable throwable) {
            throw fail("swe_split_deg", throwable);
        }
    }

    @SuppressWarnings("restricted") // downcallHandle is the whole point of this class
    private static MethodHandle bind(SymbolLookup symbols, String name,
                                     FunctionDescriptor descriptor) {
        MemorySegment symbol = symbols.find(name)
                .orElseThrow(() -> new UnsatisfiedLinkError("Missing Swiss Ephemeris symbol: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    /**
     * Converts the {@code Throwable} that {@code invokeExact} declares into
     * something callers can reason about.
     *
     * <p>{@link Error}s (a {@code VirtualMachineError} from a corrupted stack, a
     * {@code LinkageError} from a missing symbol) and {@link RuntimeException}s
     * raised by the FFM runtime itself ({@code WrongThreadException}, or
     * {@code IllegalStateException} on a closed arena) are rethrown untouched:
     * folding them into a generic wrapper would hide exactly the failures that
     * matter most. Only the residual checked throwables are wrapped.</p>
     */
    private static RuntimeException fail(String function, Throwable throwable) {
        switch (throwable) {
            case Error error -> throw error;
            case RuntimeException runtime -> throw runtime;
            default -> {
                return new SwissEphException("FFM invocation of " + function + " failed", throwable);
            }
        }
    }
}
