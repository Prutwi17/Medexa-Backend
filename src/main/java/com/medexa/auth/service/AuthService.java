package com.medexa.auth.service;

import com.medexa.auth.config.AuthProperties;
import com.medexa.auth.config.JwtProperties;
import com.medexa.auth.dto.AuthTokenResponse;
import com.medexa.auth.dto.CustomerRegisterRequest;
import com.medexa.auth.dto.ForgotPasswordRequest;
import com.medexa.auth.dto.GoogleLoginRequest;
import com.medexa.auth.dto.LoginRequest;
import com.medexa.auth.dto.PharmacyRegisterRequest;
import com.medexa.auth.dto.RegistrationResponse;
import com.medexa.auth.dto.ResendVerificationRequest;
import com.medexa.auth.dto.ResetPasswordRequest;
import com.medexa.auth.dto.RiderRegisterRequest;
import com.medexa.auth.dto.VerifyEmailRequest;
import com.medexa.auth.entity.AuthMethod;
import com.medexa.auth.entity.EmailVerificationToken;
import com.medexa.auth.entity.LoginOutcome;
import com.medexa.auth.entity.PasswordResetToken;
import com.medexa.auth.entity.RefreshToken;
import com.medexa.auth.exception.AccountStatusAuthException;
import com.medexa.auth.repository.EmailVerificationTokenRepository;
import com.medexa.auth.repository.PasswordResetTokenRepository;
import com.medexa.auth.repository.RefreshTokenRepository;
import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.ConflictException;
import com.medexa.common.exception.ErrorCode;
import com.medexa.common.exception.ForbiddenException;
import com.medexa.common.exception.RateLimitExceededException;
import com.medexa.common.exception.UnauthorizedException;
import com.medexa.pharmacy.Pharmacy;
import com.medexa.pharmacy.PharmacyLicense;
import com.medexa.pharmacy.PharmacyLicenseRepository;
import com.medexa.pharmacy.PharmacyRepository;
import com.medexa.pharmacy.VerificationStatus;
import com.medexa.rider.Rider;
import com.medexa.rider.RiderAvailabilityStatus;
import com.medexa.rider.RiderRepository;
import com.medexa.security.JwtTokenService;
import com.medexa.security.TokenHashUtils;
import com.medexa.user.AccountStatus;
import com.medexa.user.AuthProvider;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import com.medexa.user.UserRole;
import com.medexa.user.UserSummaryResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyLicenseRepository pharmacyLicenseRepository;
    private final RiderRepository riderRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final AuthProperties authProperties;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final LoginRateLimiterService rateLimiterService;
    private final LoginHistoryService loginHistoryService;
    private final AuthEmailService authEmailService;
    private final RefreshTokenCookieService cookieService;

    @Transactional
    public RegistrationResponse registerCustomer(CustomerRegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("An account with this email address already exists.");
        }

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .phoneNumber(trimOrNull(request.phoneNumber()))
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .build();
        userRepository.save(user);

        issueEmailVerificationToken(user);

        return new RegistrationResponse(
                UserSummaryResponse.from(user),
                authProperties.isRequireEmailVerificationForLogin(),
                false
        );
    }

    @Transactional
    public RegistrationResponse registerPharmacy(PharmacyRegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("An account with this email address already exists.");
        }
        if (pharmacyLicenseRepository.existsByLicenseNumberIgnoreCase(request.licenseNumber().trim())) {
            throw new ConflictException("A pharmacy with this drug license number is already registered.");
        }

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .phoneNumber(request.phoneNumber().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.PHARMACY)
                .accountStatus(AccountStatus.PENDING_APPROVAL)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .build();
        userRepository.save(user);

        Pharmacy pharmacy = Pharmacy.builder()
                .user(user)
                .pharmacyName(request.pharmacyName().trim())
                .slug(generateUniquePharmacySlug(request.pharmacyName()))
                .contactPhone(request.phoneNumber().trim())
                .addressLine(request.addressLine().trim())
                .city(request.city().trim())
                .state(request.state().trim())
                .postalCode(request.postalCode().trim())
                .verificationStatus(VerificationStatus.PENDING_VERIFICATION)
                .acceptingOrders(false)
                .build();
        pharmacyRepository.save(pharmacy);

        PharmacyLicense license = PharmacyLicense.builder()
                .pharmacy(pharmacy)
                .licenseNumber(request.licenseNumber().trim())
                .issuingAuthority(request.issuingAuthority().trim())
                .pharmacistName(request.pharmacistName().trim())
                .pharmacistRegistrationNumber(request.pharmacistRegistrationNumber().trim())
                .expiryDate(request.licenseExpiryDate())
                .documentUrl(trimOrNull(request.licenseDocumentUrl()))
                .verificationStatus(VerificationStatus.PENDING_VERIFICATION)
                .build();
        pharmacyLicenseRepository.save(license);

        issueEmailVerificationToken(user);

        return new RegistrationResponse(
                UserSummaryResponse.from(user),
                true,
                true
        );
    }

    @Transactional
    public RegistrationResponse registerRider(RiderRegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("An account with this email address already exists.");
        }
        if (riderRepository.existsByDrivingLicenseNumberIgnoreCase(request.drivingLicenseNumber().trim())) {
            throw new ConflictException("A rider with this driving license number is already registered.");
        }

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .phoneNumber(request.phoneNumber().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.RIDER)
                .accountStatus(AccountStatus.PENDING_APPROVAL)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .build();
        userRepository.save(user);

        Rider rider = Rider.builder()
                .user(user)
                .phoneNumber(request.phoneNumber().trim())
                .vehicleType(request.vehicleType())
                .vehicleRegistrationNumber(request.vehicleRegistrationNumber().trim().toUpperCase(Locale.ROOT))
                .drivingLicenseNumber(request.drivingLicenseNumber().trim().toUpperCase(Locale.ROOT))
                .serviceCity(request.serviceCity().trim())
                .verificationStatus(VerificationStatus.PENDING_VERIFICATION)
                .availabilityStatus(RiderAvailabilityStatus.OFFLINE)
                .build();
        riderRepository.save(rider);

        issueEmailVerificationToken(user);

        return new RegistrationResponse(
                UserSummaryResponse.from(user),
                true,
                true
        );
    }

    @Transactional
    public AuthTokenResponse loginUser(
            LoginRequest request,
            String clientIp,
            String userAgent,
            HttpServletResponse response) {
        return performPasswordLogin(request, false, clientIp, userAgent, response);
    }

    @Transactional
    public AuthTokenResponse loginAdmin(
            LoginRequest request,
            String clientIp,
            String userAgent,
            HttpServletResponse response) {
        return performPasswordLogin(request, true, clientIp, userAgent, response);
    }

    @Transactional(noRollbackFor = AccountStatusAuthException.class)
    public AuthTokenResponse loginWithGoogle(
            GoogleLoginRequest request,
            String clientIp,
            String userAgent,
            HttpServletResponse response) {
        UserRole requestedRole = request.role() != null ? request.role() : UserRole.CUSTOMER;
        if (requestedRole == UserRole.ADMIN) {
            throw new ForbiddenException("Administrator accounts cannot sign in via public Google OAuth.");
        }

        GoogleTokenVerifier.GoogleIdentity identity = googleTokenVerifier.verify(request.idToken());
        String email = identity.email();

        User user = userRepository.findByGoogleSub(identity.subject())
                .or(() -> userRepository.findByEmailIgnoreCase(email))
                .orElse(null);

        if (user == null) {
            AccountStatus initialStatus = requestedRole == UserRole.CUSTOMER
                    ? AccountStatus.ACTIVE
                    : AccountStatus.PENDING_APPROVAL;

            user = User.builder()
                    .fullName(identity.fullName())
                    .email(email)
                    .role(requestedRole)
                    .accountStatus(initialStatus)
                    .authProvider(AuthProvider.GOOGLE)
                    .googleSub(identity.subject())
                    .emailVerified(true)
                    .build();
            userRepository.saveAndFlush(user);

            if (initialStatus == AccountStatus.PENDING_APPROVAL) {
                loginHistoryService.recordFailure(
                        null, email, AuthMethod.GOOGLE, LoginOutcome.PENDING_APPROVAL,
                        "New Google partner account awaiting admin verification", clientIp, userAgent);
                throw new AccountStatusAuthException(
                        HttpStatus.FORBIDDEN,
                        ErrorCode.ACCOUNT_PENDING_APPROVAL,
                        "Your account is pending verification and admin approval."
                );
            }
        } else {
            if (user.getRole() == UserRole.ADMIN) {
                loginHistoryService.recordFailure(
                        user, email, AuthMethod.GOOGLE, LoginOutcome.ACCOUNT_BLOCKED,
                        "Admin Google login blocked", clientIp, userAgent);
                throw new ForbiddenException("Administrator accounts must sign in through the admin portal.");
            }
            if (user.getGoogleSub() == null) {
                user.setGoogleSub(identity.subject());
                user.setAuthProvider(user.getPasswordHash() != null ? AuthProvider.BOTH : AuthProvider.GOOGLE);
            } else if (!user.getGoogleSub().equals(identity.subject())) {
                throw new UnauthorizedException("Google account mismatch for this email.");
            }
            if (!user.isEmailVerified()) {
                user.setEmailVerified(true);
            }
        }

        enforceAccountEligibility(user, AuthMethod.GOOGLE, clientIp, userAgent, false);

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return issueSessionTokens(user, AuthMethod.GOOGLE, clientIp, userAgent, response);
    }

    @Transactional(noRollbackFor = {UnauthorizedException.class, AccountStatusAuthException.class})
    public AuthTokenResponse refreshSession(
            HttpServletRequest request,
            HttpServletResponse response,
            String clientIp,
            String userAgent) {
        String rawRefreshToken = cookieService.extractRefreshToken(request);
        if (rawRefreshToken == null) {
            throw new UnauthorizedException("Refresh session is missing.");
        }

        String tokenHash = TokenHashUtils.sha256Hex(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    cookieService.clearRefreshCookie(response);
                    return new UnauthorizedException("Invalid refresh session.");
                });

        Instant now = Instant.now();
        User user = existing.getUser();

        if (existing.isRevoked()) {
            // Token reuse indicates theft or replay: revoke entire token family immediately.
            refreshTokenRepository.revokeFamily(existing.getTokenFamilyId(), now, "TOKEN_REUSE_DETECTED");
            cookieService.clearRefreshCookie(response);
            loginHistoryService.recordFailure(
                    user, user.getEmail(), AuthMethod.REFRESH_TOKEN, LoginOutcome.TOKEN_REUSE_DETECTED,
                    "Revoked refresh token replayed", clientIp, userAgent);
            throw new UnauthorizedException("Session compromised. Please sign in again.");
        }

        if (existing.isExpired(now)) {
            existing.setRevokedAt(now);
            existing.setRevocationReason("EXPIRED");
            refreshTokenRepository.save(existing);
            cookieService.clearRefreshCookie(response);
            loginHistoryService.recordFailure(
                    user, user.getEmail(), AuthMethod.REFRESH_TOKEN, LoginOutcome.TOKEN_INVALID,
                    "Refresh token expired", clientIp, userAgent);
            throw new UnauthorizedException("Refresh session expired. Please sign in again.");
        }

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            refreshTokenRepository.revokeFamily(existing.getTokenFamilyId(), now, "ACCOUNT_INACTIVE");
            cookieService.clearRefreshCookie(response);
            throw new AccountStatusAuthException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.ACCOUNT_SUSPENDED,
                    "Your account is currently not active."
            );
        }

        String nextRawToken = TokenHashUtils.generateOpaqueToken();
        String nextTokenHash = TokenHashUtils.sha256Hex(nextRawToken);

        existing.setRevokedAt(now);
        existing.setRevocationReason("ROTATED");
        existing.setReplacedByTokenHash(nextTokenHash);
        refreshTokenRepository.save(existing);

        RefreshToken replacement = RefreshToken.builder()
                .user(user)
                .tokenHash(nextTokenHash)
                .tokenFamilyId(existing.getTokenFamilyId())
                .expiresAt(now.plusMillis(jwtProperties.getRefreshExpirationMs()))
                .ipAddress(clientIp)
                .userAgent(truncateUserAgent(userAgent))
                .build();
        refreshTokenRepository.save(replacement);

        cookieService.writeRefreshCookie(response, nextRawToken, jwtProperties.getRefreshExpirationMs());
        JwtTokenService.AccessTokenPayload accessToken = jwtTokenService.generateAccessToken(user);

        return AuthTokenResponse.bearer(
                accessToken.token(),
                accessToken.expiresInSeconds(),
                UserSummaryResponse.from(user)
        );
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = cookieService.extractRefreshToken(request);
        if (rawRefreshToken != null) {
            String tokenHash = TokenHashUtils.sha256Hex(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                refreshTokenRepository.revokeFamily(token.getTokenFamilyId(), Instant.now(), "USER_LOGOUT");
            });
        }
        cookieService.clearRefreshCookie(response);
    }

    @Transactional
    public void logoutAll(Long userId, HttpServletResponse response) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now(), "LOGOUT_ALL_DEVICES");
        cookieService.clearRefreshCookie(response);
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        String tokenHash = TokenHashUtils.sha256Hex(request.token().trim());
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AccountStatusAuthException(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.INVALID_TOKEN,
                        "Email verification link is invalid or has expired."
                ));

        Instant now = Instant.now();
        if (!verificationToken.isUsable(now)) {
            throw new AccountStatusAuthException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_TOKEN,
                    "Email verification link is invalid or has already been used."
            );
        }

        verificationToken.setUsedAt(now);
        emailVerificationTokenRepository.save(verificationToken);

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                emailVerificationTokenRepository.invalidateActiveTokensForUser(user.getId(), Instant.now());
                issueEmailVerificationToken(user);
            }
        });
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, String clientIp) {
        String normalizedEmail = normalizeEmail(request.email());
        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            if (user.getAccountStatus() == AccountStatus.SUSPENDED
                    || user.getAccountStatus() == AccountStatus.DEACTIVATED) {
                return;
            }
            Instant now = Instant.now();
            passwordResetTokenRepository.invalidateActiveTokensForUser(user.getId(), now);

            String rawToken = TokenHashUtils.generateOpaqueToken();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(TokenHashUtils.sha256Hex(rawToken))
                    .expiresAt(now.plus(Duration.ofMinutes(authProperties.getPasswordResetTtlMinutes())))
                    .requestedIp(clientIp)
                    .build();
            passwordResetTokenRepository.save(resetToken);

            authEmailService.sendPasswordReset(user.getEmail(), user.getFullName(), rawToken);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = TokenHashUtils.sha256Hex(request.token().trim());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AccountStatusAuthException(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.INVALID_TOKEN,
                        "Password reset link is invalid or has expired."
                ));

        Instant now = Instant.now();
        if (!resetToken.isUsable(now)) {
            throw new AccountStatusAuthException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_TOKEN,
                    "Password reset link is invalid or has already been used."
            );
        }

        resetToken.setUsedAt(now);
        passwordResetTokenRepository.save(resetToken);

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            user.setAuthProvider(AuthProvider.BOTH);
        }
        userRepository.save(user);

        // Revoke all refresh tokens after password reset
        refreshTokenRepository.revokeAllForUser(user.getId(), now, "PASSWORD_RESET");
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user no longer exists."));
        return UserSummaryResponse.from(user);
    }

    private AuthTokenResponse performPasswordLogin(
            LoginRequest request,
            boolean adminPortal,
            String clientIp,
            String userAgent,
            HttpServletResponse response) {
        String normalizedEmail = normalizeEmail(request.email());

        try {
            rateLimiterService.assertAllowed(normalizedEmail, clientIp);
        } catch (RateLimitExceededException ex) {
            loginHistoryService.recordFailure(
                    null, normalizedEmail, AuthMethod.PASSWORD, LoginOutcome.RATE_LIMITED,
                    "Rate limit exceeded", clientIp, userAgent);
            throw ex;
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            rateLimiterService.recordFailure(normalizedEmail, clientIp);
            loginHistoryService.recordFailure(
                    user, normalizedEmail, AuthMethod.PASSWORD, LoginOutcome.INVALID_CREDENTIALS,
                    "Invalid email or password", clientIp, userAgent);
            throw new UnauthorizedException("Invalid email or password.");
        }

        if (adminPortal && user.getRole() != UserRole.ADMIN) {
            rateLimiterService.recordFailure(normalizedEmail, clientIp);
            loginHistoryService.recordFailure(
                    user, normalizedEmail, AuthMethod.PASSWORD, LoginOutcome.INVALID_CREDENTIALS,
                    "Non-admin attempted admin login", clientIp, userAgent);
            throw new UnauthorizedException("Invalid email or password.");
        }

        if (!adminPortal && user.getRole() == UserRole.ADMIN) {
            rateLimiterService.recordFailure(normalizedEmail, clientIp);
            loginHistoryService.recordFailure(
                    user, normalizedEmail, AuthMethod.PASSWORD, LoginOutcome.ACCOUNT_BLOCKED,
                    "Admin attempted non-admin portal login", clientIp, userAgent);
            throw new ForbiddenException("Administrator accounts must sign in using the admin login portal.");
        }

        boolean requireEmailCheck = authProperties.isRequireEmailVerificationForLogin() && user.getRole() != UserRole.ADMIN;
        enforceAccountEligibility(user, AuthMethod.PASSWORD, clientIp, userAgent, requireEmailCheck);

        rateLimiterService.reset(normalizedEmail, clientIp);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return issueSessionTokens(user, AuthMethod.PASSWORD, clientIp, userAgent, response);
    }

    private void enforceAccountEligibility(
            User user,
            AuthMethod method,
            String clientIp,
            String userAgent,
            boolean enforceEmailVerification) {
        if (user.getAccountStatus() == AccountStatus.PENDING_APPROVAL) {
            loginHistoryService.recordFailure(
                    user, user.getEmail(), method, LoginOutcome.PENDING_APPROVAL,
                    "Account awaiting admin verification", clientIp, userAgent);
            throw new AccountStatusAuthException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.ACCOUNT_PENDING_APPROVAL,
                    "Your account is pending verification and admin approval."
            );
        }

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            loginHistoryService.recordFailure(
                    user, user.getEmail(), method, LoginOutcome.ACCOUNT_BLOCKED,
                    "Account status: " + user.getAccountStatus(), clientIp, userAgent);
            throw new AccountStatusAuthException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.ACCOUNT_SUSPENDED,
                    "Your account is currently " + user.getAccountStatus().name().toLowerCase(Locale.ROOT) + "."
            );
        }

        if (enforceEmailVerification && !user.isEmailVerified()) {
            loginHistoryService.recordFailure(
                    user, user.getEmail(), method, LoginOutcome.EMAIL_NOT_VERIFIED,
                    "Email not verified", clientIp, userAgent);
            throw new AccountStatusAuthException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.EMAIL_NOT_VERIFIED,
                    "Please verify your email address before signing in."
            );
        }
    }

    private AuthTokenResponse issueSessionTokens(
            User user,
            AuthMethod method,
            String clientIp,
            String userAgent,
            HttpServletResponse response) {
        Instant now = Instant.now();
        String rawRefreshToken = TokenHashUtils.generateOpaqueToken();
        String refreshTokenHash = TokenHashUtils.sha256Hex(rawRefreshToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .tokenFamilyId(UUID.randomUUID().toString())
                .expiresAt(now.plusMillis(jwtProperties.getRefreshExpirationMs()))
                .ipAddress(clientIp)
                .userAgent(truncateUserAgent(userAgent))
                .build();
        refreshTokenRepository.save(refreshToken);

        cookieService.writeRefreshCookie(response, rawRefreshToken, jwtProperties.getRefreshExpirationMs());
        loginHistoryService.recordSuccess(user, user.getEmail(), method, clientIp, userAgent);

        JwtTokenService.AccessTokenPayload accessToken = jwtTokenService.generateAccessToken(user);
        return AuthTokenResponse.bearer(
                accessToken.token(),
                accessToken.expiresInSeconds(),
                UserSummaryResponse.from(user)
        );
    }

    private void issueEmailVerificationToken(User user) {
        String rawToken = TokenHashUtils.generateOpaqueToken();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(TokenHashUtils.sha256Hex(rawToken))
                .expiresAt(Instant.now().plus(Duration.ofMinutes(authProperties.getEmailVerificationTtlMinutes())))
                .build();
        emailVerificationTokenRepository.save(token);
        authEmailService.sendEmailVerification(user.getEmail(), user.getFullName(), rawToken);
    }

    private String generateUniquePharmacySlug(String pharmacyName) {
        String normalized = Normalizer.normalize(pharmacyName, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        String baseSlug = normalized.isEmpty() ? "pharmacy" : normalized;
        String candidate = baseSlug;
        int suffix = 1;
        while (pharmacyRepository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + suffix++;
        }
        return candidate;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent;
    }
}
