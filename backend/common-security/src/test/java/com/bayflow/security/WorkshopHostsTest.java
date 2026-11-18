package com.bayflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class WorkshopHostsTest {

    private final WorkshopHosts local = new WorkshopHosts("localhost");

    @Test
    void readsTheWorkshopLabel() {
        assertThat(local.slugOf("acme.localhost:3000")).contains("acme");
        assertThat(local.slugOf("Demo.LocalHost")).contains("demo");
        assertThat(local.slugOf("acme.localhost.")).contains("acme");
        assertThat(new WorkshopHosts("bayflow.app").slugOf("acme.bayflow.app")).contains("acme");
    }

    @Test
    void addressesWithoutAWorkshopGiveNone() {
        assertThat(local.slugOf("localhost:3000")).isEmpty();
        assertThat(local.slugOf("www.localhost")).isEmpty();
        assertThat(local.slugOf("a.b.localhost")).isEmpty();
        assertThat(local.slugOf("auth-service:8083")).isEmpty();
        assertThat(local.slugOf("evil-localhost")).isEmpty();
        assertThat(local.slugOf("acme.localhost.evil.com")).isEmpty();
        assertThat(local.slugOf("[::1]:8080")).isEmpty();
        assertThat(local.slugOf((String) null)).isEmpty();
        assertThat(local.slugOf("")).isEmpty();
    }

    @Test
    void prefersTheForwardedHostAndItsFirstValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Host", "auth-service:8083");
        assertThat(local.slugOf(request)).isEmpty();

        request.addHeader("X-Forwarded-Host", "acme.localhost:3000, proxy.internal");
        assertThat(local.slugOf(request)).contains("acme");
    }
}
