package com.medexa.refund.dto;

import com.medexa.refund.RefundRecord;
import com.medexa.refund.RefundRecordStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class RefundDtos {

    private RefundDtos() {
    }

    public record CreateRefundRequest(
            @NotBlank(message = "Refund reason is required.")
            @Size(min = 3, max = 500)
            String reason,

            @Size(max = 100)
            String idempotencyKey
    ) {
    }

    public record AdminRefundDecisionRequest(
            @Size(max = 500)
            String notes
    ) {
    }

    public record RefundResponse(
            Long id,
            String refundReference,
            Long orderId,
            String orderNumber,
            String checkoutReference,
            Long paymentId,
            String paymentReference,
            String gatewayPaymentId,
            Long customerId,
            String customerName,
            String customerEmail,
            BigDecimal amount,
            String currency,
            String reason,
            RefundRecordStatus status,
            String gatewayRefundId,
            String failureReason,
            String adminNotes,
            Instant processedAt,
            Instant createdAt
    ) {
        public static RefundResponse from(RefundRecord r) {
            return new RefundResponse(
                    r.getId(),
                    r.getRefundReference(),
                    r.getOrder().getId(),
                    r.getOrder().getOrderNumber(),
                    r.getOrder().getCheckoutSession().getCheckoutReference(),
                    r.getPayment().getId(),
                    r.getPayment().getPaymentReference(),
                    r.getPayment().getGatewayPaymentId(),
                    r.getCustomer().getId(),
                    r.getCustomer().getFullName(),
                    r.getCustomer().getEmail(),
                    r.getAmount(),
                    r.getCurrency(),
                    r.getReason(),
                    r.getStatus(),
                    r.getGatewayRefundId(),
                    r.getFailureReason(),
                    r.getAdminNotes(),
                    r.getProcessedAt(),
                    r.getCreatedAt()
            );
        }
    }
}
