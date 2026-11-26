package com.medexa.rider.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.pharmacy.VerificationStatus;
import com.medexa.rider.RiderAvailabilityStatus;
import com.medexa.rider.dto.RiderDtos.AdminRiderStatusRequest;
import com.medexa.rider.dto.RiderDtos.AdminVerifyRiderRequest;
import com.medexa.rider.dto.RiderDtos.RiderProfileResponse;
import com.medexa.rider.service.RiderService;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/admin/riders")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Admin Riders", description = "Rider onboarding verification, document review, and suspension")
public class AdminRiderController {

    private final RiderService riderService;

    @GetMapping
    @Operation(summary = "List riders for verification and operational oversight")
    public ApiResponse<List<RiderProfileResponse>> list(
            @RequestParam(required = false) VerificationStatus status,
            @RequestParam(required = false) RiderAvailabilityStatus availabilityStatus,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<RiderProfileResponse> page = riderService.listRidersForAdmin(
                status, availabilityStatus, city, q, pageable
        );
        return ApiResponse.paged("Riders fetched.", page);
    }

    @GetMapping("/{riderId}")
    @Operation(summary = "Get full rider KYC, vehicle, and delivery zone details")
    public ApiResponse<RiderProfileResponse> detail(@PathVariable Long riderId) {
        return ApiResponse.ok(riderService.getRiderForAdmin(riderId));
    }

    @GetMapping("/{riderId}/documents/{docType}")
    @Operation(summary = "Inspect uploaded rider identity or driving license document")
    public ResponseEntity<Resource> viewDocument(
            @PathVariable Long riderId,
            @PathVariable String docType) {
        Resource res = riderService.loadRiderDocumentForAdminOrOwner(riderId, docType);
        MediaType type = MediaTypeFactory.getMediaType(res.getFilename()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(res);
    }

    @PostMapping("/{riderId}/verify")
    @Operation(summary = "Approve or reject rider identity and driving license onboarding")
    public ApiResponse<RiderProfileResponse> verify(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long riderId,
            @Valid @RequestBody AdminVerifyRiderRequest request) {
        return ApiResponse.ok(
                "Rider verification decision recorded.",
                riderService.verifyRider(principal.getUserId(), riderId, request)
        );
    }

    @PatchMapping("/{riderId}/status")
    @Operation(summary = "Suspend or reactivate a verified rider")
    public ApiResponse<RiderProfileResponse> updateStatus(
            @PathVariable Long riderId,
            @Valid @RequestBody AdminRiderStatusRequest request) {
        return ApiResponse.ok(
                "Rider account status updated.",
                riderService.updateRiderStatus(riderId, request)
        );
    }
}
