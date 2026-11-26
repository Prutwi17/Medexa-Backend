package com.medexa.delivery.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.delivery.DeliveryStatus;
import com.medexa.delivery.dto.DeliveryDtos.CompleteDeliveryOtpRequest;
import com.medexa.delivery.dto.DeliveryDtos.ReportDeliveryFailureRequest;
import com.medexa.delivery.dto.DeliveryDtos.RiderDecisionRequest;
import com.medexa.delivery.dto.DeliveryDtos.RiderDeliveryResponse;
import com.medexa.delivery.service.DeliveryLifecycleService;
import com.medexa.rider.dto.RiderDtos.UpdateRiderLocationRequest;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/rider/deliveries")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Rider Deliveries", description = "Rider delivery assignments, pickup confirmation, OTP completion, and history")
public class RiderDeliveryController {

    private final DeliveryLifecycleService deliveryLifecycleService;

    @GetMapping("/active")
    @Operation(summary = "List active deliveries assigned to the authenticated rider")
    public ApiResponse<List<RiderDeliveryResponse>> active(@AuthenticationPrincipal MedexaUserPrincipal principal) {
        return ApiResponse.ok(deliveryLifecycleService.listActiveDeliveriesForRider(principal.getUserId()));
    }

    @GetMapping
    @Operation(summary = "List rider delivery history")
    public ApiResponse<List<RiderDeliveryResponse>> history(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @RequestParam(required = false) DeliveryStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<RiderDeliveryResponse> page = deliveryLifecycleService.listRiderDeliveryHistory(
                principal.getUserId(), status, pageable
        );
        return ApiResponse.paged("Rider deliveries fetched.", page);
    }

    @GetMapping("/{deliveryId}")
    @Operation(summary = "Get details of a delivery assigned to the authenticated rider")
    public ApiResponse<RiderDeliveryResponse> detail(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long deliveryId) {
        return ApiResponse.ok(deliveryLifecycleService.getRiderDelivery(principal.getUserId(), deliveryId));
    }

    @PostMapping("/{deliveryId}/decision")
    @Operation(summary = "Accept or reject an assigned pickup offer")
    public ApiResponse<RiderDeliveryResponse> decide(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long deliveryId,
            @Valid @RequestBody RiderDecisionRequest request) {
        return ApiResponse.ok(
                Boolean.TRUE.equals(request.accepted()) ? "Delivery accepted." : "Delivery rejected and returned to queue.",
                deliveryLifecycleService.decideAssignment(principal.getUserId(), deliveryId, request)
        );
    }

    @PostMapping("/{deliveryId}/pickup")
    @Operation(summary = "Confirm medicine parcel pickup from dispensing pharmacy")
    public ApiResponse<RiderDeliveryResponse> confirmPickup(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long deliveryId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        return ApiResponse.ok(
                "Pharmacy pickup confirmed.",
                deliveryLifecycleService.confirmPickup(principal.getUserId(), deliveryId, notes)
        );
    }

    @PostMapping("/{deliveryId}/out-for-delivery")
    @Operation(summary = "Mark parcel out for delivery and issue 6-digit customer OTP")
    public ApiResponse<RiderDeliveryResponse> outForDelivery(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long deliveryId,
            @Valid @RequestBody(required = false) UpdateRiderLocationRequest location) {
        return ApiResponse.ok(
                "Out for delivery; customer OTP generated.",
                deliveryLifecycleService.markOutForDelivery(principal.getUserId(), deliveryId, location)
        );
    }

    @PatchMapping("/{deliveryId}/location")
    @Operation(summary = "Update rider GPS position for active delivery")
    public ApiResponse<RiderDeliveryResponse> updateLocation(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long deliveryId,
            @Valid @RequestBody UpdateRiderLocationRequest location) {
        return ApiResponse.ok(
                "Delivery location updated.",
                deliveryLifecycleService.updateLiveLocation(principal.getUserId(), deliveryId, location)
        );
    }

    @PostMapping(value = "/{deliveryId}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload delivery handover proof photo")
    public ApiResponse<RiderDeliveryResponse> uploadProof(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long deliveryId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(
                "Delivery proof uploaded.",
                deliveryLifecycleService.uploadDeliveryProof(principal.getUserId(), deliveryId, file)
        );
    }

    @GetMapping("/{deliveryId}/proof")
    @Operation(summary = "View uploaded proof image for assigned delivery")
    public ResponseEntity<Resource> viewProof(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long deliveryId) {
        Resource res = deliveryLifecycleService.loadDeliveryProofResource(principal.getUserId(), "RIDER", deliveryId);
        MediaType type = MediaTypeFactory.getMediaType(res.getFilename()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(res);
    }

    @PostMapping("/{deliveryId}/complete")
    @Operation(summary = "Verify customer delivery OTP and complete delivery")
    public ApiResponse<RiderDeliveryResponse> completeWithOtp(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long deliveryId,
            @Valid @RequestBody CompleteDeliveryOtpRequest request) {
        return ApiResponse.ok(
                "Delivery completed and OTP verified.",
                deliveryLifecycleService.completeDeliveryWithOtp(principal.getUserId(), deliveryId, request)
        );
    }

    @PostMapping("/{deliveryId}/fail")
    @Operation(summary = "Report delivery exception/failure")
    public ApiResponse<RiderDeliveryResponse> failDelivery(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long deliveryId,
            @Valid @RequestBody ReportDeliveryFailureRequest request) {
        return ApiResponse.ok(
                "Delivery exception recorded.",
                deliveryLifecycleService.reportDeliveryFailure(principal.getUserId(), deliveryId, request)
        );
    }
}
