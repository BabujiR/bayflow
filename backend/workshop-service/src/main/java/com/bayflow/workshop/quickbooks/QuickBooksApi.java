package com.bayflow.workshop.quickbooks;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.bayflow.http.ServiceHttp;
import com.bayflow.workshop.config.WorkshopProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The calls Ramplight makes to Intuit: the OAuth 2.0 token endpoints and the QuickBooks Online
 * Accounting API (v3, JSON). Nothing here touches the database; tests replace this bean.
 */
@Component
public class QuickBooksApi {

    /** Tokens from Intuit; the refresh token changes on refresh and must be kept. */
    public record Tokens(String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt) {
    }

    /** Intuit refused or failed. unauthorized = the access token is no good (refresh and retry once). */
    public static class QuickBooksException extends RuntimeException {
        private final int status;
        private final boolean unauthorized;

        public QuickBooksException(int status, String message) {
            super(message);
            this.status = status;
            this.unauthorized = status == 401;
        }

        public int getStatus() { return status; }
        public boolean isUnauthorized() { return unauthorized; }
    }

    private static final String SCOPE = "com.intuit.quickbooks.accounting";

    private final WorkshopProperties.QuickBooks settings;
    private final RestClient http;
    private final ObjectMapper json;

    public QuickBooksApi(WorkshopProperties properties, ObjectMapper json) {
        this.settings = properties.getQuickbooks();
        // Intuit's API, with full URLs: nothing of ours (request ids, trace context) goes to it.
        this.http = RestClient.builder()
                .requestFactory(ServiceHttp.requestFactory(settings.getConnectTimeout(), settings.getReadTimeout()))
                .build();
        this.json = json;
    }

    /** Where the admin signs in to Intuit and picks the company. */
    public String authorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(settings.getAuthorizeUrl())
                .queryParam("client_id", settings.getClientId())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("redirect_uri", settings.getRedirectUri())
                .queryParam("state", state)
                .encode()
                .build()
                .toUriString();
    }

    public Tokens exchangeCode(String code, Instant now) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", settings.getRedirectUri());
        return tokens(form, now);
    }

    public Tokens refresh(String refreshToken, Instant now) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return tokens(form, now);
    }

    /** Disconnecting: Intuit forgets the grant (revoking the refresh token revokes both). */
    public void revoke(String refreshToken) {
        call(() -> http.post().uri(settings.getRevokeUrl())
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of("token", refreshToken))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> fail(response))
                .toBodilessEntity());
    }

    /** GET /v3/company/{realm}/{path}. */
    public JsonNode get(String realmId, String accessToken, String path) {
        return call(() -> http.get().uri(api(realmId, path, null))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> fail(response))
                .body(JsonNode.class));
    }

    /** A QuickBooks query ("select … from Item where …"); the QueryResponse object. */
    public JsonNode query(String realmId, String accessToken, String query) {
        JsonNode body = call(() -> http.get().uri(api(realmId, "query", Map.of("query", query)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> fail(response))
                .body(JsonNode.class));
        return body == null ? json.createObjectNode() : body.path("QueryResponse");
    }

    /** POST /v3/company/{realm}/{entity}[?operation=…]; the response body. */
    public JsonNode post(String realmId, String accessToken, String entity, String operation, Object body) {
        return call(() -> http.post().uri(api(realmId, entity, operation == null ? null : Map.of("operation", operation)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> fail(response))
                .body(JsonNode.class));
    }

    // ---- helpers -------------------------------------------------------------------------------

    private Tokens tokens(MultiValueMap<String, String> form, Instant now) {
        JsonNode body = call(() -> http.post().uri(settings.getTokenUrl())
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> fail(response))
                .body(JsonNode.class));
        if (body == null || !body.hasNonNull("access_token") || !body.hasNonNull("refresh_token")) {
            throw new QuickBooksException(502, "Intuit returned no tokens.");
        }
        long accessSeconds = body.path("expires_in").asLong(3600);
        long refreshSeconds = body.path("x_refresh_token_expires_in").asLong(0);
        return new Tokens(body.get("access_token").asString(), now.plusSeconds(accessSeconds),
                body.get("refresh_token").asString(), refreshSeconds > 0 ? now.plusSeconds(refreshSeconds) : null);
    }

    private URI api(String realmId, String path, Map<String, String> params) {
        StringBuilder url = new StringBuilder(settings.apiBaseUrl()).append("/v3/company/")
                .append(URLEncoder.encode(realmId, StandardCharsets.UTF_8)).append('/').append(path)
                .append("?minorversion=").append(settings.getMinorVersion());
        if (params != null) {
            params.forEach((name, value) -> url.append('&').append(name).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")));
        }
        return URI.create(url.toString());
    }

    private String basicAuth() {
        String pair = settings.getClientId() + ":" + settings.getClientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    }

    /** The message of Intuit's Fault (or OAuth error) body, as an exception. */
    private void fail(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String message = "QuickBooks answered " + status + ".";
        try {
            JsonNode body = json.readTree(response.getBody());
            JsonNode error = body.path("Fault").path("Error").path(0);
            if (!error.isMissingNode()) {
                String detail = error.path("Detail").asString("");
                message = error.path("Message").asString(message)
                        + (detail.isBlank() ? "" : ": " + detail)
                        + (error.hasNonNull("code") ? " (code " + error.get("code").asString() + ")" : "");
            } else if (body.hasNonNull("error")) {
                message = "Intuit: " + body.get("error").asString()
                        + (body.hasNonNull("error_description") ? " — " + body.get("error_description").asString() : "");
            }
        } catch (IOException | RuntimeException e) {
            // Not JSON: keep the status.
        }
        throw new QuickBooksException(status, message.length() > 480 ? message.substring(0, 480) : message);
    }

    private interface Call<T> {
        T run();
    }

    private static <T> T call(Call<T> call) {
        try {
            return call.run();
        } catch (ResourceAccessException e) {
            throw new QuickBooksException(503, "QuickBooks could not be reached. Try again in a moment.");
        }
    }
}
