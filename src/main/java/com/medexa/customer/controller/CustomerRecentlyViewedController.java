package com.medexa.customer.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.customer.service.RecentlyViewedService;
import com.medexa.medicine.dto.MedicineDtos.PublicMedicineResponse;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/recently-viewed")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Recently Viewed Medicines", description = "Authenticated customer browsing history")
public class CustomerRecentlyViewedController {

    private final RecentlyViewedService recentlyViewedService;

    @GetMapping
    @Operation(summary = "List recently viewed medicines for the authenticated customer")
    public ApiResponse<List<PublicMedicineResponse>> list(
            @AuthenticationPrincipal MedexaUserPrincipal principal) {
        return ApiResponse.ok(recentlyViewedService.listRecentlyViewed(principal.getUserId()));
    }

    @PostMapping("/{medicineId}")
    @Operation(summary = "Record a medicine view for the authenticated customer")
    public ApiResponse<Void> record(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long medicineId) {
        recentlyViewedService.recordView(principal.getUserId(), medicineId);
        return ApiResponse.ok("Medicine view recorded.", null);
    }
}
