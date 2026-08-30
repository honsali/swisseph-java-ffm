# Swiss Ephemeris Java FFM

Java 25 bindings for [Swiss Ephemeris C 2.10.03](https://github.com/aloistr/swisseph/tree/v2.10.03),
built on the Foreign Function and Memory API. No JNI, no bundled binaries.

This is an independent, unofficial binding and is not affiliated with Astrodienst AG.

```java
try (SwissEph swe = SwissEph.open(SwissEphConfig.builder()
        .library(Path.of("/opt/swisseph/libswe.so"))
        .ephemerisPath(Path.of("/opt/swisseph/ephe"))
        .build())) {

    double jd = swe.julianDay(2026, 8, 4, 12.0, CalendarType.GREGORIAN);

    EphemerisPosition sun = swe.calculateUt(jd, CelestialBody.SUN,
            CalculationFlag.SWISS_EPHEMERIS, CalculationFlag.SPEED);
    System.out.println(sun.longitude() + " moving " + sun.longitudeSpeed() + " deg/day");

    HouseCusps houses = swe.houses(jd, GeographicPosition.of(-7.5898, 33.5731),
            HouseSystem.PLACIDUS);
    System.out.println("Ascendant " + houses.ascendant());
}
```

## Two things worth knowing before you start

**Swiss Ephemeris state is thread-local.** Upstream declares it as
`extern TLS struct swe_data swed`, where `TLS` expands to `__thread` on GCC and
Clang and `__declspec(thread)` on MSVC. Configuring the ephemeris path on one
thread and calculating on another reads a *different* `swed` on those builds,
and the same one on a build compiled without thread-local support. This binding
runs every native call on one dedicated platform thread, so the two cases become
indistinguishable and the behaviour is the same everywhere. Calls are serialized
as a consequence; `SwissEph` is safe to use from any number of Java threads.

**The C library has one set of settings, and they are global.** The ephemeris
path, JPL file, topocentric observer, and sidereal mode are shared by everything
running against that library. Handles are reference counted, so closing one no
longer breaks another, but changing a setting still affects everyone.
`swe.settings()` reports what is currently applied.

## Requirements

- JDK 25
- Maven 3.9 or newer
- a Swiss Ephemeris shared library, built from tag `v2.10.03`
- Swiss Ephemeris data files, if you want anything better than the built-in
  Moshier ephemeris

The version reported by `swe_version()` is checked when the library is opened,
and an unsupported build is refused. That is deliberate: the function
descriptors in this binding are written against one upstream tag, and a
signature change in another build corrupts the stack rather than failing
cleanly. Override with `versionPolicy(...)` if you know a build is compatible.

## Building the native library

No binaries are shipped here. Swiss Ephemeris is dual licensed, its build
differs per platform and compiler, and a JAR that bundled one particular build
would decide both the licence and the numerical behaviour on your behalf.

```bash
git clone --branch v2.10.03 --depth 1 https://github.com/aloistr/swisseph.git
cd swisseph
```

Library sources are the `SWEOBJ` list from the upstream `Makefile`; `swetest.c`,
`swemini.c`, and `swevents.c` are excluded because they define `main()`.

```bash
SOURCES="swedate.c swehouse.c swejpl.c swemmoon.c swemplan.c sweph.c swephlib.c swecl.c swehel.c"

# Linux
gcc -O2 -fPIC -shared -o libswe.so   $SOURCES -lm -ldl
# macOS
cc  -O2 -fPIC -dynamiclib -o libswe.dylib $SOURCES -lm
# Windows, MinGW
gcc -O2 -shared -o swe.dll $SOURCES -lm
```

Data files come from the upstream `ephe` directory. `sepl_18.se1` and
`semo_18.se1` cover the planets and the Moon for 1800–2399 and are enough for
most applications; `seas_18.se1` adds Ceres, Pallas, Juno and Vesta, and
`sefstars.txt` is the fixed-star catalogue.

```bash
# Pinned to the same commit the CI uses, so the data cannot shift underfoot.
base=https://raw.githubusercontent.com/aloistr/swisseph/3fd0f956d73898b91cc4f67cf18b21af656d1342/ephe
mkdir -p ephe && cd ephe
for f in sepl_18.se1 semo_18.se1 seas_18.se1 sefstars.txt; do curl -fLO "$base/$f"; done
```

## Finding the library at runtime

`SwissEph.open()` resolves the library through `NativeLibraryLocator`, which
tries these sources in order and stops at the first hit:

| Order | Source | Meaning |
| --- | --- | --- |
| 1 | `SwissEphConfig.Builder.library(Path)` | exact file, skips the search |
| 2 | `-Dswisseph.library.path=...` | exact file |
| 3 | `SWISSEPH_LIBRARY` | exact file |
| 4 | `-Dswisseph.library.dir=...` or `SWISSEPH_LIBRARY_DIR` | directory to search |
| 5 | `java.library.path` | each entry searched |

Directories are searched for the platform file names: `swe.dll`,
`swedll64.dll`, `libswe.dll` on Windows; `libswe.dylib`, `libswe.so` on macOS;
`libswe.so`, `swe.so` elsewhere. When nothing is found, the failure lists every
candidate that was tried.

## Running

Native access must be granted to the module:

```bash
java --enable-native-access=org.swisseph.ffm \
  -Dswisseph.library.path=/absolute/path/to/libswe.so \
  --module-path app.jar:swisseph-java-ffm.jar --module your.app/your.package.Main
```

On the class path, grant it to the unnamed module instead:

```bash
java --enable-native-access=ALL-UNNAMED \
  -Dswisseph.library.path=/absolute/path/to/libswe.so \
  -cp your-application.jar:swisseph-java-ffm.jar your.package.Main
```

```powershell
java --enable-native-access=ALL-UNNAMED `
  "-Dswisseph.library.path=C:\absolute\path\to\swe.dll" `
  -cp "your-application.jar;swisseph-java-ffm.jar" your.package.Main
```

## API coverage

**Time** — `swe_julday`, `swe_revjul`, `swe_utc_to_jd`, `swe_jdet_to_utc`,
`swe_jdut1_to_utc`, `swe_utc_time_zone`, `swe_deltat_ex`, `swe_sidtime`

**Positions** — `swe_calc`, `swe_calc_ut`, `swe_fixstar_ut`, `swe_fixstar2_ut`,
`swe_fixstar2_mag`, `swe_pheno`, `swe_pheno_ut`, `swe_get_ayanamsa_ex`,
`swe_get_ayanamsa_ex_ut`

**Houses** — `swe_houses_ex`, `swe_houses_armc`, `swe_house_pos`, `swe_house_name`

**Horizon** — `swe_azalt`, `swe_azalt_rev`, `swe_rise_trans`

**Eclipses** — `swe_sol_eclipse_when_glob`, `swe_sol_eclipse_when_loc`,
`swe_sol_eclipse_where`, `swe_sol_eclipse_how`, `swe_lun_eclipse_when`,
`swe_lun_eclipse_when_loc`, `swe_lun_eclipse_how`

**Settings and diagnostics** — `swe_set_ephe_path`, `swe_set_jpl_file`,
`swe_set_topo`, `swe_set_sid_mode`, `swe_version`, `swe_get_library_path`,
`swe_get_planet_name`, `swe_get_ayanamsa_name`, `swe_get_current_file_data`

**Utilities** — `swe_split_deg`

Each eclipse function returns its own result type, because their `tret[]` and
`attr[]` layouts differ. Reading a lunar result through the solar accessors
would give the right numbers under the wrong names, so the types make that
impossible.

## Knowing what you actually got

Swiss Ephemeris does not fail when a data file is missing. It falls back to
another ephemeris and reports the substitution only through the returned flags.
`ReturnedFlags` is therefore a different type from `CalculationFlag`:

```java
EphemerisPosition sun = swe.calculateUt(jd, CelestialBody.SUN,
        CalculationFlag.SWISS_EPHEMERIS);

if (!sun.returnedFlags().used(Ephemeris.SWISS)) {
    // Fell back to Moshier: the data files were not found.
    log.warn("ephemeris downgraded: {}", sun.warning());
}
```

`swe.currentFiles()` goes further and names the files actually open:

```java
swe.currentFile(EphemerisFileSlot.PLANET)
   .ifPresent(file -> log.info("planets from {} (DE{})", file.path(), file.jplEphemerisNumber()));
```

The same idea applies to house systems. `swe_houses_ex` fills the cusps from a
substitute system when it cannot honour the request — Porphyry standing in for
Placidus beyond the polar circles — so the cusps are returned along with
`requestedSystemUsed()`:

```java
HouseCusps houses = swe.houses(jd, GeographicPosition.of(15.0, 78.0), HouseSystem.PLACIDUS);
if (!houses.requestedSystemUsed()) {
    log.warn("Placidus is undefined here; these cusps come from another system");
}
```

## Build and test

```bash
mvn clean verify
```

Unit tests need no native library. The native suite is skipped without one:

```bash
mvn verify \
  -Dswisseph.integration.library=/absolute/path/to/libswe.so \
  -Dswisseph.integration.ephemeris=/absolute/path/to/ephe
```

CI builds Swiss Ephemeris from the upstream tag and runs the whole suite on
Linux, macOS, and Windows, and the release pipeline will not publish until that
has passed. The suite asserts against values that can be checked independently
rather than against `isFinite`: the Sun stands at longitude 0 at the March 2000
equinox, greatest eclipse on 11 August 1999 fell at 11:03 UT over Romania,
Sirius sits near 14° Cancer. A binding with a wrong function descriptor does not
crash, it returns a plausible number, and only anchored assertions catch that.

The constant tables are cross-checked against the loaded library too: every
`SiderealMode` must name a distinct ayanamsha through `swe_get_ayanamsa_name()`,
and every `HouseSystem` must be recognised by `swe_house_name()`.

## Versioning

From 1.0.0 the published API follows semantic versioning: a minor release adds,
a patch release fixes, and anything that breaks compilation or changes what a
call returns waits for a major one.

Two things sit outside that promise, because they are not this project's to
make. The supported native versions are whatever
`SwissEphConfig.DEFAULT_SUPPORTED_VERSIONS` lists, and following a new upstream
release may change results without changing this API. And
`org.swisseph.ffm.internal` is not exported: it is an implementation detail and
moves freely.

## Publishing

```text
org.swisseph:swisseph-java-ffm:1.0.0
```

Published to this repository's GitHub Packages registry. Create a release
tagged `v1.0.0`, or run the **Publish Maven package** workflow manually. Builds
are reproducible: bump `project.build.outputTimestamp` alongside the version.

Note that consuming from GitHub Packages requires a GitHub token in the
consumer's `settings.xml`, even for a public artifact.

## License

AGPL-3.0. Swiss Ephemeris itself is dual licensed: AGPL-3.0 or the Swiss
Ephemeris Professional License. Review the
[upstream terms](https://github.com/aloistr/swisseph/blob/v2.10.03/LICENSE)
before distributing software or operating a service based on it.

## Credits

Developed with assistance from GPT-5.6 Sol and Claude.
