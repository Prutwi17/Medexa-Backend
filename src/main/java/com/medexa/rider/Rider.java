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

    @Column(name = "driving_license_number", nullable = false, unique = true, length = 100)
    private String drivingLicenseNumber;

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

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private Long verifiedBy;
}
