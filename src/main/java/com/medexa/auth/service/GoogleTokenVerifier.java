package com.medexa.auth.service;

import com.medexa.auth.config.AuthProperties;
import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.UnauthorizedException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleTokenVerifier {

    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> ALLOWED_ISSUERS = List.of(
            "https://accounts.google.com",
            "accounts.google.com"
    );

    private final AuthProperties authProperties;
    private volatile JwtDecoder cachedDecoder;

    public GoogleIdentity verify(String rawIdToken) {
        String clientId = authProperties.getGoogleClientId();
        if (clientId == null || clientId.isBlank()) {
            throw new BadRequestException("Google OAuth is not configured on the server.");
        }

        try {
            Jwt jwt = getOrCreateDecoder(clientId).decode(rawIdToken);
            Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
            if (!Boolean.TRUE.equals(emailVerified)) {
                throw new UnauthorizedException("Google account email address is not verified.");
            }

            String subject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            String fullName = jwt.getClaimAsString("name");

            if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
                throw new UnauthorizedException("Google identity token is missing required profile claims.");
            }

            return new GoogleIdentity(
                    subject,
                    email.trim().toLowerCase(),
                    fullName != null && !fullName.isBlank() ? fullName.trim() : email
            );
        } catch (JwtException ex) {
            log.warn("Google ID token verification failed: {}", ex.getMessage());
            throw new UnauthorizedException("Invalid or expired Google identity token.");
        }
    }

    private JwtDecoder getOrCreateDecoder(String clientId) {
        JwtDecoder existing = cachedDecoder;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (cachedDecoder == null) {
                NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build();
                OAuth2TokenValidator<Jwt> issuerAndAudienceValidator = token -> {
                    String issuer = token.getIssuer() != null ? token.getIssuer().toString() : null;
                    if (issuer == null || !ALLOWED_ISSUERS.contains(issuer)) {
                        return OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "Invalid Google token issuer", null));
                    }
                    List<String> audiences = token.getAudience();
                    if (audiences == null || !audiences.contains(clientId)) {
                        return OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "Google token audience mismatch", null));
                    }
                    return OAuth2TokenValidatorResult.success();
                };
                decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(),
                        issuerAndAudienceValidator
                ));
                cachedDecoder = decoder;
            }
            return cachedDecoder;
        }
    }

    public record GoogleIdentity(String subject, String email, String fullName) {
    }
}
