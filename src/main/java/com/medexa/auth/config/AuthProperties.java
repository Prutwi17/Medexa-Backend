package com.medexa.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String googleClientId = "";
    private boolean requireEmailVerificationForLogin = true;
    private long emailVerificationTtlMinutes = 1440L;
    private long passwordResetTtlMinutes = 30L;
    private RateLimit rateLimit = new RateLimit();
    private Cookie cookie = new Cookie();

    @Getter
    @Setter
    public static class RateLimit {
        private int maxAttempts = 5;
        private long windowMinutes = 15L;
    }

    @Getter
    @Setter
    public static class Cookie {
        private String name = "medexa_refresh_token";
        private boolean secure = false;
        private String sameSite = "Lax";
        private String path = "/api/v1/auth";
    }
}
