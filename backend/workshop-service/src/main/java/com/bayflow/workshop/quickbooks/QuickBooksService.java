package com.bayflow.workshop.quickbooks;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;
import com.bayflow.workshop.application.ApiException;
import com.bayflow.workshop.config.WorkshopProperties;
import com.bayflow.workshop.quickbooks.QuickBooksApi.QuickBooksException;
import com.bayflow.workshop.quickbooks.QuickBooksApi.Tokens;

import tools.jackson.databind.JsonNode;

/**
 * The organization's QuickBooks Online connection: connecting (OAuth 2.0 with one redirect URI
 * on the main domain; the state names the organization), fresh access tokens, the products and
 * services Ramplight books to, and disconnecting.
 */
@Service
public class QuickBooksService {

    private static final Logger log = LoggerFactory.getLogger(QuickBooksService.class);

    /** How long the admin has to finish signing in at Intuit. */
    static final Duration STATE_TTL = Duration.ofMinutes(10);
    /** Access tokens are refreshed this long before they expire. */
    static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);
    private static final Set<String> ITEM_TYPES = Set.of("Service", "NonInventory", "Inventory");
    private static final Set<String> DEPOSIT_TYPES = Set.of("Bank", "Other Current Asset");

    /** Which QuickBooks product/service each kind of line goes to, and where payments are deposited. */
    public record Mapping(String partsItemId, String partsItemName, String labourItemId, String labourItemName,
            String feesItemId, String feesItemName, String depositAccountId, String depositAccountName) {

        static Mapping of(QboConnection c) {
            return new Mapping(c.getPartsItemId(), c.getPartsItemName(), c.getLabourItemId(), c.getLabourItemName(),
                    c.getFeesItemId(), c.getFeesItemName(), c.getDepositAccountId(), c.getDepositAccountName());
        }
    }

    /**
     * available: the server has Intuit keys. ready: connected and parts, labour and fees each have
     * a product/service, so invoices can be sent.
     */
    public record Status(boolean available, boolean connected, boolean ready, String environment, String companyName,
            String country, Instant connectedAt, String connectedBy, Mapping mapping) {
    }

    /** A product/service or an account in QuickBooks. */
    public record Choice(String id, String name, String type) {
    }

    /** What a call to QuickBooks needs: the company and a valid access token. */
    public record Access(String realmId, String token) {
    }

    private final QboConnectionRepository connections;
    private final QboOAuthStateRepository states;
    private final QuickBooksApi api;
    private final TokenCipher cipher;
    private final WorkshopProperties properties;
    private final EntityManager entityManager;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public QuickBooksService(QboConnectionRepository connections, QboOAuthStateRepository states, QuickBooksApi api,
            TokenCipher cipher, WorkshopProperties properties, EntityManager entityManager, Clock clock) {
        this.connections = connections;
        this.states = states;
        this.api = api;
        this.cipher = cipher;
        this.properties = properties;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public boolean isAvailable() {
        return properties.getQuickbooks().isConfigured() && cipher.isAvailable();
    }

    @Transactional(readOnly = true)
    public Status status() {
        Optional<QboConnection> found = isAvailable() ? connections.findCurrent() : Optional.empty();
        if (found.isEmpty()) {
            return new Status(isAvailable(), false, false, null, null, null, null, null, null);
        }
        QboConnection c = found.get();
        return new Status(true, true, c.isMapped(), c.getEnvironment(), c.getCompanyName(), c.getCountry(),
                c.getConnectedAt(), c.getConnectedBy(), Mapping.of(c));
    }

    /** The connection, or 409 when there is none (inside the caller's transaction). */
    public QboConnection current() {
        requireAvailable();
        return connections.findCurrent().orElseThrow(() -> ApiException.conflict("quickbooks-not-connected",
                "QuickBooks is not connected. An admin can connect it under Settings."));
    }

    // ---- connecting --------------------------------------------------------------------------------

    /**
     * Starts "Connect to QuickBooks": a one-time state (the organization's id, a dot and 32
     * random bytes; only its hash is stored) and Intuit's sign-in address. returnUrl: where the
     * admin comes back to, on this site or one of its workshop subdomains.
     */
    @Transactional
    public String startConnect(CurrentUser user, String returnUrl) {
        requireAvailable();
        String back = checkedReturnUrl(returnUrl);
        Instant now = clock.instant();
        states.deleteExpired(now);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = TenantContext.require() + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.save(QboOAuthState.create(sha256(state), back, user.username(), now.plus(STATE_TTL), now));
        return api.authorizeUrl(state);
    }

    /** The organization named in a state, or empty when it isn't one of ours. */
    public static Optional<UUID> tenantOf(String state) {
        if (state == null) {
            return Optional.empty();
        }
        int dot = state.indexOf('.');
        try {
            return dot <= 0 ? Optional.empty() : Optional.of(UUID.fromString(state.substring(0, dot)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Intuit sent the admin back (run with the state's organization as the tenant). Returns where
     * to send the browser: the page they started from, with ?quickbooks=connected, cancelled or
     * failed; empty when the state is unknown or expired.
     */
    @Transactional
    public Optional<String> completeConnect(String state, String code, String realmId, String error) {
        Optional<QboOAuthState> found = states.findByStateHash(sha256(state));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        QboOAuthState pending = found.get();
        states.delete(pending);
        Instant now = clock.instant();
        if (pending.isExpired(now)) {
            return Optional.of(withResult(pending.getReturnUrl(), "expired"));
        }
        if (error != null || code == null || realmId == null || realmId.isBlank()) {
            return Optional.of(withResult(pending.getReturnUrl(), error != null ? "cancelled" : "failed"));
        }
        try {
            Tokens tokens = api.exchangeCode(code, now);
            String environment = properties.getQuickbooks().isProduction() ? "production" : "sandbox";
            QboConnection connection = connections.findCurrent().orElse(null);
            if (connection == null) {
                connection = QboConnection.create(realmId, environment, pending.getCreatedBy(), now);
            } else {
                connection.reconnect(realmId, environment, pending.getCreatedBy(), now);
            }
            store(connection, tokens, now);
            try {
                JsonNode info = api.get(realmId, tokens.accessToken(), "companyinfo/" + realmId)
                        .path("CompanyInfo");
                connection.company(text(info.path("CompanyName")), text(info.path("Country")));
            } catch (QuickBooksException e) {
                log.warn("Connected to QuickBooks company {} but could not read its name: {}", realmId, e.getMessage());
            }
            connections.save(connection);
            log.info("QuickBooks company {} connected by {}", realmId, pending.getCreatedBy());
            return Optional.of(withResult(pending.getReturnUrl(), "connected"));
        } catch (QuickBooksException e) {
            log.warn("Connecting QuickBooks failed: {}", e.getMessage());
            return Optional.of(withResult(pending.getReturnUrl(), "failed"));
        }
    }

    /** Forgets the connection here and, as far as Intuit answers, there too. */
    @Transactional
    public void disconnect() {
        connections.findCurrent().ifPresent(c -> {
            try {
                api.revoke(cipher.decrypt(c.getRefreshToken(), c.getTenantId()));
            } catch (RuntimeException e) {
                log.warn("Revoking the QuickBooks tokens failed (removed here anyway): {}", e.getMessage());
            }
            connections.delete(c);
        });
    }

    // ---- products, services and accounts ---------------------------------------------------------

    @Transactional
    public List<Choice> items() {
        return call(access -> choices(api.query(access.realmId(), access.token(),
                "select * from Item where Active = true maxresults 1000").path("Item"), ITEM_TYPES));
    }

    @Transactional
    public List<Choice> accounts() {
        return call(access -> choices(api.query(access.realmId(), access.token(),
                "select * from Account where Active = true maxresults 1000").path("Account"), DEPOSIT_TYPES));
    }

    @Transactional
    public Status saveMapping(Mapping mapping) {
        QboConnection c = current();
        c.map(blankToNull(mapping.partsItemId()), blankToNull(mapping.partsItemName()),
                blankToNull(mapping.labourItemId()), blankToNull(mapping.labourItemName()),
                blankToNull(mapping.feesItemId()), blankToNull(mapping.feesItemName()),
                blankToNull(mapping.depositAccountId()), blankToNull(mapping.depositAccountName()));
        c.touch(clock.instant());
        connections.saveAndFlush(c);
        return status();
    }

    // ---- calling QuickBooks ------------------------------------------------------------------------

    /**
     * Runs {@code work} with a fresh access token, refreshing it first when it is about to expire
     * and once more if QuickBooks still says 401. Must run inside the caller's transaction (it
     * saves refreshed tokens); deliberately not @Transactional itself, so a QuickBooks error the
     * caller handles doesn't mark that transaction for rollback.
     */
    public <T> T call(Function<Access, T> work) {
        QboConnection c = current();
        String token = accessToken(c, false);
        try {
            return work.apply(new Access(c.getRealmId(), token));
        } catch (QuickBooksException e) {
            if (!e.isUnauthorized()) {
                throw e;
            }
            return work.apply(new Access(c.getRealmId(), accessToken(c, true)));
        }
    }

    private String accessToken(QboConnection c, boolean force) {
        Instant now = clock.instant();
        if (!force && now.isBefore(c.getAccessExpiresAt().minus(REFRESH_MARGIN))) {
            return cipher.decrypt(c.getAccessToken(), c.getTenantId());
        }
        // Lock and re-read: another request may have refreshed it a moment ago.
        String before = c.getAccessToken();
        entityManager.refresh(c, LockModeType.PESSIMISTIC_WRITE);
        if (!c.getAccessToken().equals(before) && now.isBefore(c.getAccessExpiresAt().minus(REFRESH_MARGIN))) {
            return cipher.decrypt(c.getAccessToken(), c.getTenantId());
        }
        Tokens tokens;
        try {
            tokens = api.refresh(cipher.decrypt(c.getRefreshToken(), c.getTenantId()), now);
        } catch (QuickBooksException e) {
            if (e.getStatus() == 400 || e.getStatus() == 401) {
                throw ApiException.conflict("quickbooks-reconnect",
                        "QuickBooks needs to be connected again: an admin can reconnect it under Settings.");
            }
            throw e;
        }
        store(c, tokens, now);
        connections.saveAndFlush(c);
        return tokens.accessToken();
    }

    private void store(QboConnection c, Tokens tokens, Instant now) {
        UUID tenant = TenantContext.require();
        c.tokens(cipher.encrypt(tokens.accessToken(), tenant), tokens.accessExpiresAt(),
                cipher.encrypt(tokens.refreshToken(), tenant), tokens.refreshExpiresAt(), now);
    }

    /** A QuickBooks error as an API error (502: the problem is on QuickBooks' side or in its data). */
    public static ApiException problem(QuickBooksException e) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "quickbooks-error", "QuickBooks error", e.getMessage());
    }

    // ---- helpers -------------------------------------------------------------------------------

    private void requireAvailable() {
        if (!isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "quickbooks-unavailable", "Service Unavailable",
                    "QuickBooks is not set up on this server.");
        }
    }

    /** Only back to this site or one of its workshop subdomains (no open redirect). */
    private String checkedReturnUrl(String returnUrl) {
        URI site = URI.create(properties.getSiteUrl());
        try {
            URI back = URI.create(returnUrl == null ? "" : returnUrl.strip());
            String host = back.getHost();
            boolean ours = host != null && site.getHost() != null
                    && (host.equalsIgnoreCase(site.getHost()) || host.toLowerCase().endsWith("." + site.getHost().toLowerCase()))
                    && site.getScheme().equalsIgnoreCase(back.getScheme())
                    && back.getPort() == site.getPort()
                    && back.getUserInfo() == null;
            if (ours && returnUrl.length() <= 400) {
                return new URI(back.getScheme(), null, back.getHost(), back.getPort(),
                        back.getPath() == null || back.getPath().isEmpty() ? "/" : back.getPath(), null, null).toString();
            }
        } catch (IllegalArgumentException | java.net.URISyntaxException e) {
            // falls through
        }
        throw ApiException.badRequest("invalid-return-url", "Come back to a page of this site.");
    }

    private static String withResult(String url, String result) {
        return url + (url.contains("?") ? "&" : "?") + "quickbooks=" + result;
    }

    private static List<Choice> choices(JsonNode nodes, Set<String> types) {
        List<Choice> out = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            JsonNode node = nodes.get(i);
            String type = text(node.has("Type") ? node.path("Type") : node.path("AccountType"));
            if (type != null && types.contains(type)) {
                String name = text(node.has("FullyQualifiedName") ? node.path("FullyQualifiedName") : node.path("Name"));
                out.add(new Choice(text(node.path("Id")), name, type));
            }
        }
        out.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(a.name()), String.valueOf(b.name())));
        return out;
    }

    static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
