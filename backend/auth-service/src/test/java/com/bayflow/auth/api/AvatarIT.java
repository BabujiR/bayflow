package com.bayflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.User;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/** Avatars: initials by default, a built-in character, or a photo (checked, cut square, 256 px). */
class AvatarIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";

    @Test
    void everyoneStartsWithInitialsAndChoosesACharacter() throws Exception {
        String token = tokenFor(Role.MECHANIC).token();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.avatar.kind").value("INITIALS"))
                .andExpect(jsonPath("$.avatar.photoUrl").doesNotExist());

        choose(token, "{\"kind\":\"CHARACTER\",\"character\":\"wrench\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar.kind").value("CHARACTER"))
                .andExpect(jsonPath("$.avatar.character").value("wrench"));
        choose(token, "{\"kind\":\"CHARACTER\",\"character\":\"dragon\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-character"));
        choose(token, "{\"kind\":\"INITIALS\"}").andExpect(jsonPath("$.avatar.kind").value("INITIALS"));
        mvc.perform(put("/api/v1/auth/me/avatar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"INITIALS\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aPhotoIsCutSquareAndServedAtItsVersionedAddress() throws Exception {
        Login mechanic = tokenFor(Role.MECHANIC);
        String body = photo(mechanic.token(), ImageFixtures.jpegWithExif(800, 600), "me.jpg", "image/jpeg")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar.kind").value("PHOTO"))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.avatar.photoUrl");
        assertThat(url).startsWith("/api/v1/tenants/current/avatars/" + mechanic.userId() + "?v=");

        byte[] served = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(served));
        assertThat(image.getWidth()).isEqualTo(256);
        assertThat(image.getHeight()).isEqualTo(256);
        // No EXIF (location) survives the re-encoding.
        assertThat(new String(served, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
        // An old or guessed address shows nothing.
        mvc.perform(get("/api/v1/tenants/current/avatars/" + mechanic.userId() + "?v=0000"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tenants/current/avatars/" + mechanic.userId())).andExpect(status().isNotFound());

        // Back to initials: the photo is gone.
        choose(mechanic.token(), "{\"kind\":\"INITIALS\"}").andExpect(status().isOk());
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void onlyImagesAreAccepted() throws Exception {
        String token = tokenFor(Role.SERVICE_ADVISOR).token();
        photo(token, "<svg onload=alert(1)>".getBytes(), "me.svg", "image/svg+xml")
                .andExpect(status().isUnsupportedMediaType());
        photo(token, new byte[3 * 1024 * 1024], "big.png", "image/png").andExpect(status().isPayloadTooLarge());
        photo(token, ImageFixtures.png(300, 300), "me.png", "image/png").andExpect(status().isOk());
    }

    @Test
    void anAdminCanOnlyPutSomeoneBackToInitials() throws Exception {
        Login mechanic = tokenFor(Role.MECHANIC);
        photo(mechanic.token(), ImageFixtures.png(300, 300), "me.png", "image/png").andExpect(status().isOk());
        String admin = tokenFor(Role.ADMIN).token();

        mvc.perform(delete("/api/v1/auth/users/" + mechanic.userId() + "/avatar")
                        .header("Authorization", "Bearer " + tokenFor(Role.SERVICE_ADVISOR).token()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/auth/users/" + mechanic.userId() + "/avatar")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar.kind").value("INITIALS"));
        // The users list shows each one's avatar.
        mvc.perform(get("/api/v1/auth/users").param("size", "100").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.content[0].avatar.kind").exists());
    }

    // ---- helpers ------------------------------------------------------------------

    private record Login(UUID userId, String token) {
    }

    private Login tokenFor(Role role) throws Exception {
        String username = "av-" + role.name().toLowerCase().replace('_', '-') + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        User user = newUser(Tenants.DEFAULT_TENANT_ID, username, username + "@example.com", PASSWORD, Set.of(role));
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Login(user.getId(), JsonPath.read(body, "$.accessToken"));
    }

    private ResultActions choose(String token, String json) throws Exception {
        return mvc.perform(put("/api/v1/auth/me/avatar").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions photo(String token, byte[] content, String name, String type) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, "/api/v1/auth/me/avatar/photo")
                .file(new MockMultipartFile("file", name, type, content))
                .header("Authorization", "Bearer " + token));
    }
}
