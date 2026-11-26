package com.medexa.prescription;

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
@Table(name = "prescriptions")
public class Prescription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacy_id")
    private Pharmacy pharmacy;

    @Column(name = "reference_code", nullable = false, unique = true, length = 64)
    private String referenceCode;

    @Column(name = "patient_name", nullable = false, length = 150)
    private String patientName;

    @Column(name = "patient_age")
    private Integer patientAge;

    @Column(name = "doctor_name", nullable = false, length = 150)
    private String doctorName;

    @Column(name = "doctor_registration_number", length = 120)
    private String doctorRegistrationNumber;

    @Column(name = "clinic_name", length = 200)
    private String clinicName;

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "stored_filename", nullable = false, length = 255)
    private String storedFilename;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private PrescriptionStatus status = PrescriptionStatus.PENDING_REVIEW;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "review_notes", length = 500)
    private String reviewNotes;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_pharmacy_id")
    private Pharmacy reviewedByPharmacy;

    @Column(name = "pharmacist_name", length = 150)
    private String pharmacistName;

    @Column(name = "pharmacist_registration_number", length = 120)
    private String pharmacistRegistrationNumber;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public boolean isExpiredAsOf(LocalDate today) {
        return validUntil != null && validUntil.isBefore(today);
    }
}
