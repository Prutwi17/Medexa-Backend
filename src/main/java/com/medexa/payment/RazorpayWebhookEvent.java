package com.medexa.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "razorpay_webhook_events")
public class RazorpayWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 150)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "razorpay_order_id", length = 120)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 120)
    private String razorpayPaymentId;

    @Column(name = "razorpay_refund_id", length = 120)
    private String razorpayRefundId;

    @Column(name = "signature_verified", nullable = false)
    @Builder.Default
    private boolean signatureVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 40)
    private WebhookProcessingStatus processingStatus;

    @Column(name = "processing_notes", length = 500)
    private String processingNotes;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
