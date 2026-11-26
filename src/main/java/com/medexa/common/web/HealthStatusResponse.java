package com.medexa.common.web;

import java.time.Instant;

public record HealthStatusResponse(
        String status,
        String service,
        Instant timestamp
) {
}
