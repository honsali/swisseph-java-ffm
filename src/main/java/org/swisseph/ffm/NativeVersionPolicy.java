package org.swisseph.ffm;

/** What to do when the loaded library reports an unsupported version. */
public enum NativeVersionPolicy {
    /**
     * Refuse to open the library. This is the default: the function descriptors
     * in this binding are written against one specific upstream tag, and a
     * signature change in another build corrupts the stack rather than failing
     * cleanly.
     */
    REJECT,
    /** Log a warning through {@link System.Logger} and continue. */
    WARN,
    /** Accept any version without checking. */
    ACCEPT
}
