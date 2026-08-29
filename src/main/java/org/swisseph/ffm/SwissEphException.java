package org.swisseph.ffm;

/** Signals a native loading, linking, or Swiss Ephemeris calculation failure. */
public final class SwissEphException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String function;
    private final Integer returnCode;
    private final String nativeMessage;

    public SwissEphException(String message, Throwable cause) {
        super(message, cause);
        this.function = null;
        this.returnCode = null;
        this.nativeMessage = null;
    }

    public SwissEphException(String function, int returnCode, String nativeMessage) {
        super(function + " failed with code " + returnCode
                + (nativeMessage == null || nativeMessage.isBlank() ? "" : ": " + nativeMessage));
        this.function = function;
        this.returnCode = returnCode;
        this.nativeMessage = nativeMessage;
    }

    /** Native function involved in a calculation error, or {@code null}. */
    public String function() {
        return function;
    }

    /** Native return code, or {@code null} for loading and linking errors. */
    public Integer returnCode() {
        return returnCode;
    }

    /** Contents of the native {@code serr} buffer, or {@code null}. */
    public String nativeMessage() {
        return nativeMessage;
    }
}
