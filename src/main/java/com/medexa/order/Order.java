package com.medexa.order;

import com.medexa.address.CustomerAddress;
import com.medexa.checkout.CheckoutSession;
import com.medexa.common.entity.BaseEntity;
import com.medexa.pharmacy.Pharmacy;
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
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "order_number", nullable = false, unique = true, length = 64)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checkout_session_id", nullable = false)
    private CheckoutSession checkoutSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private CustomerAddress address;

    @Column(name = "shipping_recipient_name", nullable = false, length = 150)
    private String shippingRecipientName;

    @Column(name = "shipping_phone", nullable = false, length = 30)
    private String shippingPhone;

    @Column(name = "shipping_address", nullable = false, length = 500)
    private String shippingAddress;

    @Column(name = "shipping_city", nullable = false, length = 100)
    private String shippingCity;

    @Column(name = "shipping_state", nullable = false, length = 100)
    private String shippingState;

    @Column(name = "shipping_postal_code", nullable = false, length = 20)
    private String shippingPostalCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private OrderStatus status = OrderStatus.PAYMENT_PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    @Builder.Default
    private OrderPaymentStatus paymentStatus = OrderPaymentStatus.PENDING;

    @Column(name = "items_subtotal", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal itemsSubtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "platform_commission_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal platformCommissionAmount = BigDecimal.ZERO;

    @Column(name = "pharmacy_payout_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal pharmacyPayoutAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_assignment_status", nullable = false, length = 30)
    @Builder.Default
    private DeliveryAssignmentStatus deliveryAssignmentStatus = DeliveryAssignmentStatus.UNASSIGNED;

    @Column(name = "delivery_eta_minutes")
    private Integer deliveryEtaMinutes;

    @Column(name = "requires_prescription", nullable = false)
    @Builder.Default
    private boolean requiresPrescription = false;

    @Column(name = "all_prescriptions_verified", nullable = false)
    @Builder.Default
    private boolean allPrescriptionsVerified = true;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_by_role", length = 30)
    private String cancelledByRole;

    @Column(name = "refund_eligible", nullable = false)
    @Builder.Default
    private boolean refundEligible = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 30)
    @Builder.Default
    private RefundStatus refundStatus = RefundStatus.NONE;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "placed_at")
    private Instant placedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "prepared_at")
    private Instant preparedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}
