package com.naztech.lending.auth.repository;

import com.naztech.lending.auth.domain.UserAccount;
import com.naztech.lending.auth.domain.UserSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    /** Looked up by hash: the raw refresh token is never stored or queried. */
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    List<UserSession> findByUserAndRevokedAtIsNullAndExpiresAtAfter(UserAccount user, Instant now);
}
