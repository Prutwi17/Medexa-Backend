package com.medexa.payment.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.payment.PaymentStatus;
import com.medexa.payment.dto.PaymentDtos.PaymentResponse;
import com.medexa.payment.dto.PaymentDtos.WebhookEventLogResponse;
import com.medexa.payment.service.PaymentLedgerService;
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
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Admin Payments", description = "Platform payment ledger and webhook monitoring")
public class AdminPaymentController {

    private final PaymentLedgerService paymentLedgerService;

    @GetMapping
    @Operation(summary = "List internal payment records across the marketplace")
    public ApiResponse<List<PaymentResponse>> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PaymentResponse> page = paymentLedgerService.listAdminPayments(status, q, pageable);
        return ApiResponse.paged("Payments fetched.", page);
    }

    @GetMapping("/webhooks")
    @Operation(summary = "List processed and deduplicated Razorpay webhook events")
    public ApiResponse<List<WebhookEventLogResponse>> webhooks(
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<WebhookEventLogResponse> page = paymentLedgerService.listWebhookLogs(pageable);
        return ApiResponse.paged("Webhook logs fetched.", page);
    }

    @GetMapping("/{paymentReference}")
    @Operation(summary = "Get payment record with full event history")
    public ApiResponse<PaymentResponse> detail(@PathVariable String paymentReference) {
        return ApiResponse.ok(paymentLedgerService.getAdminPayment(paymentReference));
    }
}
