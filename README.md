# Swiss Ephemeris Java FFM

### Developed with love by GPT-5.6 Sol ❤️

Java 25 bindings for the core API of [Swiss Ephemeris C 2.10.03](https://github.com/aloistr/swisseph/tree/v2.10.03), implemented with the Foreign Function and Memory API (FFM) and without JNI. This is an independent, unofficial binding and is not affiliated with Astrodienst AG.

## Status

The first binding layer covers:

- native-library loading and `swe_version()`;
- `swe_julday()`, `swe_revjul()`, and `swe_utc_to_jd()`;
- `swe_calc()` and `swe_calc_ut()`;
- `swe_get_planet_name()`;
- `swe_set_ephe_path()`, `swe_set_jpl_file()`, `swe_set_topo()`, and `swe_set_sid_mode()`.

The native Swiss Ephemeris binary and ephemeris data files are intentionally not bundled in the Java JAR.

## Requirements

- JDK 25;
- Maven 3.9 or newer;
- a shared library built from the exact upstream tag `v2.10.03`:
  - `swedll64.dll` on Windows;
  - `libswe.so` on Linux;
  - `libswe.dylib` on macOS.

Swiss Ephemeris is stateful. This library serializes native calls, but path, topocentric, and sidereal settings are process-wide. Prefer one `SwissEph` instance per application.

## Usage

```java
import org.swisseph.ffm.CalculationFlag;
import org.swisseph.ffm.CalendarType;
import org.swisseph.ffm.CelestialBody;
import org.swisseph.ffm.EphemerisPosition;
import org.swisseph.ffm.SwissEph;

try (SwissEph swe = SwissEph.loadConfigured()) {
    swe.setEphemerisPath("/opt/swisseph/ephe");

    double jd = swe.julianDay(2026, 8, 4, 12.0, CalendarType.GREGORIAN);
    EphemerisPosition sun = swe.calculateUt(
            jd,
            CelestialBody.SUN,
            CalculationFlag.SWISS_EPHEMERIS,
            CalculationFlag.SPEED);

    System.out.println(swe.version());
    System.out.println(sun.longitude());
}
```

Pass the native-library path and enable native access:

```shell
java --enable-native-access=ALL-UNNAMED \
  -Dswisseph.library.path=/absolute/path/to/libswe.so \
  -cp your-application.jar:swisseph-java-ffm.jar your.package.Main
```

On Windows PowerShell:

```powershell
java --enable-native-access=ALL-UNNAMED `
  "-Dswisseph.library.path=C:\absolute\path\to\swedll64.dll" `
  -cp "your-application.jar;swisseph-java-ffm.jar" your.package.Main
```

Alternatively, set the `SWISSEPH_LIBRARY` environment variable to the absolute library path.

## Build

```shell
mvn clean verify
```

Unit tests do not require a native library. Run the native integration test with:

```shell
mvn -Dswisseph.integration.library=/absolute/path/to/libswe.so \
  -DargLine=--enable-native-access=ALL-UNNAMED test
```

Without `swisseph.integration.library`, the native integration test is skipped.

## Publishing to GitHub Packages

The Maven coordinates are:

```text
org.swisseph:swisseph-java-ffm:0.1.0
```

Publishing is configured for the repository's GitHub Packages registry. Create
and publish a GitHub release with the tag `v0.1.0`, or run the **Publish Maven
package** workflow manually. The workflow builds with JDK 25 and authenticates
with its repository-scoped `GITHUB_TOKEN`; no personal token is stored in this
repository.

Increment the version in `pom.xml` before publishing another release because a
released Maven version is immutable.

## License

This project is licensed under AGPL-3.0. Swiss Ephemeris itself uses a dual-license model: AGPL-3.0 or the Swiss Ephemeris Professional License. Review the [upstream license terms](https://github.com/aloistr/swisseph/blob/v2.10.03/LICENSE) before distributing software or operating a service based on it.
