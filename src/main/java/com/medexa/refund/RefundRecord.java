package com.medexa.refund;

import com.medexa.common.entity.BaseEntity;
import com.medexa.order.Order;
import com.medexa.payment.Payment;
import com.medexa.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "refunds")
public class RefundRecord extends BaseEntity {

    @Column(name = "refund_reference", nullable = false, unique = true, length = 64)
    private String refundReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private RefundRecordStatus status = RefundRecordStatus.REQUESTED;

    @Column(name = "gateway_refund_id", unique = true, length = 120)
    private String gatewayRefundId;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "initiated_by_user_id")
    private Long initiatedByUserId;

    @Column(name = "processed_by_user_id")
    private Long processedByUserId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "admin_notes", length = 500)
    private String adminNotes;

    @Column(name = "processed_at")
    private Instant processedAt;
}
