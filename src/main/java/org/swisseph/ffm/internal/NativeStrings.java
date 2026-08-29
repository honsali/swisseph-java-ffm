package org.swisseph.ffm.internal;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

/** Helpers for the {@code char *} conventions used by Swiss Ephemeris. */
public final class NativeStrings {
    private NativeStrings() {
    }

    /**
     * Reads a NUL-terminated string from a pointer returned by the native library.
     *
     * <p>Functions such as {@code swe_get_ayanamsa_name()} and
     * {@code swe_get_current_file_data()} return a pointer into a static buffer,
     * which reaches Java as a zero-length segment. It has to be reinterpreted
     * before it can be read; {@code maxBytes} bounds that window to the size the
     * C code actually declares, so a missing terminator cannot walk off into
     * unrelated memory.</p>
     *
     * @return the decoded string, or {@code null} when the native pointer is NULL
     */
    @SuppressWarnings("restricted") // reinterpret is bounded by the C buffer size
    public static String read(MemorySegment pointer, int maxBytes) {
        if (pointer == null || MemorySegment.NULL.equals(pointer)) {
            return null;
        }
        return pointer.reinterpret(maxBytes).getString(0);
    }

    /** Reads a string the native library wrote into a buffer we own. */
    public static String readBuffer(MemorySegment buffer) {
        return buffer.getString(0);
    }

    /**
     * Rejects strings that cannot survive the trip through a {@code char *}.
     *
     * @param maxBytes the largest UTF-8 encoding the native buffer can hold,
     *                 terminator excluded
     */
    public static String requireNativeSafe(String value, String name, int maxBytes) {
        if (value == null) {
            throw new NullPointerException(name);
        }
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " must not contain a NUL character");
        }
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        if (length >= maxBytes) {
            throw new IllegalArgumentException(
                    name + " must encode to fewer than " + maxBytes + " UTF-8 bytes, but was " + length);
        }
        return value;
    }
}
