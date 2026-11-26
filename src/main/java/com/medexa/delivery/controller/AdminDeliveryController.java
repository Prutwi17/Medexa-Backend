package com.medexa.delivery.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.delivery.DeliveryStatus;
import com.medexa.delivery.dto.DeliveryDtos.AdminAssignRiderRequest;
import com.medexa.delivery.dto.DeliveryDtos.AdminDeliveryResponse;
import com.medexa.delivery.service.DeliveryAssignmentService;
import com.medexa.delivery.service.DeliveryLifecycleService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/deliveries")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Admin Deliveries", description = "Assignment monitoring, timeout sweeps, exception recovery, and proof inspection")
public class AdminDeliveryController {

    private final DeliveryLifecycleService deliveryLifecycleService;
    private final DeliveryAssignmentService deliveryAssignmentService;

    @GetMapping
    @Operation(summary = "List deliveries across active, exception, and completed queues")
    public ApiResponse<List<AdminDeliveryResponse>> list(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) Long riderId,
            @RequestParam(required = false, defaultValue = "false") boolean exceptionsOnly,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AdminDeliveryResponse> page = deliveryLifecycleService.listAdminDeliveries(
                status, riderId, exceptionsOnly, q, pageable
        );
        return ApiResponse.paged("Deliveries fetched.", page);
    }

    @GetMapping("/{deliveryId}")
    @Operation(summary = "Get full delivery details, assignment attempts, and timeline")
    public ApiResponse<AdminDeliveryResponse> detail(@PathVariable Long deliveryId) {
        return ApiResponse.ok(deliveryLifecycleService.getAdminDelivery(deliveryId));
    }

    @PostMapping("/{deliveryId}/assign")
    @Operation(summary = "Manually assign or override rider for a delivery")
    public ApiResponse<AdminDeliveryResponse> assignSpecific(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long deliveryId,
            @Valid @RequestBody AdminAssignRiderRequest request) {
        deliveryAssignmentService.assignSpecificRiderByAdmin(
                principal.getUserId(), deliveryId, request.riderId(), request.notes()
        );
        return ApiResponse.ok("Rider assigned.", deliveryLifecycleService.getAdminDelivery(deliveryId));
    }

    @PostMapping("/{deliveryId}/retry-auto")
    @Operation(summary = "Re-run automatic ranking and rider assignment for an unassigned/exception delivery")
    public ApiResponse<AdminDeliveryResponse> retryAuto(@PathVariable Long deliveryId) {
        AdminDeliveryResponse existing = deliveryLifecycleService.getAdminDelivery(deliveryId);
        deliveryAssignmentService.initiateOrRetryAssignment(existing.orderId());
        return ApiResponse.ok(
                "Automatic assignment cycle triggered.",
                deliveryLifecycleService.getAdminDelivery(deliveryId)
        );
    }

    @PostMapping("/sweep-timeouts")
    @Operation(summary = "Manually run timeout sweep to expire stale ASSIGNED offers and reassign")
    public ApiResponse<java.util.Map<String, Integer>> sweepTimeouts() {
        int processed = deliveryAssignmentService.expireTimedOutAssignments();
        return ApiResponse.ok("Timeout sweep completed.", java.util.Map.of("reassignedCount", processed));
    }

    @GetMapping("/{deliveryId}/proof")
    @Operation(summary = "Inspect uploaded delivery proof photo")
    public ResponseEntity<Resource> viewProof(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long deliveryId) {
        Resource res = deliveryLifecycleService.loadDeliveryProofResource(principal.getUserId(), "ADMIN", deliveryId);
        MediaType type = MediaTypeFactory.getMediaType(res.getFilename()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(res);
    }
}
