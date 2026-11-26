package com.medexa.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String issuer = "medexa-api";
    private String secret = "";
    private long accessExpirationMs = 900_000L;
    private long refreshExpirationMs = 604_800_000L;
}
