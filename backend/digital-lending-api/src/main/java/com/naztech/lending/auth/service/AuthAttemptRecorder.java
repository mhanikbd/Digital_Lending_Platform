package com.naztech.lending.auth.service;

import com.naztech.lending.auth.AuthProperties;
import com.naztech.lending.auth.domain.LoginHistory;
import com.naztech.lending.auth.domain.LoginOutcome;
import com.naztech.lending.auth.domain.UserAccount;
import com.naztech.lending.auth.domain.UserType;
import com.naztech.lending.auth.repository.LoginHistoryRepository;
import com.naztech.lending.auth.repository.UserAccountRepository;
import com.naztech.lending.common.correlation.CorrelationId;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the two things that must survive a rejected sign-in.
 *
 * <p>This exists as its own bean for one reason. A failed authentication ends by
 * throwing, which rolls the caller transaction back - and if the attempt counter
 * and the audit row were written in that transaction, they would be rolled back
 * with it. Lock-out would never trigger and the audit trail would record only
 * the attempts that succeeded, which is precisely the opposite of what it is
 * for.
 *
 * <p>Every method therefore runs in its own transaction. A separate bean is
 * required because REQUIRES_NEW is applied by the proxy, and a call from one
 * method of a class to another of the same class never passes through it.
 */
@Service
public class AuthAttemptRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuthAttemptRecorder.class);

    private final UserAccountRepository users;
    private final LoginHistoryRepository loginHistory;
    private final AuthProperties properties;

    public AuthAttemptRecorder(UserAccountRepository users,
                               LoginHistoryRepository loginHistory,
                               AuthProperties properties) {
        this.users = users;
        this.loginHistory = loginHistory;
        this.properties = properties;
    }

    /** Appends one attempt to the audit trail, whatever the caller goes on to do. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String usernameAttempted, UserType userType,
                       LoginOutcome outcome, String reason, Caller caller) {
        UserAccount user = userId == null ? null : users.findById(userId).orElse(null);
        loginHistory.save(new LoginHistory(user, usernameAttempted, userType, outcome)
                .withReason(reason)
                .withCaller(caller.ipAddress(), caller.userAgent(), caller.deviceId())
                .withCorrelationId(CorrelationId.current()));
    }

    /**
     * Counts a failure against the identity and locks it at the threshold.
     *
     * @return true when this attempt is what caused the lock
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerFailure(UUID userId, Instant now) {
        UserAccount user = users.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        // A lock whose deadline has passed is spent: clear it first, so this
        // attempt is counted as the first of a fresh run rather than the last of
        // an old one.
        user.releaseExpiredLock(now);
        boolean locked = user.registerFailedAttempt(
                properties.lockout().maxFailedAttempts(),
                properties.lockout().lockDuration(),
                now);
        users.save(user);
        if (locked) {
            log.warn("Account {} locked after {} consecutive failed attempts",
                    userId, user.getFailedAttempts());
        }
        return locked;
    }

}
