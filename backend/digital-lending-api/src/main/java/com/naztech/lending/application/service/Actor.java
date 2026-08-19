package com.naztech.lending.application.service;

import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Who is acting on an application.
 *
 * <p>Assembled from the access token rather than looked up, because the token
 * already carries the roles and looking them up again would let a revoked role
 * keep working until a cache expired somewhere else.
 *
 * <p>The username is kept alongside the id because the audit trail is read by
 * people. A history row saying {@code fc492d2c-fc73-440f} is a history row
 * somebody has to go and resolve before they can understand it.
 */
public record Actor(UUID userId, String username, List<String> roles) {

    public static Actor of(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        String username = jwt.getClaimAsString("username");
        return new Actor(
                UUID.fromString(jwt.getSubject()),
                username != null ? username : jwt.getSubject(),
                roles != null ? List.copyOf(roles) : List.of());
    }

    /**
     * The role to record against an action.
     *
     * <p>A person may hold several. The one recorded is the one the workflow
     * actually accepted the action under, which the caller works out and passes
     * in; this is the fallback for actions no single role owns.
     */
    public String primaryRole() {
        return roles.isEmpty() ? null : roles.get(0);
    }
}
