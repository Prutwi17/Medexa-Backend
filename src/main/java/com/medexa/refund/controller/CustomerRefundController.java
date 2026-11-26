package com.medexa.refund.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.refund.RefundRecordStatus;
import com.medexa.refund.dto.RefundDtos.CreateRefundRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/refunds")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Customer Refunds", description = "Customer refund requests and status tracking")
public class CustomerRefundController {

    private final RefundService refundService;

    @GetMapping
    @Operation(summary = "List authenticated customer's refund records")
    public ApiResponse<List<RefundResponse>> list(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @RequestParam(required = false) RefundRecordStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<RefundResponse> page = refundService.listCustomerRefunds(principal.getUserId(), status, pageable);
        return ApiResponse.paged("Refund records fetched.", page);
    }

    @PostMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit an idempotent refund request for an eligible order")
    public ApiResponse<RefundResponse> create(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long orderId,
            @Valid @RequestBody CreateRefundRequest request) {
        return ApiResponse.ok(
                "Refund request submitted.",
                refundService.createRefundRequest(principal.getUserId(), orderId, request)
        );
    }
}
