package com.bayflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code bayflow.tenancy}. */
@ConfigurationProperties("bayflow.tenancy")
public class BayflowTenancyProperties {

    /**
     * Workshops live at {@code <slug>.<base domain>}; the base domain itself is the public site
     * (landing page and sign-up). {@code localhost} locally, e.g. {@code bayflow.app} on AWS.
     */
    private String baseDomain = "localhost";

    public String getBaseDomain() { return baseDomain; }
    public void setBaseDomain(String baseDomain) { this.baseDomain = baseDomain; }
}
