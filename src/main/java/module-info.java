/**
 * Java bindings for the Swiss Ephemeris C API, implemented with the Foreign
 * Function and Memory API.
 *
 * <p>The {@code org.swisseph.ffm.internal} package holds the raw downcall
 * handles and the native execution context. It is deliberately not exported:
 * callers must go through {@link org.swisseph.ffm.SwissEph}, which owns the
 * lifecycle, the argument validation, and the dedicated native thread.</p>
 *
 * <p>Applications must grant native access to this module, for example with
 * {@code --enable-native-access=org.swisseph.ffm} on the module path, or
 * {@code --enable-native-access=ALL-UNNAMED} on the class path.</p>
 */
module org.swisseph.ffm {
    exports org.swisseph.ffm;
}
