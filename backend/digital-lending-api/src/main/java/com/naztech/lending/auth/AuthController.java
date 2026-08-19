package com.naztech.lending.auth;

import com.naztech.lending.auth.dto.AuthenticatedUserResponse;
import com.naztech.lending.auth.dto.BankLoginRequest;
import com.naztech.lending.auth.dto.CustomerLoginRequest;
import com.naztech.lending.auth.dto.LoginResponse;
import com.naztech.lending.auth.dto.MfaVerificationRequest;
import com.naztech.lending.auth.dto.OtpRequest;
import com.naztech.lending.auth.dto.OtpVerificationRequest;
import com.naztech.lending.auth.dto.RefreshTokenRequest;
import com.naztech.lending.auth.dto.TokenPair;
import com.naztech.lending.auth.service.AuthenticationService;
import com.naztech.lending.auth.service.Caller;
import com.naztech.lending.auth.service.OtpService;
import com.naztech.lending.common.api.ApiResponse;
import com.naztech.lending.common.exception.BusinessException;
import com.naztech.lending.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-in for the three actors the platform serves.
 *
 * <p>Everything under {@code /auth} except {@code /me} and {@code /logout} is
 * unauthenticated by necessity: it is how a caller obtains a token in the first
 * place. Every one of them is rate-limit sensitive and every attempt is written
 * to the audit trail.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Sign-in, second factor, session refresh and sign-out")
public class AuthController {

    private final AuthenticationService authentication;
    private final OtpService otpService;

    public AuthController(AuthenticationService authentication, OtpService otpService) {
        this.authentication = authentication;
        this.otpService = otpService;
    }

    @PostMapping("/bank/login")
    @Operation(summary = "Bank user sign-in",
            description = "Employee id and password. Answers MFA_REQUIRED with a challenge id when "
                    + "the identity has a second factor enabled, otherwise returns tokens. Every "
                    + "failure returns the same message whatever the cause.")
    public ApiResponse<LoginResponse> bankLogin(@Valid @RequestBody BankLoginRequest request,
                                                HttpServletRequest http) {
        return ApiResponse.success(authentication.authenticateBankUser(request, callerOf(http)));
    }

    @PostMapping("/bank/mfa")
    @Operation(summary = "Complete a bank user second factor",
            description = "Presents the code for a challenge raised by /bank/login.")
    public ApiResponse<LoginResponse> verifyMfa(@Valid @RequestBody MfaVerificationRequest request,
                                                HttpServletRequest http) {
        return ApiResponse.success(authentication.verifyMfa(request, callerOf(http)));
    }

    @PostMapping("/customer/otp")
    @Operation(summary = "Send a customer one-time passcode",
            description = "Raises a challenge used to bind a handset. Answers the same way whether "
                    + "or not the mobile number is registered, so the endpoint cannot be used to "
                    + "discover who banks here.")
    public ApiResponse<Map<String, Object>> requestOtp(@Valid @RequestBody OtpRequest request) {
        OtpService.Challenge challenge =
                otpService.issue(request.mobile(), OtpService.Purpose.DEVICE_BINDING);

        // developmentCode is populated only when dlp.auth.otp.expose-in-response
        // is on, which no environment a customer can reach may set.
        Map<String, Object> body = challenge.developmentCode() == null
                ? Map.of("challengeId", challenge.challengeId(),
                         "expiresInSeconds", challenge.expiresInSeconds())
                : Map.of("challengeId", challenge.challengeId(),
                         "expiresInSeconds", challenge.expiresInSeconds(),
                         "developmentCode", challenge.developmentCode());
        return ApiResponse.success(body);
    }

    @PostMapping("/customer/otp/verify")
    @Operation(summary = "Bind a handset with a one-time passcode",
            description = "A verified code promotes the device to TRUSTED, which is what allows a "
                    + "6 digit PIN to stand as authentication from it afterwards.")
    public ApiResponse<Map<String, String>> verifyOtp(@Valid @RequestBody OtpVerificationRequest request) {
        OtpService.Verification result = otpService.verify(
                request.challengeId(), request.code(),
                request.mobile(), OtpService.Purpose.DEVICE_BINDING);

        if (result != OtpService.Verification.VERIFIED) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "The code was not accepted");
        }
        authentication.bindDevice(request.mobile(), request.device());
        return ApiResponse.success(Map.of("deviceStatus", "TRUSTED"));
    }

    @PostMapping("/customer/login")
    @Operation(summary = "Customer sign-in",
            description = "Mobile number and 6 digit PIN, accepted only from a device already bound "
                    + "to this customer.")
    public ApiResponse<LoginResponse> customerLogin(@Valid @RequestBody CustomerLoginRequest request,
                                                    HttpServletRequest http) {
        return ApiResponse.success(authentication.authenticateCustomer(request, callerOf(http)));
    }

    @PostMapping("/token/refresh")
    @Operation(summary = "Exchange a refresh token",
            description = "Returns a new pair and rotates the refresh token, so a stolen one stops "
                    + "working the moment the real client refreshes.")
    public ApiResponse<TokenPair> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                          HttpServletRequest http) {
        return ApiResponse.success(authentication.refresh(request.refreshToken(), callerOf(http)));
    }

    @PostMapping("/logout")
    @Operation(summary = "End a session",
            description = "Revokes the refresh token. Idempotent: an unknown token is already ended.")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authentication.logout(request.refreshToken());
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in identity",
            description = "Resolved from the access token. Carries no permissions: authorisation "
                    + "arrives in Milestone 6.")
    public ApiResponse<AuthenticatedUserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(authentication.describe(UUID.fromString(jwt.getSubject())));
    }

    /**
     * The caller as far as the audit trail is concerned. The proxy header is
     * trusted only because the gateway in front of this service is the one
     * setting it; nothing downstream makes an authorisation decision on it.
     */
    private static Caller callerOf(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
        return new Caller(
                ip, request.getHeader("User-Agent"), request.getHeader("X-Device-Id"));
    }
}
