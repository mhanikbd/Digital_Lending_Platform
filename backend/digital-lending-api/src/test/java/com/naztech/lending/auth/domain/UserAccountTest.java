package com.naztech.lending.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The lock-out rule, tested where it lives.
 *
 * <p>This is the one piece of authentication that is pure logic with no
 * collaborators, so it is worth pinning precisely: an off-by-one here is either
 * an account that never locks or one that locks a try early.
 */
class UserAccountTest {

    private static final int THRESHOLD = 3;
    private static final Duration LOCK_FOR = Duration.ofMinutes(15);
    private static final Instant NOW = Instant.parse("2026-08-19T10:00:00Z");

    @Test
    void locksOnlyOnTheAttemptThatReachesTheThreshold() {
        UserAccount user = user();

        assertThat(user.registerFailedAttempt(THRESHOLD, LOCK_FOR, NOW)).isFalse();
        assertThat(user.registerFailedAttempt(THRESHOLD, LOCK_FOR, NOW)).isFalse();
        assertThat(user.registerFailedAttempt(THRESHOLD, LOCK_FOR, NOW)).isTrue();

        assertThat(user.getFailedAttempts()).isEqualTo((short) THRESHOLD);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.isLockedAt(NOW)).isTrue();
        assertThat(user.canAuthenticateAt(NOW)).isFalse();
    }

    @Test
    void theLockExpiresOnItsOwn() {
        UserAccount user = user();
        lockOut(user);

        Instant afterLock = NOW.plus(LOCK_FOR).plusSeconds(1);

        assertThat(user.isLockedAt(afterLock)).isFalse();
        assertThat(user.canAuthenticateAt(afterLock)).isTrue();
    }

    @Test
    void releasingAServedLockClearsTheCounterSoTheNextAttemptStartsFresh() {
        UserAccount user = user();
        lockOut(user);

        user.releaseExpiredLock(NOW.plus(LOCK_FOR).plusSeconds(1));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void aLockStillInForceIsNotReleased() {
        UserAccount user = user();
        lockOut(user);

        user.releaseExpiredLock(NOW.plusSeconds(60));

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getFailedAttempts()).isEqualTo((short) THRESHOLD);
    }

    @Test
    void aSuccessfulLoginClearsFailureState() {
        UserAccount user = user();
        user.registerFailedAttempt(THRESHOLD, LOCK_FOR, NOW);
        user.registerFailedAttempt(THRESHOLD, LOCK_FOR, NOW);

        user.registerSuccessfulLogin(NOW);

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void aSuspendedAccountCannotAuthenticateEvenWithNoFailures() {
        UserAccount user = user();
        user.setStatus(UserStatus.SUSPENDED);

        assertThat(user.isLockedAt(NOW)).isFalse();
        assertThat(user.canAuthenticateAt(NOW)).isFalse();
    }

    @Test
    void releasingDoesNotResurrectAnAccountThatWasSuspendedRatherThanLocked() {
        UserAccount user = user();
        user.setStatus(UserStatus.SUSPENDED);

        user.releaseExpiredLock(NOW.plus(LOCK_FOR).plusSeconds(1));

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    private static UserAccount user() {
        return new UserAccount(UserType.BANK_USER, "EMP-10001", "Test Officer");
    }

    private static void lockOut(UserAccount user) {
        for (int attempt = 0; attempt < THRESHOLD; attempt++) {
            user.registerFailedAttempt(THRESHOLD, LOCK_FOR, NOW);
        }
    }
}
