package com.medexa.prescription.dto;

import com.medexa.prescription.Prescription;
import com.medexa.prescription.PrescriptionAuditLog;
import com.medexa.prescription.PrescriptionOrderLink;
import com.medexa.prescription.PrescriptionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PrescriptionDtos {

    private PrescriptionDtos() {
    }

    public record PrescriptionUploadMetadata(
            Long pharmacyId,

            @NotBlank(message = "Patient name is required.")
            @Size(min = 2, max = 150)
            String patientName,

            @Min(0)
            @Max(130)
            Integer patientAge,

            @NotBlank(message = "Prescribing doctor name is required.")
            @Size(min = 2, max = 150)
            String doctorName,

            @Size(max = 120)
            String doctorRegistrationNumber,

            @Size(max = 200)
            String clinicName,

            LocalDate issuedDate,

            LocalDate validUntil,

            @Size(max = 1000)
            String notes
    ) {
    }

    public record PrescriptionReviewRequest(
            @NotNull(message = "Approval decision is required.")
            Boolean approved,

            @Size(max = 500)
            String rejectionReason,

            @Size(max = 500)
            String reviewNotes,

            LocalDate validUntilOverride
    ) {
    }

    public record DispatchEligibilityCheckRequest(
            @NotNull(message = "Medicine ID is required.")
            Long medicineId,

            Long prescriptionId
    ) {
    }

    public record DispatchEligibilityResponse(
            boolean eligible,
            boolean prescriptionRequired,
            String classificationCode,
            boolean autoSubstitutionAllowed,
            Long prescriptionId,
            String prescriptionReference,
            PrescriptionStatus prescriptionStatus,
            LocalDate prescriptionValidUntil,
            String reason
    ) {
    }

    public record PrescriptionAuditResponse(
            Long id,
            String actorRole,
            String actorName,
            String action,
            PrescriptionStatus previousStatus,
            PrescriptionStatus newStatus,
            String notes,
            Instant createdAt
    ) {
        public static PrescriptionAuditResponse from(PrescriptionAuditLog log) {
            return new PrescriptionAuditResponse(
                    log.getId(),
                    log.getActorRole(),
                    log.getActorName(),
                    log.getAction(),
                    log.getPreviousStatus(),
                    log.getNewStatus(),
                    log.getNotes(),
                    log.getCreatedAt()
            );
        }
    }

    public record PrescriptionOrderLinkResponse(
            Long id,
            Long orderId,
            String orderReference,
            Long medicineId,
            String medicineName,
            Long pharmacyId,
            String verificationStatus,
            Instant verifiedAt
    ) {
        public static PrescriptionOrderLinkResponse from(PrescriptionOrderLink link) {
            return new PrescriptionOrderLinkResponse(
                    link.getId(),
                    link.getOrderId(),
                    link.getOrderReference(),
                    link.getMedicine().getId(),
                    link.getMedicine().getName(),
                    link.getPharmacy().getId(),
                    link.getVerificationStatus(),
                    link.getVerifiedAt()
            );
        }
    }

    public record PrescriptionResponse(
            Long id,
            String referenceCode,
            Long customerId,
            String customerName,
            Long pharmacyId,
            String pharmacyName,
            String patientName,
            Integer patientAge,
            String doctorName,
            String doctorRegistrationNumber,
            String clinicName,
            LocalDate issuedDate,
            LocalDate validUntil,
            boolean expired,
            String originalFilename,
            String contentType,
            Long fileSizeBytes,
            String notes,
            PrescriptionStatus status,
            String rejectionReason,
            String reviewNotes,
            Long reviewedByPharmacyId,
            String reviewedByPharmacyName,
            String pharmacistName,
            String pharmacistRegistrationNumber,
            Instant reviewedAt,
            Instant createdAt,
            List<PrescriptionAuditResponse> auditHistory,
            List<PrescriptionOrderLinkResponse> orderLinks
    ) {
        public static PrescriptionResponse summary(Prescription p) {
            return withDetails(p, List.of(), List.of());
        }

        public static PrescriptionResponse withDetails(
                Prescription p,
                List<PrescriptionAuditLog> audits,
                List<PrescriptionOrderLink> orderLinks) {
            return new PrescriptionResponse(
                    p.getId(),
                    p.getReferenceCode(),
                    p.getCustomer().getId(),
                    p.getCustomer().getFullName(),
                    p.getPharmacy() != null ? p.getPharmacy().getId() : null,
                    p.getPharmacy() != null ? p.getPharmacy().getPharmacyName() : null,
                    p.getPatientName(),
                    p.getPatientAge(),
                    p.getDoctorName(),
                    p.getDoctorRegistrationNumber(),
                    p.getClinicName(),
                    p.getIssuedDate(),
                    p.getValidUntil(),
                    p.getStatus() == PrescriptionStatus.EXPIRED || p.isExpiredAsOf(LocalDate.now()),
                    p.getOriginalFilename(),
                    p.getContentType(),
                    p.getFileSizeBytes(),
                    p.getNotes(),
                    p.getStatus(),
                    p.getRejectionReason(),
                    p.getReviewNotes(),
                    p.getReviewedByPharmacy() != null ? p.getReviewedByPharmacy().getId() : null,
                    p.getReviewedByPharmacy() != null ? p.getReviewedByPharmacy().getPharmacyName() : null,
                    p.getPharmacistName(),
                    p.getPharmacistRegistrationNumber(),
                    p.getReviewedAt(),
                    p.getCreatedAt(),
                    audits.stream().map(PrescriptionAuditResponse::from).toList(),
                    orderLinks.stream().map(PrescriptionOrderLinkResponse::from).toList()
            );
        }
    }
}
