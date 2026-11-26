package com.medexa.payment.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.payment.PaymentStatus;
import com.medexa.payment.dto.PaymentDtos.PaymentResponse;
import com.medexa.payment.dto.PaymentDtos.PaymentVerifyRequest;
import com.medexa.payment.service.PaymentLedgerService;
import com.medexa.checkout.service.CheckoutService;
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
@RequestMapping("/api/v1/customer/payments")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Customer Payments", description = "Customer payment history and server-side Razorpay verification")
public class CustomerPaymentController {

    private final PaymentLedgerService paymentLedgerService;
    private final CheckoutService checkoutService;

    @GetMapping
    @Operation(summary = "List authenticated customer's payment records")
    public ApiResponse<List<PaymentResponse>> list(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PaymentResponse> page = paymentLedgerService.listCustomerPayments(principal.getUserId(), status, pageable);
        return ApiResponse.paged("Payment history fetched.", page);
    }

    @GetMapping("/{paymentReference}")
    @Operation(summary = "Get internal payment details and event history")
    public ApiResponse<PaymentResponse> getDetail(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable String paymentReference) {
        return ApiResponse.ok(paymentLedgerService.getCustomerPayment(principal.getUserId(), paymentReference));
    }

    @PostMapping("/{paymentReference}/verify")
    @Operation(summary = "Verify payment status server-side against Razorpay API and synchronize checkout/orders")
    public ApiResponse<PaymentResponse> verify(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable String paymentReference,
            @Valid @RequestBody(required = false) PaymentVerifyRequest request) {
        return ApiResponse.ok(
                "Payment status synchronized with gateway.",
                checkoutService.verifyPaymentFromServer(principal.getUserId(), paymentReference, request)
        );
    }
}
