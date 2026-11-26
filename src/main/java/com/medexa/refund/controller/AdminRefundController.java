package com.medexa.refund.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.refund.RefundRecordStatus;
import com.medexa.refund.dto.RefundDtos.AdminRefundDecisionRequest;
import com.medexa.refund.dto.RefundDtos.RefundResponse;
import com.medexa.refund.service.RefundService;
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
@RequestMapping("/api/v1/admin/refunds")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Admin Refunds", description = "Refund review and Razorpay refund execution")
public class AdminRefundController {

    private final RefundService refundService;

    @GetMapping
    @Operation(summary = "List refund requests across the platform")
    public ApiResponse<List<RefundResponse>> list(
            @RequestParam(required = false) RefundRecordStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<RefundResponse> page = refundService.listAdminRefunds(status, q, pageable);
        return ApiResponse.paged("Refund requests fetched.", page);
    }

    @PostMapping("/{refundId}/process")
    @Operation(summary = "Execute Razorpay refund idempotently and synchronize order/payment states")
    public ApiResponse<RefundResponse> process(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long refundId,
            @Valid @RequestBody(required = false) AdminRefundDecisionRequest request) {
        return ApiResponse.ok(
                "Refund processed.",
                refundService.processRefund(principal.getUserId(), refundId, request)
        );
    }

    @PostMapping("/{refundId}/reject")
    @Operation(summary = "Reject an ineligible or invalid refund request")
    public ApiResponse<RefundResponse> reject(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long refundId,
            @Valid @RequestBody AdminRefundDecisionRequest request) {
        return ApiResponse.ok(
                "Refund request rejected.",
                refundService.rejectRefund(principal.getUserId(), refundId, request)
        );
    }
}
