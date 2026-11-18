package com.bayflow.security;

import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Maps the {@code roles} claim of a Bayflow access token to {@code ROLE_*} authorities.
 * The authentication name is the token subject (the user id, or the client id for SYSTEM tokens).
 * <p>
 * A user token must name its workshop in the {@value Tenants#CLAIM} claim; one without it is
 * rejected (401). Only SYSTEM tokens may be tenant-less. Staff who work at particular locations
 * (MANAGER, SERVICE_ADVISOR, MECHANIC) must also carry their locations in
 * {@value Tenants#LOCATIONS_CLAIM}; ADMIN and CUSTOMER tokens cover every location.
 * <p>
 * A MANAGER also gets the SERVICE_ADVISOR authority: a manager may do everything an advisor may.
 */
public class RolesClaimAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ROLES_CLAIM = "roles";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = authorities(jwt);
        boolean system = authorities.stream()
                .anyMatch(a -> Roles.authority(Roles.SYSTEM).equals(a.getAuthority()));
        if (!system && Tenants.parse(jwt.getClaim(Tenants.CLAIM)).isEmpty()) {
            throw new InvalidBearerTokenException("The access token does not name a workshop. Sign in again.");
        }
        boolean admin = authorities.stream().anyMatch(a -> Roles.authority(Roles.ADMIN).equals(a.getAuthority()));
        boolean scoped = authorities.stream()
                .anyMatch(a -> Roles.LOCATION_SCOPED.stream().anyMatch(r -> Roles.authority(r).equals(a.getAuthority())));
        if (!system && !admin && scoped && LocationScope.fromClaim(jwt.getClaim(Tenants.LOCATIONS_CLAIM)).isEmpty()) {
            throw new InvalidBearerTokenException("The access token does not name your locations. Sign in again.");
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    static Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles == null) {
            return List.of();
        }
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                names.add(role.trim());
                if (Roles.authority(Roles.MANAGER).equals(Roles.authority(role.trim()))) {
                    names.add(Roles.SERVICE_ADVISOR);
                }
            }
        }
        return names.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(Roles.authority(role)))
                .toList();
    }
}
