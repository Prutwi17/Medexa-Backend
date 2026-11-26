package com.medexa.order.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.order.OrderStatus;
import com.medexa.order.dto.OrderDtos.CancelOrderRequest;
import com.medexa.order.dto.OrderDtos.OrderResponse;
import com.medexa.order.dto.OrderDtos.RefundRequestPayload;
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
@RequestMapping("/api/v1/customer/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Customer Orders", description = "Customer child order history, tracking timeline, cancellation, and refund requests")
public class CustomerOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "List authenticated customer's pharmacy orders")
    public ApiResponse<List<OrderResponse>> list(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<OrderResponse> page = orderService.listCustomerOrders(principal.getUserId(), status, q, pageable);
        return ApiResponse.paged("Customer orders fetched.", page);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get child order detail, items, and status timeline")
    public ApiResponse<OrderResponse> getDetail(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long orderId) {
        return ApiResponse.ok(orderService.getCustomerOrder(principal.getUserId(), orderId));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an eligible order and restore inventory")
    public ApiResponse<OrderResponse> cancel(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long orderId,
            @Valid @RequestBody CancelOrderRequest request) {
        return ApiResponse.ok(
                "Order cancelled.",
                orderService.cancelCustomerOrder(principal.getUserId(), orderId, request)
        );
    }

    @PostMapping("/{orderId}/refund-request")
    @Operation(summary = "Submit a refund request for an eligible cancelled or rejected paid order")
    public ApiResponse<OrderResponse> requestRefund(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long orderId,
            @Valid @RequestBody RefundRequestPayload request) {
        return ApiResponse.ok(
                "Refund request submitted.",
                orderService.requestRefund(principal.getUserId(), orderId, request)
        );
    }
}
