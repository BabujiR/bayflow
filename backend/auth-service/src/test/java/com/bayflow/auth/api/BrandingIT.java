package com.bayflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.domain.Role;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/**
 * Workshop branding: only the workshop's ADMIN changes it, every visitor of the workshop's
 * address sees it, images are checked and re-encoded, and no workshop sees another's.
 * Requests without X-Forwarded-Host go to "demo" (the test fallback workshop).
 */
class BrandingIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";
    private static final UUID OTHER = UUID.fromString("0c0c0c0c-0000-4000-8000-0000000000b1");
    private static final String OTHER_HOST = "branding-other.localhost:3000";

    private final JdbcTemplate jdbc = ownerJdbc();

    @BeforeEach
    void cleanBranding() {
        jdbc.update("""
                insert into bayflow.auth_tenants (id, slug, name, status, created_at, updated_at)
                values (?, 'branding-other', 'Kiwi Motors', 'ACTIVE', now(), now())
                on conflict (id) do nothing""", OTHER);
        jdbc.update("delete from bayflow.auth_tenant_assets");
        jdbc.update("delete from bayflow.auth_tenant_announcements");
        jdbc.update("delete from bayflow.auth_tenant_branding");
    }

    @Test
    void aWorkshopWithoutBrandingLooksLikeBayflow() throws Exception {
        mvc.perform(get("/api/v1/tenants/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branding.logoUrl").value(nullValue()))
                .andExpect(jsonPath("$.branding.coverUrl").value(nullValue()))
                .andExpect(jsonPath("$.branding.primaryColor").value(nullValue()))
                .andExpect(jsonPath("$.branding.announcements").isEmpty())
                .andExpect(jsonPath("$.branding.showPoweredBy").value(true));
        mvc.perform(get("/api/v1/tenants/current/logo")).andExpect(status().isNotFound());
    }

    @Test
    void anAdminSetsColourAndPoweredByAndEveryoneSeesThem() throws Exception {
        String admin = tokenFor(Role.ADMIN);

        update(admin, body("#1D4ED8", false, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryColor").value("#1d4ed8"))
                .andExpect(jsonPath("$.showPoweredBy").value(false))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(get("/api/v1/tenants/current"))
                .andExpect(jsonPath("$.branding.primaryColor").value("#1d4ed8"))
                .andExpect(jsonPath("$.branding.showPoweredBy").value(false));

        // Empty values go back to Bayflow's look.
        update(admin, body("", true, 1)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/tenants/current"))
                .andExpect(jsonPath("$.branding.primaryColor").value(nullValue()))
                .andExpect(jsonPath("$.branding.announcements").isEmpty())
                .andExpect(jsonPath("$.branding.showPoweredBy").value(true));
    }

    @Test
    void onlyAnAdminChangesTheBranding() throws Exception {
        for (Role role : new Role[] { Role.CUSTOMER, Role.SERVICE_ADVISOR, Role.MECHANIC, Role.MANAGER }) {
            String token = tokenFor(role);
            mvc.perform(get("/api/v1/auth/tenant/branding").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            update(token, body("#000000", false, 0))
                    .andExpect(status().isForbidden());
            upload(token, "logo", ImageFixtures.png(64, 64)).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/auth/tenant/branding/logo").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        update(null, body("#000000", false, 0)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/tenants/current")).andExpect(jsonPath("$.branding.showPoweredBy").value(true));
    }

    @Test
    void invalidBrandingIsRejected() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        update(admin, body("blue", true, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-color"));
        update(admin, body("#12345g", true, 0))
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-color"));
        update(admin, """
                {"primaryColor":null,"showPoweredBy":true}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void aStaleVersionIsAConflict() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        update(admin, body("#111111", true, 0)).andExpect(status().isOk());
        update(admin, body("#222222", true, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:concurrent-update"));
    }

    @Test
    void anUploadedLogoIsReEncodedScaledAndServedWithCaching() throws Exception {
        String admin = tokenFor(Role.ADMIN);

        String body = upload(admin, "logo", ImageFixtures.png(1024, 256))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value(startsWith("/api/v1/tenants/current/logo?v=")))
                .andReturn().getResponse().getContentAsString();
        String logoUrl = JsonPath.read(body, "$.logoUrl");
        String version = logoUrl.substring(logoUrl.indexOf("?v=") + 3);

        mvc.perform(get("/api/v1/tenants/current"))
                .andExpect(jsonPath("$.branding.logoUrl").value(logoUrl));

        // Unauthenticated, like an <img> on the sign-in page.
        MvcResult image = mvc.perform(get("/api/v1/tenants/current/logo").param("v", version))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(image.getResponse().getContentAsByteArray()));
        assertThat(stored.getWidth()).isEqualTo(512);
        assertThat(stored.getHeight()).isEqualTo(128);

        String etag = image.getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank();
        mvc.perform(get("/api/v1/tenants/current/logo")).andExpect(header().string("Cache-Control",
                containsString("no-cache")));
        mvc.perform(get("/api/v1/tenants/current/logo").header("If-None-Match", etag))
                .andExpect(status().isNotModified());

        // A stale token sent by the browser does not stop the image from loading.
        mvc.perform(get("/api/v1/tenants/current/logo").header("Authorization", "Bearer stale"))
                .andExpect(status().isOk());
    }

    @Test
    void aCoverPhotoLosesItsMetadata() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        byte[] jpeg = ImageFixtures.jpegWithExif(2400, 1200);

        upload(admin, "cover", jpeg)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverUrl").value(startsWith("/api/v1/tenants/current/cover?v=")));

        byte[] served = mvc.perform(get("/api/v1/tenants/current/cover"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(served, StandardCharsets.ISO_8859_1)).doesNotContain("Exif", "GPS-SECRET");
        assertThat(ImageIO.read(new ByteArrayInputStream(served)).getWidth()).isEqualTo(2000);
    }

    @Test
    void unsafeOrUnsupportedFilesAreRefused() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8);

        upload(admin, "logo", svg, "logo.png", "image/png")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:unsupported-image"));
        upload(admin, "logo", "not an image".getBytes(StandardCharsets.UTF_8), "logo.jpg", "image/jpeg")
                .andExpect(status().isUnsupportedMediaType());

        byte[] tooLarge = new byte[1024 * 1024 + 10];
        System.arraycopy(ImageFixtures.png(4, 4), 0, tooLarge, 0, 8);
        upload(admin, "logo", tooLarge)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:image-too-large"));

        upload(admin, "banner", ImageFixtures.png(4, 4)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tenants/current")).andExpect(jsonPath("$.branding.logoUrl").value(nullValue()));
    }

    @Test
    void anIconAndADarkModeLogoAreUploadedServedAndRemoved() throws Exception {
        String admin = tokenFor(Role.ADMIN);

        upload(admin, "icon", ImageFixtures.png(300, 150))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iconUrl").value(startsWith("/api/v1/tenants/current/icon?v=")))
                .andExpect(jsonPath("$.logoDarkUrl").value(nullValue()));
        upload(admin, "logo-dark", ImageFixtures.png(800, 200))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoDarkUrl").value(startsWith("/api/v1/tenants/current/logo-dark?v=")));

        mvc.perform(get("/api/v1/tenants/current"))
                .andExpect(jsonPath("$.branding.iconUrl").value(startsWith("/api/v1/tenants/current/icon?v=")))
                .andExpect(jsonPath("$.branding.logoDarkUrl").value(startsWith("/api/v1/tenants/current/logo-dark")))
                .andExpect(jsonPath("$.branding.logoUrl").value(nullValue()));

        // Public, like the logo; the icon is square.
        byte[] icon = mvc.perform(get("/api/v1/tenants/current/icon"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage square = ImageIO.read(new ByteArrayInputStream(icon));
        assertThat(square.getWidth()).isEqualTo(300);
        assertThat(square.getHeight()).isEqualTo(300);
        mvc.perform(get("/api/v1/tenants/current/logo-dark")).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/auth/tenant/branding/icon").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iconUrl").value(nullValue()))
                .andExpect(jsonPath("$.logoDarkUrl").isNotEmpty());
        mvc.perform(get("/api/v1/tenants/current/icon")).andExpect(status().isNotFound());
    }

    @Test
    void anAdminRemovesAnImage() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        upload(admin, "logo", ImageFixtures.png(64, 64)).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/auth/tenant/branding/logo").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value(nullValue()));
        mvc.perform(get("/api/v1/tenants/current/logo")).andExpect(status().isNotFound());
        // Removing again is harmless.
        mvc.perform(delete("/api/v1/auth/tenant/branding/logo").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void workshopsNeverSeeEachOthersBranding() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        upload(admin, "logo", ImageFixtures.png(64, 64)).andExpect(status().isOk());
        update(admin, body("#be123c", false, 0)).andExpect(status().isOk());

        mvc.perform(get("/api/v1/tenants/current").header("X-Forwarded-Host", OTHER_HOST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("branding-other"))
                .andExpect(jsonPath("$.branding.logoUrl").value(nullValue()))
                .andExpect(jsonPath("$.branding.primaryColor").value(nullValue()))
                .andExpect(jsonPath("$.branding.announcements").isEmpty())
                .andExpect(jsonPath("$.branding.showPoweredBy").value(true));
        mvc.perform(get("/api/v1/tenants/current/logo").header("X-Forwarded-Host", OTHER_HOST))
                .andExpect(status().isNotFound());

        // The demo admin's token is not accepted at the other workshop's address.
        mvc.perform(get("/api/v1/auth/tenant/branding").header("X-Forwarded-Host", OTHER_HOST)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("select count(*) from bayflow.auth_tenant_branding where tenant_id = ?",
                Long.class, Tenants.DEFAULT_TENANT_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from bayflow.auth_tenant_branding where tenant_id = ?",
                Long.class, OTHER)).isZero();
    }

    // ---- helpers ------------------------------------------------------------------

    private String tokenFor(Role role) throws Exception {
        String username = "brand-" + role.name().toLowerCase().replace('_', '-') + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        newUser(Tenants.DEFAULT_TENANT_ID, username, username + "@example.com", PASSWORD, Set.of(role));
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions update(String token, String json) throws Exception {
        var request = put("/api/v1/auth/tenant/branding").contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request);
    }

    private ResultActions upload(String token, String kind, byte[] content) throws Exception {
        return upload(token, kind, content, "image.png", "image/png");
    }

    private ResultActions upload(String token, String kind, byte[] content, String filename, String type)
            throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, "/api/v1/auth/tenant/branding/" + kind)
                .file(new MockMultipartFile("file", filename, type, content))
                .header("Authorization", "Bearer " + token));
    }

    private static String body(String color, boolean poweredBy, long version) {
        return """
                {"primaryColor":%s,"showPoweredBy":%s,"version":%d}
                """.formatted(json(color), poweredBy, version);
    }

    private static String json(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
