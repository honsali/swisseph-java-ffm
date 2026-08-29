package org.swisseph.ffm;

/**
 * Observer-dependent bits returned by the local eclipse functions.
 *
 * <p>These are output only: passing them as a search filter is meaningless.
 * The four contact bits carry different meanings for solar and lunar eclipses,
 * so both readings are documented on each constant.</p>
 */
public enum EclipseVisibility {
    /** {@code SE_ECL_VISIBLE}: the eclipse is visible from the given place. */
    VISIBLE(128),
    /** {@code SE_ECL_MAX_VISIBLE}: the moment of maximum eclipse is visible. */
    MAXIMUM_VISIBLE(256),
    /** {@code SE_ECL_1ST_VISIBLE}. Solar: first contact. Lunar: begin of the partial phase. */
    FIRST_CONTACT_VISIBLE(512),
    /** {@code SE_ECL_2ND_VISIBLE}. Solar: second contact. Lunar: begin of totality. */
    SECOND_CONTACT_VISIBLE(1_024),
    /** {@code SE_ECL_3RD_VISIBLE}. Solar: third contact. Lunar: end of totality. */
    THIRD_CONTACT_VISIBLE(2_048),
    /** {@code SE_ECL_4TH_VISIBLE}. Solar: fourth contact. Lunar: end of the partial phase. */
    FOURTH_CONTACT_VISIBLE(4_096),
    /** {@code SE_ECL_PENUMBBEG_VISIBLE}: begin of the penumbral phase, lunar eclipses. */
    PENUMBRAL_BEGIN_VISIBLE(8_192),
    /** {@code SE_ECL_PENUMBEND_VISIBLE}: end of the penumbral phase, lunar eclipses. */
    PENUMBRAL_END_VISIBLE(16_384);

    private final int mask;

    EclipseVisibility(int mask) {
        this.mask = mask;
    }

    public int value() {
        return mask;
    }
}
