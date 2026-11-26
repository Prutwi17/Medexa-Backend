package com.medexa.checkout.controller;

import com.medexa.checkout.dto.CheckoutDtos.CancelCheckoutRequest;
import com.medexa.checkout.dto.CheckoutDtos.CheckoutPreviewRequest;
import com.medexa.checkout.dto.CheckoutDtos.CheckoutPreviewResponse;
import com.medexa.checkout.dto.CheckoutDtos.CheckoutSessionResponse;
import com.medexa.checkout.dto.CheckoutDtos.ConfirmPaymentRequest;
import com.medexa.checkout.dto.CheckoutDtos.CreateCheckoutSessionRequest;
import com.medexa.checkout.service.CheckoutService;
import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/checkout")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Customer Checkout", description = "Multi-pharmacy checkout preview, stock reservation, and payment confirmation")
public class CustomerCheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping("/preview")
    @Operation(summary = "Preview server-computed multi-pharmacy checkout breakdown with coupon, delivery fee, and tax")
    public ApiResponse<CheckoutPreviewResponse> preview(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody(required = false) CheckoutPreviewRequest request) {
        return ApiResponse.ok(checkoutService.previewCheckout(principal.getUserId(), request));
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create parent checkout session, child pharmacy orders, and FEFO stock reservations")
    public ApiResponse<CheckoutSessionResponse> createSession(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody CreateCheckoutSessionRequest request) {
        return ApiResponse.ok(
                "Checkout session prepared and stock reserved.",
                checkoutService.createCheckoutSession(principal.getUserId(), request)
        );
    }

    @GetMapping("/sessions/{checkoutReference}")
    @Operation(summary = "Get parent checkout session and linked child orders")
    public ApiResponse<CheckoutSessionResponse> getSession(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable String checkoutReference) {
        return ApiResponse.ok(checkoutService.getCheckoutSession(principal.getUserId(), checkoutReference));
    }

    @PostMapping("/sessions/{checkoutReference}/confirm-payment")
    @Operation(summary = "Confirm verified Razorpay payment signature and commit reserved stock idempotently")
    public ApiResponse<CheckoutSessionResponse> confirmPayment(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable String checkoutReference,
            @Valid @RequestBody ConfirmPaymentRequest request) {
        return ApiResponse.ok(
                "Payment verified and pharmacy orders placed.",
                checkoutService.confirmPayment(principal.getUserId(), checkoutReference, request)
        );
    }

    @PostMapping("/sessions/{checkoutReference}/cancel")
    @Operation(summary = "Cancel awaiting-payment checkout session and release reserved inventory")
    public ApiResponse<CheckoutSessionResponse> cancelSession(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable String checkoutReference,
            @Valid @RequestBody(required = false) CancelCheckoutRequest request) {
        return ApiResponse.ok(
                "Checkout cancelled and reserved stock released.",
                checkoutService.cancelCheckout(
                        principal.getUserId(),
                        checkoutReference,
                        request != null ? request.reason() : null
                )
        );
    }
}
