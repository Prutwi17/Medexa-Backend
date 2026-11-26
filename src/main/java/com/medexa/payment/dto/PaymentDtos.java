package com.medexa.payment.dto;

import com.medexa.payment.Payment;
import com.medexa.payment.PaymentEvent;
import com.medexa.payment.PaymentStatus;
import com.medexa.payment.RazorpayWebhookEvent;
import com.medexa.payment.WebhookProcessingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PaymentFailureRequest(
            @Size(max = 120) String razorpayPaymentId,
            @Size(max = 100) String errorCode,
            @NotBlank(message = "Failure description is required.") @Size(max = 500) String errorDescription,
            Boolean releaseStock
    ) {
    }

    public record PaymentVerifyRequest(
            @Size(max = 120) String razorpayPaymentId
    ) {
    }

    public record PaymentEventResponse(
            Long id,
            String eventSource,
            String eventType,
            PaymentStatus previousStatus,
            PaymentStatus newStatus,
            String gatewayReference,
            String notes,
            Instant createdAt
    ) {
        public static PaymentEventResponse from(PaymentEvent e) {
            return new PaymentEventResponse(
                    e.getId(),
                    e.getEventSource(),
                    e.getEventType(),
                    e.getPreviousStatus(),
                    e.getNewStatus(),
                    e.getGatewayReference(),
                    e.getNotes(),
                    e.getCreatedAt()
            );
        }
    }

    public record PaymentResponse(
            Long id,
            String paymentReference,
            String checkoutReference,
            Long customerId,
            String customerEmail,
            String gatewayProvider,
            String gatewayOrderId,
            String gatewayPaymentId,
            BigDecimal amount,
            BigDecimal refundedAmount,
            String currency,
            PaymentStatus status,
            String verificationMethod,
            String lastGatewayStatus,
            String failureCode,
            String failureReason,
            Instant authorizedAt,
            Instant capturedAt,
            Instant failedAt,
            Instant createdAt,
            List<PaymentEventResponse> events
    ) {
        public static PaymentResponse from(Payment p, List<PaymentEvent> events) {
            return new PaymentResponse(
                    p.getId(),
                    p.getPaymentReference(),
                    p.getCheckoutSession().getCheckoutReference(),
                    p.getCustomer().getId(),
                    p.getCustomer().getEmail(),
                    p.getGatewayProvider(),
                    p.getGatewayOrderId(),
                    p.getGatewayPaymentId(),
                    p.getAmount(),
                    p.getRefundedAmount(),
                    p.getCurrency(),
                    p.getStatus(),
                    p.getVerificationMethod(),
                    p.getLastGatewayStatus(),
                    p.getFailureCode(),
                    p.getFailureReason(),
                    p.getAuthorizedAt(),
                    p.getCapturedAt(),
                    p.getFailedAt(),
                    p.getCreatedAt(),
                    events.stream().map(PaymentEventResponse::from).toList()
            );
        }
    }

    public record WebhookEventLogResponse(
            Long id,
            String eventId,
            String eventType,
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpayRefundId,
            boolean signatureVerified,
            WebhookProcessingStatus processingStatus,
            String processingNotes,
            Instant receivedAt,
            Instant processedAt
    ) {
        public static WebhookEventLogResponse from(RazorpayWebhookEvent w) {
            return new WebhookEventLogResponse(
                    w.getId(),
                    w.getEventId(),
                    w.getEventType(),
                    w.getRazorpayOrderId(),
                    w.getRazorpayPaymentId(),
                    w.getRazorpayRefundId(),
                    w.isSignatureVerified(),
                    w.getProcessingStatus(),
                    w.getProcessingNotes(),
                    w.getReceivedAt(),
                    w.getProcessedAt()
            );
        }
    }
}
