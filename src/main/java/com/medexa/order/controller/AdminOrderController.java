package com.medexa.order.controller;

import com.medexa.checkout.dto.CheckoutDtos.CheckoutSessionResponse;
import com.medexa.checkout.service.CheckoutService;
import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.order.OrderPaymentStatus;
import com.medexa.order.OrderStatus;
import com.medexa.order.dto.OrderDtos.OrderResponse;
import com.medexa.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Admin Orders", description = "Marketplace-wide order monitoring and parent checkout oversight")
public class AdminOrderController {

    private final OrderService orderService;
    private final CheckoutService checkoutService;

    @GetMapping
    @Operation(summary = "List all child orders across pharmacies with filters")
    public ApiResponse<List<OrderResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) OrderPaymentStatus paymentStatus,
            @RequestParam(required = false) Long pharmacyId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<OrderResponse> page = orderService.listAdminOrders(status, paymentStatus, pharmacyId, q, pageable);
        return ApiResponse.paged("Marketplace orders fetched.", page);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Inspect individual child order details, payout breakdown, and timeline")
    public ApiResponse<OrderResponse> getDetail(@PathVariable Long orderId) {
        return ApiResponse.ok(orderService.getAdminOrder(orderId));
    }

    @GetMapping("/checkouts/{checkoutReference}")
    @Operation(summary = "Inspect parent checkout session and all child orders")
    public ApiResponse<CheckoutSessionResponse> getParentCheckout(@PathVariable String checkoutReference) {
        return ApiResponse.ok(checkoutService.getAdminCheckoutSession(checkoutReference));
    }
}
