package com.naztech.lending.security;

import java.util.List;

/**
 * Paths a module needs reachable without a credential.
 *
 * <p>The baseline list in {@link SecurityConfig} is the set that must be open in
 * every environment - sign-in, token refresh, health. This interface is for the
 * ones that must not be: an endpoint that exists only under a profile should not
 * be named in the security configuration that ships to production, because a
 * permitted path for a handler that does not exist is a line nobody can explain
 * two years later.
 *
 * <p>Implementations are expected to carry the profile guard themselves. A bean
 * that is not created contributes nothing.
 */
public interface PublicEndpoints {

    /** Ant-style paths to permit without authentication. */
    List<String> paths();

    /** Why they are open, for the startup log and for whoever asks later. */
    String reason();
}
