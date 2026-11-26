package com.medexa.customer.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.customer.dto.CustomerDtos.CustomerProfileResponse;
import com.medexa.customer.dto.CustomerDtos.UpdateCustomerProfileRequest;
import com.medexa.customer.service.CustomerProfileService;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/profile")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Customer Profile", description = "Authenticated customer profile management")
public class CustomerProfileController {

    private final CustomerProfileService profileService;

    @GetMapping
    @Operation(summary = "Get authenticated customer profile")
    public ApiResponse<CustomerProfileResponse> getProfile(
            @AuthenticationPrincipal MedexaUserPrincipal principal) {
        return ApiResponse.ok(profileService.getProfile(principal.getUserId()));
    }

    @PutMapping
    @Operation(summary = "Update authenticated customer profile")
    public ApiResponse<CustomerProfileResponse> updateProfile(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody UpdateCustomerProfileRequest request) {
        return ApiResponse.ok(
                "Customer profile updated.",
                profileService.updateProfile(principal.getUserId(), request)
        );
    }
}
