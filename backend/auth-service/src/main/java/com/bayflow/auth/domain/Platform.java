package com.bayflow.auth.domain;

import java.util.UUID;

/**
 * The Ramplight console's organization (V16): Ramplight's own team signs in here as
 * PLATFORM_ADMIN. It has no workshop address: sign-in reaches it only from the base domain with
 * the console client header.
 */
public final class Platform {

    public static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c0");
    public static final String SLUG = "ramplight-console";
    /** The X-Bayflow-Client value the console page sends. */
    public static final String CONSOLE_CLIENT = "console";

    private Platform() {
    }
}
