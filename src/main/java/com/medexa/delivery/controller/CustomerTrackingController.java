package com.medexa.delivery.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.delivery.dto.DeliveryDtos.CustomerTrackingResponse;
import com.medexa.delivery.service.DeliveryLifecycleService;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Customer Delivery Tracking", description = "Live order preparation, rider status, and delivery OTP instructions")
public class CustomerTrackingController {

    private final DeliveryLifecycleService deliveryLifecycleService;

    @GetMapping("/{orderId}/tracking")
    @Operation(summary = "Get combined order and delivery tracking status plus customer OTP")
    public ApiResponse<CustomerTrackingResponse> getTracking(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long orderId) {
        return ApiResponse.ok(deliveryLifecycleService.getCustomerTracking(principal.getUserId(), orderId));
    }

    @PostMapping("/{orderId}/tracking/regenerate-otp")
    @Operation(summary = "Generate a fresh 6-digit delivery OTP while order is OUT_FOR_DELIVERY")
    public ApiResponse<CustomerTrackingResponse> regenerateOtp(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long orderId) {
        return ApiResponse.ok(
                "Fresh delivery OTP generated.",
                deliveryLifecycleService.regenerateCustomerOtp(principal.getUserId(), orderId)
        );
    }

    @GetMapping("/{orderId}/tracking/proof")
    @Operation(summary = "View uploaded delivery proof image for owned order")
    public ResponseEntity<Resource> viewProof(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long orderId) {
        CustomerTrackingResponse tracking = deliveryLifecycleService.getCustomerTracking(principal.getUserId(), orderId);
        if (tracking.deliveryId() == null) {
            return ResponseEntity.notFound().build();
        }
        Resource res = deliveryLifecycleService.loadDeliveryProofResource(
                principal.getUserId(), "CUSTOMER", tracking.deliveryId()
        );
        MediaType type = MediaTypeFactory.getMediaType(res.getFilename()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(res);
    }
}
