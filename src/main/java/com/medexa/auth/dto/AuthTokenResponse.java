package com.medexa.auth.dto;

import com.medexa.user.UserSummaryResponse;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserSummaryResponse user
) {
    public static AuthTokenResponse bearer(String accessToken, long expiresInSeconds, UserSummaryResponse user) {
        return new AuthTokenResponse(accessToken, "Bearer", expiresInSeconds, user);
    }
}
