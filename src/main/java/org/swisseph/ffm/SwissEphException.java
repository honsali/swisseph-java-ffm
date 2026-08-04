package org.swisseph.ffm;

/** Indicates a native loading, linking, or Swiss Ephemeris calculation error. */
public final class SwissEphException extends RuntimeException {
    private final String function;
    private final Integer returnCode;

    public SwissEphException(String message, Throwable cause) {
        super(message, cause);
        this.function = null;
        this.returnCode = null;
    }

    SwissEphException(String function, int returnCode, String nativeMessage) {
        super(function + " failed with code " + returnCode
                + (nativeMessage == null || nativeMessage.isBlank() ? "" : ": " + nativeMessage));
        this.function = function;
        this.returnCode = returnCode;
    }

    /** Native function involved in a calculation error, or {@code null}. */
    public String function() {
        return function;
    }

    /** Native return code, or {@code null} for loading/linking errors. */
    public Integer returnCode() {
        return returnCode;
    }
}
