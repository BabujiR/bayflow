package com.bayflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

class RolesClaimAuthenticationConverterTest {

    private final RolesClaimAuthenticationConverter converter = new RolesClaimAuthenticationConverter();

    @Test
    void mapsRolesClaimToRoleAuthoritiesAndUsesSubjectAsName() {
        AbstractAuthenticationToken auth = converter.convert(
                TestJwts.userJwt("user-1", "advisor1", List.of("SERVICE_ADVISOR", "MECHANIC")));

        assertThat(auth.getName()).isEqualTo("user-1");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_SERVICE_ADVISOR", "ROLE_MECHANIC");
    }

    @Test
    void tokenWithoutRolesHasNoAuthorities() {
        AbstractAuthenticationToken auth = converter.convert(TestJwts.jwt(Map.of("sub", "x")));

        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void ignoresBlankRolesAndKeepsExistingPrefix() {
        AbstractAuthenticationToken auth = converter.convert(
                TestJwts.jwt(Map.of("sub", "x", "roles", List.of(" ", "ROLE_SYSTEM"))));

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_SYSTEM");
    }
}
