package com.medexa.rider;

import com.medexa.common.entity.BaseEntity;
import com.medexa.pharmacy.VerificationStatus;
import com.medexa.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "riders")
public class Rider extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "phone_number", nullable = false, length = 30)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 40)
    private VehicleType vehicleType;

    @Column(name = "vehicle_registration_number", nullable = false, length = 60)
    private String vehicleRegistrationNumber;

    @Column(name = "vehicle_model", length = 120)
    private String vehicleModel;

    @Column(name = "driving_license_number", nullable = false, unique = true, length = 100)
    private String drivingLicenseNumber;

    @Column(name = "identity_document_type", length = 50)
    private String identityDocumentType;

    @Column(name = "identity_document_number", length = 100)
    private String identityDocumentNumber;

    @Column(name = "identity_document_url", length = 500)
    private String identityDocumentUrl;

    @Column(name = "driving_license_expiry")
    private LocalDate drivingLicenseExpiry;

    @Column(name = "driving_license_url", length = 500)
    private String drivingLicenseUrl;

    @Column(name = "service_city", nullable = false, length = 100)
    private String serviceCity;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 40)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING_VERIFICATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 30)
    @Builder.Default
    private RiderAvailabilityStatus availabilityStatus = RiderAvailabilityStatus.OFFLINE;

    @Column(name = "current_latitude", precision = 9, scale = 6)
    private BigDecimal currentLatitude;

    @Column(name = "current_longitude", precision = 9, scale = 6)
    private BigDecimal currentLongitude;

    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;

    @Column(name = "max_concurrent_deliveries", nullable = false)
    @Builder.Default
    private int maxConcurrentDeliveries = 2;

    @Column(name = "base_fee_per_delivery", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal baseFeePerDelivery = new BigDecimal("40.00");

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private Long verifiedBy;
}
