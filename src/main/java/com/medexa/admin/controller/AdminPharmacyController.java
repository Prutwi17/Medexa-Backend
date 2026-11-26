package com.medexa.admin.controller;

import com.medexa.admin.dto.AdminDtos.PharmacyStatusUpdateRequest;
import com.medexa.admin.dto.AdminDtos.PharmacyVerificationRequest;
import com.medexa.admin.service.AdminOversightService;
import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.pharmacy.VerificationStatus;
import com.medexa.pharmacy.dto.PharmacyDtos.PharmacyProfileResponse;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/pharmacies")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Admin Pharmacies", description = "Pharmacy license verification, approval, and suspension")
public class AdminPharmacyController {

    private final AdminOversightService adminOversightService;

    @GetMapping
    @Operation(summary = "List pharmacies for verification and oversight")
    public ApiResponse<List<PharmacyProfileResponse>> list(
            @RequestParam(required = false) VerificationStatus status,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        Page<PharmacyProfileResponse> page = adminOversightService.listPharmacies(status, q, pageable);
        return ApiResponse.paged("Pharmacies fetched.", page);
    }

    @GetMapping("/{pharmacyId}")
    @Operation(summary = "Get pharmacy and license review details")
    public ApiResponse<PharmacyProfileResponse> getOne(@PathVariable Long pharmacyId) {
        return ApiResponse.ok(adminOversightService.getPharmacyDetail(pharmacyId));
    }

    @PostMapping("/{pharmacyId}/verify")
    @Operation(summary = "Approve or reject pharmacy onboarding and drug license")
    public ApiResponse<PharmacyProfileResponse> verify(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long pharmacyId,
            @Valid @RequestBody PharmacyVerificationRequest request) {
        return ApiResponse.ok(
                "Pharmacy verification decision saved.",
                adminOversightService.verifyPharmacy(principal.getUserId(), pharmacyId, request)
        );
    }

    @PatchMapping("/{pharmacyId}/status")
    @Operation(summary = "Suspend or reactivate a verified pharmacy")
    public ApiResponse<PharmacyProfileResponse> updateStatus(
            @PathVariable Long pharmacyId,
            @Valid @RequestBody PharmacyStatusUpdateRequest request) {
        return ApiResponse.ok(
                "Pharmacy status updated.",
                adminOversightService.updatePharmacyStatus(pharmacyId, request)
        );
    }
}
