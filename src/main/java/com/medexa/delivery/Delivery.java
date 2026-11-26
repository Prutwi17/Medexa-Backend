package com.medexa.delivery;

import com.medexa.common.entity.BaseEntity;
import com.medexa.order.Order;
import com.medexa.pharmacy.Pharmacy;
import com.medexa.rider.Rider;
import com.medexa.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
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
@Table(name = "deliveries")
public class Delivery extends BaseEntity {

    @Column(name = "delivery_reference", nullable = false, unique = true, length = 64)
    private String deliveryReference;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_rider_id")
    private Rider assignedRider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.WAITING_FOR_RIDER;

    @Column(name = "pickup_postal_code", nullable = false, length = 20)
    private String pickupPostalCode;

    @Column(name = "drop_postal_code", nullable = false, length = 20)
    private String dropPostalCode;

    @Column(name = "distance_km", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal distanceKm = BigDecimal.ZERO;

    @Column(name = "assignment_attempts", nullable = false)
    @Builder.Default
    private int assignmentAttempts = 0;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "assignment_expires_at")
    private Instant assignmentExpiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "out_for_delivery_at")
    private Instant outForDeliveryAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "otp_hash", length = 128)
    private String otpHash;

    @Column(name = "customer_otp_code", length = 10)
    private String customerOtpCode;

    @Column(name = "otp_expires_at")
    private Instant otpExpiresAt;

    @Column(name = "otp_attempts", nullable = false)
    @Builder.Default
    private int otpAttempts = 0;

    @Column(name = "max_otp_attempts", nullable = false)
    @Builder.Default
    private int maxOtpAttempts = 3;

    @Column(name = "otp_verified_at")
    private Instant otpVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "proof_type", length = 40)
    private ProofType proofType;

    @Column(name = "delivery_proof_url", length = 500)
    private String deliveryProofUrl;

    @Column(name = "recipient_confirmed_name", length = 150)
    private String recipientConfirmedName;

    @Column(name = "delivery_notes", length = 500)
    private String deliveryNotes;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "rider_lat", precision = 9, scale = 6)
    private BigDecimal riderLat;

    @Column(name = "rider_lng", precision = 9, scale = 6)
    private BigDecimal riderLng;

    @Column(name = "rider_location_updated_at")
    private Instant riderLocationUpdatedAt;

    @Column(name = "rider_earning_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal riderEarningAmount = BigDecimal.ZERO;
}
