package com.medexa.common.web;

import com.medexa.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Public service availability probe")
public class HealthController {

    @GetMapping
    @Operation(summary = "Check API readiness", description = "Returns public service health status without exposing internal infrastructure details.")
    public ApiResponse<HealthStatusResponse> checkHealth() {
        return ApiResponse.ok(
                "Medexa API is operational",
                new HealthStatusResponse("UP", "medexa-backend", Instant.now())
        );
    }
}
