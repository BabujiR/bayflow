package com.bayflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Organizations and locations, phase L2: who works with which locations. */
class LocationScopeTest {

    private static final UUID A = UUID.fromString("0a0a0a0a-0000-4000-8000-00000000000a");
    private static final UUID B = UUID.fromString("0b0b0b0b-0000-4000-8000-00000000000b");

    private final RolesClaimAuthenticationConverter converter = new RolesClaimAuthenticationConverter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private static Map<String, Object> claims(List<String> roles, Object lsc) {
        var map = new java.util.HashMap<String, Object>(Map.of("sub", "u1", "roles", roles, "tid", TestJwts.TENANT));
        if (lsc != null) {
            map.put("lsc", lsc);
        }
        return map;
    }

    private JwtAuthenticationToken token(List<String> roles, Object lsc) {
        return (JwtAuthenticationToken) converter.convert(TestJwts.jwt(claims(roles, lsc)));
    }

    @Test
    void readsTheClaim() {
        assertThat(LocationScope.fromClaim(List.of("*"))).contains(LocationScope.ALL);
        assertThat(LocationScope.fromClaim(List.of(A.toString(), B.toString())).orElseThrow().ids())
                .containsExactly(A, B);
        assertThat(LocationScope.fromClaim(List.of())).isEmpty();
        assertThat(LocationScope.fromClaim(List.of("not-a-uuid"))).isEmpty();
        assertThat(LocationScope.fromClaim("*")).isEmpty();
        assertThat(LocationScope.fromClaim(null)).isEmpty();
    }

    @Test
    void scopesAllowNarrowAndBecomeTheDatabaseSetting() {
        LocationScope ab = LocationScope.of(List.of(A, B));
        assertThat(ab.allows(A)).isTrue();
        assertThat(ab.allows(UUID.randomUUID())).isFalse();
        assertThat(ab.narrowTo(B)).contains(LocationScope.of(B));
        assertThat(ab.narrowTo(UUID.randomUUID())).isEmpty();
        assertThat(ab.single()).isEmpty();
        assertThat(LocationScope.of(A).single()).contains(A);
        assertThat(LocationScope.ALL.asSetting()).isEqualTo("*");
        assertThat(ab.asSetting()).isEqualTo(A + "," + B);
        assertThat(LocationScope.NONE.asSetting()).isEmpty();
        assertThat(LocationScope.ALL.allows(A)).isTrue();
    }

    @Test
    void staffTokensMustNameTheirLocations() {
        for (String role : List.of("SERVICE_ADVISOR", "MECHANIC", "MANAGER")) {
            assertThatThrownBy(() -> token(List.of(role), null)).as(role)
                    .isInstanceOf(InvalidBearerTokenException.class);
            assertThatThrownBy(() -> token(List.of(role), List.of())).as(role)
                    .isInstanceOf(InvalidBearerTokenException.class);
        }
        // Customers and admins cover every location, with or without the claim.
        assertThat(LocationContext.ofToken(token(List.of("CUSTOMER"), null))).isEqualTo(LocationScope.ALL);
        assertThat(LocationContext.ofToken(token(List.of("ADMIN"), null))).isEqualTo(LocationScope.ALL);
        assertThat(LocationContext.ofToken(token(List.of("ADMIN", "MECHANIC"), List.of(A.toString()))))
                .isEqualTo(LocationScope.ALL);
        assertThat(LocationContext.ofToken(token(List.of("MECHANIC"), List.of(A.toString()))))
                .isEqualTo(LocationScope.of(A));
    }

    @Test
    void aManagerIsAlsoAnAdvisor() {
        assertThat(token(List.of("MANAGER"), List.of(A.toString())).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_MANAGER", "ROLE_SERVICE_ADVISOR");
    }

    @Test
    void theHeaderNarrowsButNeverWidens() throws Exception {
        LocationScopeFilter filter = new LocationScopeFilter(new ProblemDetailsSecurityHandlers());
        SecurityContextHolder.getContext().setAuthentication(
                token(List.of("SERVICE_ADVISOR"), List.of(A.toString(), B.toString())));

        assertThat(scopeFor(filter, null)).isEqualTo(LocationScope.of(List.of(A, B)));
        assertThat(scopeFor(filter, "all")).isEqualTo(LocationScope.of(List.of(A, B)));
        assertThat(scopeFor(filter, B.toString())).isEqualTo(LocationScope.of(B));

        for (String outside : new String[] { UUID.randomUUID().toString(), "nonsense" }) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(LocationScopeFilter.HEADER, outside);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("location-not-allowed");
            assertThat(chain.getRequest()).isNull();
        }
    }

    @Test
    void backgroundWorkSeesEveryLocationOfItsOrganizationOnly() {
        assertThat(LocationContext.current()).isEqualTo(LocationScope.NONE);
        assertThat(TenantContext.call(UUID.randomUUID(), LocationContext::current)).isEqualTo(LocationScope.ALL);
        assertThat(TenantContext.callAcrossTenants(LocationContext::current)).isEqualTo(LocationScope.NONE);
        assertThat(LocationContext.call(LocationScope.of(A), LocationContext::current)).isEqualTo(LocationScope.of(A));
    }

    private static LocationScope scopeFor(LocationScopeFilter filter, String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader(LocationScopeFilter.HEADER, header);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return LocationContext.current();
    }
}
