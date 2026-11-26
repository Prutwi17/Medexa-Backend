package com.medexa.rider.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.rider.dto.RiderDtos.RiderDeliveryZoneRequest;
import com.medexa.rider.dto.RiderDtos.RiderDeliveryZoneResponse;
import com.medexa.rider.dto.RiderDtos.RiderEarningsSummaryResponse;
import com.medexa.rider.dto.RiderDtos.RiderProfileResponse;
import com.medexa.rider.dto.RiderDtos.RiderZoneStatusRequest;
import com.medexa.rider.dto.RiderDtos.UpdateRiderAvailabilityRequest;
import com.medexa.rider.dto.RiderDtos.UpdateRiderLocationRequest;
import com.medexa.rider.dto.RiderDtos.UpdateRiderProfileRequest;
import com.medexa.rider.service.RiderService;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/rider")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Rider Portal", description = "Rider profile, KYC documents, availability, delivery zones, and earnings")
public class RiderProfileController {

    private final RiderService riderService;

    @GetMapping("/profile")
    @Operation(summary = "Get authenticated rider profile, verification state, zones, and workload")
    public ApiResponse<RiderProfileResponse> getProfile(@AuthenticationPrincipal MedexaUserPrincipal principal) {
        return ApiResponse.ok(riderService.getOwnProfile(principal.getUserId()));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update rider vehicle, identity, and driving license details")
    public ApiResponse<RiderProfileResponse> updateProfile(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody UpdateRiderProfileRequest request) {
        return ApiResponse.ok(
                "Rider profile updated.",
                riderService.updateOwnProfile(principal.getUserId(), request)
        );
    }

    @PostMapping(value = "/profile/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload identity or driving license verification document")
    public ApiResponse<RiderProfileResponse> uploadDocument(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @RequestParam("docType") String docType,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(
                "Verification document uploaded.",
                riderService.uploadDocument(principal.getUserId(), docType, file)
        );
    }

    @GetMapping("/profile/documents/{docType}")
    @Operation(summary = "Download authenticated rider's own KYC or license document")
    public ResponseEntity<Resource> downloadOwnDocument(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable String docType) {
        RiderProfileResponse profile = riderService.getOwnProfile(principal.getUserId());
        Resource res = riderService.loadRiderDocumentForAdminOrOwner(profile.id(), docType);
        MediaType type = MediaTypeFactory.getMediaType(res.getFilename()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(res);
    }

    @PatchMapping("/availability")
    @Operation(summary = "Toggle rider ONLINE / OFFLINE status")
    public ApiResponse<RiderProfileResponse> updateAvailability(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody UpdateRiderAvailabilityRequest request) {
        return ApiResponse.ok(
                "Rider availability updated.",
                riderService.updateAvailability(principal.getUserId(), request)
        );
    }

    @PatchMapping("/location")
    @Operation(summary = "Update rider live GPS coordinates for distance-aware dispatch and tracking")
    public ApiResponse<RiderProfileResponse> updateLocation(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody UpdateRiderLocationRequest request) {
        return ApiResponse.ok(
                "Location updated.",
                riderService.updateLocation(principal.getUserId(), request)
        );
    }

    @GetMapping("/delivery-zones")
    @Operation(summary = "List rider delivery zones")
    public ApiResponse<List<RiderDeliveryZoneResponse>> listZones(
            @AuthenticationPrincipal MedexaUserPrincipal principal) {
        return ApiResponse.ok(riderService.listZones(principal.getUserId()));
    }

    @PostMapping("/delivery-zones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a rider delivery zone / pincode")
    public ApiResponse<RiderDeliveryZoneResponse> addZone(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody RiderDeliveryZoneRequest request) {
        return ApiResponse.ok(
                "Rider delivery zone added.",
                riderService.addZone(principal.getUserId(), request)
        );
    }

    @PatchMapping("/delivery-zones/{zoneId}/status")
    @Operation(summary = "Activate or pause a rider delivery zone")
    public ApiResponse<RiderDeliveryZoneResponse> toggleZone(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long zoneId,
            @Valid @RequestBody RiderZoneStatusRequest request) {
        return ApiResponse.ok(
                "Rider delivery zone status updated.",
                riderService.toggleZoneStatus(principal.getUserId(), zoneId, request)
        );
    }

    @DeleteMapping("/delivery-zones/{zoneId}")
    @Operation(summary = "Remove a rider delivery zone")
    public ApiResponse<Void> deleteZone(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long zoneId) {
        riderService.deleteZone(principal.getUserId(), zoneId);
        return ApiResponse.ok("Delivery zone removed.", null);
    }

    @GetMapping("/earnings")
    @Operation(summary = "Get daily, monthly, and total earnings summary plus ledger entries")
    public ApiResponse<RiderEarningsSummaryResponse> earnings(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(riderService.getEarningsSummary(principal.getUserId(), pageable));
    }
}
