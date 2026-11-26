package com.medexa.security;

import com.medexa.auth.config.JwtProperties;
import com.medexa.common.exception.UnauthorizedException;
import com.medexa.user.User;
import com.medexa.user.UserRole;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtProperties jwtProperties;

    public AccessTokenPayload generateAccessToken(User user) {
        byte[] secretBytes = requireSecretBytes();
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtProperties.getAccessExpirationMs());

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(jwtProperties.getIssuer())
                .subject(String.valueOf(user.getId()))
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();

        try {
            SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            JWSSigner signer = new MACSigner(secretBytes);
            signedJwt.sign(signer);
            long expiresInSeconds = Math.max(1L, jwtProperties.getAccessExpirationMs() / 1000L);
            return new AccessTokenPayload(signedJwt.serialize(), expiresInSeconds);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to sign JWT access token", ex);
        }
    }

    public DecodedAccessToken parseAndValidateAccessToken(String token) {
        byte[] secretBytes = requireSecretBytes();
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secretBytes);
            if (!signedJwt.verify(verifier)) {
                throw new UnauthorizedException("Invalid access token signature.");
            }

            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            Instant now = Instant.now();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.toInstant().isAfter(now)) {
                throw new UnauthorizedException("Access token has expired.");
            }
            if (!jwtProperties.getIssuer().equals(claims.getIssuer())) {
                throw new UnauthorizedException("Invalid access token issuer.");
            }

            Long userId = Long.parseLong(claims.getSubject());
            String email = claims.getStringClaim("email");
            UserRole role = UserRole.valueOf(claims.getStringClaim("role"));
            return new DecodedAccessToken(userId, email, role);
        } catch (ParseException | JOSEException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid or malformed access token.");
        }
    }

    private byte[] requireSecretBytes() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET environment variable must be configured.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes (256 bits) for HS256.");
        }
        return bytes;
    }

    public record AccessTokenPayload(String token, long expiresInSeconds) {
    }

    public record DecodedAccessToken(Long userId, String email, UserRole role) {
    }
}
