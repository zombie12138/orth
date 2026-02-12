package com.xxl.job.core.constant;

/**
 * Core constants for the Orth executor framework.
 *
 * <p>Defines shared constants for OpenAPI authentication and executor registry timeouts.
 */
public class Const {

    // ---------------------- OpenAPI Authentication ----------------------

    /** HTTP header name for access token authentication */
    public static final String ORTH_ACCESS_TOKEN = "ORTH-ACCESS-TOKEN";

    // ---------------------- Executor Registry ----------------------

    /** Interval (seconds) between executor heartbeat registrations */
    public static final int BEAT_TIMEOUT = 30;

    /** Timeout (seconds) after which an executor is considered dead (3 missed heartbeats) */
    public static final int DEAD_TIMEOUT = BEAT_TIMEOUT * 3;

    private Const() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
