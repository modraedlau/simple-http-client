package org.modraedlau.http.message;

/**
 * Http response position
 *
 * @author modraedlau
 */
public enum Decoded {
    /**
     * Decoded status line
     */
    STATUS,

    /**
     * Decoded blank line
     */
    BLANK,

    /**
     * End of decode
     */
    END
}
