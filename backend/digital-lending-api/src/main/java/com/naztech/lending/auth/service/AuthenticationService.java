package com.naztech.lending.auth.service;

import com.naztech.lending.auth.AuthProperties;
import com.naztech.lending.auth.domain.CredentialType;
import com.naztech.lending.auth.domain.LoginOutcome;
import com.naztech.lending.auth.domain.UserAccount;
import com.naztech.lending.auth.domain.UserCredential;
import com.naztech.lending.auth.domain.UserDevice;
import com.naztech.lending.auth.domain.UserSession;
import com.naztech.lending.auth.domain.UserType;
import com.naztech.lending.auth.dto.AuthenticatedUserResponse;
import com.naztech.lending.auth.dto.BankLoginRequest;
import com.naztech.lending.auth.dto.CustomerLoginRequest;
import com.naztech.lending.auth.dto.DeviceDescriptor;
import com.naztech.lending.auth.dto.LoginResponse;
import com.naztech.lending.auth.dto.MfaVerificationRequest;
import com.naztech.lending.auth.dto.TokenPair;
import com.naztech.lending.auth.repository.UserAccountRepository;
import com.naztech.lending.auth.repository.UserCredentialRepository;
import com.naztech.lending.auth.repository.UserDeviceRepository;
import com.naztech.lending.auth.repository.UserSessionRepository;
import com.naztech.lending.common.exception.BusinessException;
import com.naztech.lending.common.exception.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides whether a caller is who they claim to be.
 *
 * <p>Four rules run through everything here.
 *
 * <p><b>One failure message.</b> A caller is told only that the credentials
 * were rejected. Which of the several reasons applied - no such identity, wrong
 * secret, account suspended - goes to the audit trail, never to the caller, so
 * the endpoint cannot be used to discover who banks here.
 *
 * <p><b>Constant work on the unhappy path.</b> When no identity matches, a hash
 * is still verified against a dummy value, so a failed login costs the same time
 * whether the username exists or not.
 *
 * <p><b>Every attempt is recorded.</b> Including the ones that matched nothing,
 * because a run of those against different usernames is the pattern worth
 * catching.
 *
 * <p><b>Bookkeeping outlives the rejection.</b> A failure ends by throwing,
 * which rolls this transaction back. The attempt counter and the audit row are
 * therefore written by {@link AuthAttemptRecorder} in transactions of their own -
 * without that, lock-out would never trigger and the trail would hold only
 * successes.
 */
@Service
public class AuthenticationService {

    /**
     * A real BCrypt hash of a value nobody holds. Verified against when no
     * identity matches, purely so the unhappy path costs the same as the happy
     * one and cannot be timed to enumerate usernames.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserAccountRepository users;
    private final UserCredentialRepository credentials;
    private final UserDeviceRepository devices;
    private final UserSessionRepository sessions;
    private final AuthAttemptRecorder recorder;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final OtpService otpService;
    private final AuthProperties properties;

    public AuthenticationService(UserAccountRepository users,
                                 UserCredentialRepository credentials,
                                 UserDeviceRepository devices,
                                 UserSessionRepository sessions,
                                 AuthAttemptRecorder recorder,
                                 PasswordEncoder passwordEncoder,
                                 TokenService tokenService,
                                 OtpService otpService,
                                 AuthProperties properties) {
        this.users = users;
        this.credentials = credentials;
        this.devices = devices;
        this.sessions = sessions;
        this.recorder = recorder;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.otpService = otpService;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Bank user
    // ------------------------------------------------------------------

    /**
     * Employee id and password. Returns a challenge instead of tokens when the
     * identity has a second factor enabled.
     */
    @Transactional
    public LoginResponse authenticateBankUser(BankLoginRequest request, Caller caller) {
        Instant now = Instant.now();
        UserAccount user = verifySecret(
                UserType.BANK_USER, request.username(), request.password(),
                CredentialType.PASSWORD, caller, now);

        if (user.isMfaEnabled()) {
            OtpService.Challenge challenge =
                    otpService.issue(user.getId().toString(), OtpService.Purpose.BANK_USER_MFA);
            recorder.record(user.getId(), request.username(), UserType.BANK_USER,
                    LoginOutcome.MFA_REQUIRED, null, caller);
            return LoginResponse.mfaRequired(challenge.challengeId(), challenge.expiresInSeconds());
        }

        return completeLogin(user, null, caller, request.username(), now);
    }

    /** Second factor for a staff sign-in that returned MFA_REQUIRED. */
    @Transactional
    public LoginResponse verifyMfa(MfaVerificationRequest request, Caller caller) {
        Instant now = Instant.now();

        // The challenge itself names the identity it was raised for, so a code
        // issued for one person can never complete a sign-in for another - and
        // the wrong-code counter belongs to that one challenge.
        UserAccount user = otpService.subjectOf(request.challengeId())
                .map(AuthenticationService::parseUserId)
                .flatMap(users::findById)
                .orElseThrow(AuthenticationService::rejected);

        OtpService.Verification result = otpService.verify(
                request.challengeId(), request.code(),
                user.getId().toString(), OtpService.Purpose.BANK_USER_MFA);

        if (result != OtpService.Verification.VERIFIED) {
            recorder.record(user.getId(), user.getUsername(), user.getUserType(),
                    LoginOutcome.MFA_FAILED, result.name(), caller);
            throw rejected();
        }
        if (!user.canAuthenticateAt(now)) {
            // The account may have been locked between the two steps.
            recorder.record(user.getId(), user.getUsername(), user.getUserType(),
                    LoginOutcome.ACCOUNT_NOT_ACTIVE, "status changed mid-challenge", caller);
            throw rejected();
        }
        return completeLogin(user, null, caller, user.getUsername(), now);
    }

    // ------------------------------------------------------------------
    // Customer
    // ------------------------------------------------------------------

    /**
     * Mobile number and PIN, accepted only from a device already bound to this
     * customer. The PIN on its own is six digits and would not be defensible.
     */
    @Transactional
    public LoginResponse authenticateCustomer(CustomerLoginRequest request, Caller caller) {
        Instant now = Instant.now();
        UserAccount user = verifySecret(
                UserType.CUSTOMER, request.mobile(), request.pin(),
                CredentialType.PIN, caller, now);

        Optional<UserDevice> bound = devices
                .findByUserAndDeviceId(user, request.device().deviceId())
                .filter(UserDevice::isTrusted);

        if (bound.isEmpty()) {
            recorder.record(user.getId(), request.mobile(), UserType.CUSTOMER,
                    LoginOutcome.DEVICE_NOT_TRUSTED, "device is not bound", caller);
            throw rejected();
        }

        UserDevice device = bound.get();
        device.touch(now);
        return completeLogin(user, device, caller, request.mobile(), now);
    }

    /**
     * Binds a handset once an OTP sent to the customer has been verified from
     * it. This is what turns a PIN into a second factor.
     */
    @Transactional
    public void bindDevice(String mobile, DeviceDescriptor descriptor) {
        Instant now = Instant.now();
        UserAccount user = users.findByTypeAndUsername(UserType.CUSTOMER, mobile)
                .orElseThrow(AuthenticationService::rejected);

        UserDevice device = devices.findByUserAndDeviceId(user, descriptor.deviceId())
                .orElseGet(() -> devices.save(new UserDevice(user, descriptor.deviceId())));
        device.describe(descriptor.platform(), descriptor.model(),
                descriptor.osVersion(), descriptor.appVersion());
        device.markTrusted(now);
        devices.save(device);
    }

    // ------------------------------------------------------------------
    // Session lifecycle
    // ------------------------------------------------------------------

    /**
     * Exchanges a refresh token for a new pair, rotating the refresh token.
     *
     * <p>Rotation matters: a stolen refresh token is only useful until the real
     * client next refreshes, at which point the stolen one no longer resolves.
     */
    @Transactional
    public TokenPair refresh(String refreshToken, Caller caller) {
        Instant now = Instant.now();
        UserSession session = sessions.findByRefreshTokenHash(tokenService.hash(refreshToken))
                .filter(candidate -> candidate.isActiveAt(now))
                .orElseThrow(AuthenticationService::rejected);

        UserAccount user = session.getUser();
        if (!user.canAuthenticateAt(now)) {
            session.revoke("USER_NOT_ACTIVE", now);
            throw rejected();
        }

        session.markUsed(now);
        session.revoke("ROTATED", now);
        return issueTokens(user, session.getDevice(), caller, now);
    }

    /** Ends one session. Idempotent: an unknown token is simply already ended. */
    @Transactional
    public void logout(String refreshToken) {
        sessions.findByRefreshTokenHash(tokenService.hash(refreshToken))
                .ifPresent(session -> session.revoke("LOGOUT", Instant.now()));
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse describe(UUID userId) {
        return users.findById(userId)
                .map(AuthenticatedUserResponse::from)
                .orElseThrow(AuthenticationService::rejected);
    }

    // ------------------------------------------------------------------
    // Shared
    // ------------------------------------------------------------------

    /**
     * Finds the identity and checks its secret, applying lock-out on the way.
     * Throws the one rejection message for every kind of failure.
     */
    private UserAccount verifySecret(UserType type, String username, String presented,
                                     CredentialType credentialType, Caller caller, Instant now) {
        Optional<UserAccount> found = users.findByTypeAndUsername(type, username);
        if (found.isEmpty()) {
            // Still do the work, so this path is not measurably faster.
            passwordEncoder.matches(presented, DUMMY_HASH);
            recorder.record(null, username, type, LoginOutcome.UNKNOWN_USER, null, caller);
            throw rejected();
        }

        UserAccount user = found.get();

        if (user.isLockedAt(now)) {
            recorder.record(user.getId(), username, type,
                    LoginOutcome.ACCOUNT_LOCKED, "lock still in force", caller);
            throw rejected();
        }
        if (!user.canAuthenticateAt(now)) {
            recorder.record(user.getId(), username, type,
                    LoginOutcome.ACCOUNT_NOT_ACTIVE, "status is " + user.getStatus(), caller);
            throw rejected();
        }

        String storedHash = credentials.findByUserAndCredentialType(user, credentialType)
                .filter(credential -> !credential.isExpiredAt(now))
                .map(UserCredential::getSecretHash)
                .orElse(DUMMY_HASH);

        if (!passwordEncoder.matches(presented, storedHash)) {
            boolean nowLocked = recorder.registerFailure(user.getId(), now);
            recorder.record(user.getId(), username, type,
                    nowLocked ? LoginOutcome.ACCOUNT_LOCKED : LoginOutcome.BAD_CREDENTIALS,
                    nowLocked ? "threshold reached" : null, caller);
            throw rejected();
        }

        return user;
    }

    private LoginResponse completeLogin(UserAccount user, UserDevice device, Caller caller,
                                        String usernameAttempted, Instant now) {
        user.registerSuccessfulLogin(now);
        users.save(user);
        TokenPair tokens = issueTokens(user, device, caller, now);
        recorder.record(user.getId(), usernameAttempted, user.getUserType(),
                LoginOutcome.SUCCESS, null, caller);
        return LoginResponse.authenticated(tokens, AuthenticatedUserResponse.from(user));
    }

    private TokenPair issueTokens(UserAccount user, UserDevice device, Caller caller, Instant now) {
        String refreshToken = tokenService.generateRefreshToken();
        UserSession session = new UserSession(
                user, device, tokenService.hash(refreshToken),
                now.plus(properties.session().refreshTtl()));
        session.describeCaller(caller.ipAddress(), caller.userAgent());
        sessions.save(session);

        return new TokenPair(
                tokenService.issueAccessToken(user, now),
                tokenService.accessTtlSeconds(),
                refreshToken,
                tokenService.refreshTtlSeconds());
    }

    /**
     * The single rejection. Every failure returns this, whatever went wrong, so
     * the response cannot be read as a hint.
     */
    private static BusinessException rejected() {
        return new BusinessException(ErrorCode.UNAUTHENTICATED, "Credentials were not accepted");
    }

    /** A challenge subject that is not a uuid is a challenge we did not raise. */
    private static UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw rejected();
        }
    }
}
