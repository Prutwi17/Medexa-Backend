package com.medexa.pharmacy.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.pharmacy.dto.PharmacyDtos.DeliveryZoneRequest;
import com.medexa.pharmacy.dto.PharmacyDtos.DeliveryZoneResponse;
import com.medexa.pharmacy.dto.PharmacyDtos.DeliveryZoneStatusRequest;
import com.medexa.pharmacy.dto.PharmacyDtos.PharmacyLicenseResponse;
import com.medexa.pharmacy.dto.PharmacyDtos.PharmacyProfileResponse;
import com.medexa.pharmacy.dto.PharmacyDtos.SubmitPharmacyLicenseRequest;
import com.medexa.pharmacy.dto.PharmacyDtos.UpdatePharmacyProfileRequest;
import com.medexa.pharmacy.service.PharmacyService;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1/pharmacy")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Pharmacy Management", description = "Pharmacy profile, license submissions, and delivery zones")
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @GetMapping("/profile")
    @Operation(summary = "Get authenticated pharmacy profile, licenses, and delivery zones")
    public ApiResponse<PharmacyProfileResponse> getProfile(@AuthenticationPrincipal MedexaUserPrincipal principal) {
        return ApiResponse.ok(pharmacyService.getOwnProfile(principal.getUserId()));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update authenticated pharmacy profile")
    public ApiResponse<PharmacyProfileResponse> updateProfile(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody UpdatePharmacyProfileRequest request) {
        return ApiResponse.ok(
                "Pharmacy profile updated.",
                pharmacyService.updateOwnProfile(principal.getUserId(), request)
        );
    }

    @PostMapping("/licenses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a new drug license for verification")
    public ApiResponse<PharmacyLicenseResponse> submitLicense(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody SubmitPharmacyLicenseRequest request) {
        return ApiResponse.ok(
                "License submitted for administrator verification.",
                pharmacyService.submitLicense(principal.getUserId(), request)
        );
    }

    @PostMapping(value = "/licenses/{licenseId}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload drug license verification document")
    public ApiResponse<PharmacyLicenseResponse> uploadLicenseDocument(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long licenseId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(
                "License document uploaded.",
                pharmacyService.uploadLicenseDocument(principal.getUserId(), licenseId, file)
        );
    }

    @GetMapping("/delivery-zones")
    @Operation(summary = "List pharmacy delivery zones")
    public ApiResponse<List<DeliveryZoneResponse>> listZones(@AuthenticationPrincipal MedexaUserPrincipal principal) {
        return ApiResponse.ok(pharmacyService.listDeliveryZones(principal.getUserId()));
    }

    @PostMapping("/delivery-zones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new delivery zone")
    public ApiResponse<DeliveryZoneResponse> createZone(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody DeliveryZoneRequest request) {
        return ApiResponse.ok(
                "Delivery zone added.",
                pharmacyService.createDeliveryZone(principal.getUserId(), request)
        );
    }

    @PutMapping("/delivery-zones/{zoneId}")
    @Operation(summary = "Update an existing delivery zone")
    public ApiResponse<DeliveryZoneResponse> updateZone(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long zoneId,
            @Valid @RequestBody DeliveryZoneRequest request) {
        return ApiResponse.ok(
                "Delivery zone updated.",
                pharmacyService.updateDeliveryZone(principal.getUserId(), zoneId, request)
        );
    }

    @PatchMapping("/delivery-zones/{zoneId}/status")
    @Operation(summary = "Activate or deactivate a delivery zone")
    public ApiResponse<DeliveryZoneResponse> updateZoneStatus(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long zoneId,
            @Valid @RequestBody DeliveryZoneStatusRequest request) {
        return ApiResponse.ok(
                "Delivery zone status updated.",
                pharmacyService.toggleDeliveryZoneStatus(principal.getUserId(), zoneId, request.active())
        );
    }

    @DeleteMapping("/delivery-zones/{zoneId}")
    @Operation(summary = "Remove a delivery zone")
    public ApiResponse<Void> deleteZone(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long zoneId) {
        pharmacyService.deleteDeliveryZone(principal.getUserId(), zoneId);
        return ApiResponse.ok("Delivery zone deleted.", null);
    }
}
