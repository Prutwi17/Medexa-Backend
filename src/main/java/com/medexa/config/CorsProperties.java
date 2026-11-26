package com.medexa.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of("http://localhost:5173");
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private List<String> allowedHeaders = List.of("Authorization", "Content-Type", "X-Correlation-Id", "Accept", "Origin");
    private List<String> exposedHeaders = List.of("X-Correlation-Id");
    private boolean allowCredentials = true;
    private long maxAgeSeconds = 3600L;
}
