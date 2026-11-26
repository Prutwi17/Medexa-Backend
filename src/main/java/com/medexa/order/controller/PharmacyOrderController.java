package com.medexa.order.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.order.OrderStatus;
import com.medexa.order.dto.OrderDtos.OrderResponse;
import com.medexa.order.dto.OrderDtos.PharmacyOrderDecisionRequest;
import com.medexa.order.dto.OrderDtos.PharmacyOrderStatusUpdateRequest;
import com.medexa.order.service.OrderService;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacy/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Pharmacy Orders", description = "Verified pharmacy fulfillment queue and order transitions")
public class PharmacyOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "List child orders routed to the authenticated pharmacy")
    public ApiResponse<List<OrderResponse>> list(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<OrderResponse> page = orderService.listPharmacyOrders(principal.getUserId(), status, q, pageable);
        return ApiResponse.paged("Pharmacy orders fetched.", page);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "View pharmacy child order details, payout calculation, and timeline")
    public ApiResponse<OrderResponse> getDetail(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long orderId) {
        return ApiResponse.ok(orderService.getPharmacyOrder(principal.getUserId(), orderId));
    }

    @PostMapping("/{orderId}/decision")
    @Operation(summary = "Accept or reject an incoming PLACED order")
    public ApiResponse<OrderResponse> decide(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long orderId,
            @Valid @RequestBody PharmacyOrderDecisionRequest request) {
        return ApiResponse.ok(
                "Pharmacy order decision recorded.",
                orderService.decideOrder(principal.getUserId(), orderId, request)
        );
    }

    @PostMapping("/{orderId}/status")
    @Operation(summary = "Move order to PREPARING or READY_FOR_PICKUP")
    public ApiResponse<OrderResponse> updateProgress(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long orderId,
            @Valid @RequestBody PharmacyOrderStatusUpdateRequest request) {
        return ApiResponse.ok(
                "Order fulfillment status updated.",
                orderService.updatePharmacyProgress(principal.getUserId(), orderId, request)
        );
    }
}
