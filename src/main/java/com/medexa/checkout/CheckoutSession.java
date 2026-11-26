package com.medexa.checkout;

import com.medexa.address.CustomerAddress;
import com.medexa.common.entity.BaseEntity;
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
@Table(name = "checkout_sessions")
public class CheckoutSession extends BaseEntity {

    @Column(name = "checkout_reference", nullable = false, unique = true, length = 64)
    private String checkoutReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private CustomerAddress address;

    @Column(name = "delivery_recipient_name", nullable = false, length = 150)
    private String deliveryRecipientName;

    @Column(name = "delivery_phone", nullable = false, length = 30)
    private String deliveryPhone;

    @Column(name = "delivery_address_line1", nullable = false, length = 250)
    private String deliveryAddressLine1;

    @Column(name = "delivery_address_line2", length = 250)
    private String deliveryAddressLine2;

    @Column(name = "delivery_city", nullable = false, length = 100)
    private String deliveryCity;

    @Column(name = "delivery_state", nullable = false, length = 100)
    private String deliveryState;

    @Column(name = "delivery_postal_code", nullable = false, length = 20)
    private String deliveryPostalCode;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private CheckoutStatus status = CheckoutStatus.AWAITING_PAYMENT;

    @Column(name = "items_subtotal", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal itemsSubtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "delivery_fee_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal deliveryFeeTotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    @Column(name = "payment_reference", length = 120)
    private String paymentReference;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
