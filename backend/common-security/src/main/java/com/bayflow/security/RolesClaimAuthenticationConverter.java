package com.bayflow.security;

import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Maps the {@code roles} claim of a Bayflow access token to {@code ROLE_*} authorities.
 * The authentication name is the token subject (the user id, or the client id for SYSTEM tokens).
 */
public class RolesClaimAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ROLES_CLAIM = "roles";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
    }

    static Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(Roles.authority(role.trim())))
                .toList();
    }
}
