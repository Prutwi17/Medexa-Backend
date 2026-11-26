package com.medexa.prescription.service;

import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.BusinessRuleException;
import com.medexa.common.exception.ForbiddenException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.common.storage.FileStorageService;
import com.medexa.common.storage.FileStorageService.StoredFileInfo;
import com.medexa.medicine.Medicine;
import com.medexa.medicine.MedicineClassificationRule;
import com.medexa.medicine.MedicineRepository;
import com.medexa.medicine.service.MedicineClassificationService;
import com.medexa.pharmacy.Pharmacy;
import com.medexa.pharmacy.PharmacyLicense;
import com.medexa.pharmacy.PharmacyLicenseRepository;
import com.medexa.pharmacy.PharmacyRepository;
import com.medexa.pharmacy.VerificationStatus;
import com.medexa.pharmacy.service.PharmacyService;
import com.medexa.prescription.Prescription;
import com.medexa.prescription.PrescriptionAuditLog;
import com.medexa.prescription.PrescriptionAuditLogRepository;
import com.medexa.prescription.PrescriptionOrderLink;
import com.medexa.prescription.PrescriptionOrderLinkRepository;
import com.medexa.prescription.PrescriptionRepository;
import com.medexa.prescription.PrescriptionStatus;
import com.medexa.prescription.dto.PrescriptionDtos.DispatchEligibilityResponse;
import com.medexa.prescription.dto.PrescriptionDtos.PrescriptionResponse;
import com.medexa.prescription.dto.PrescriptionDtos.PrescriptionReviewRequest;
import com.medexa.prescription.dto.PrescriptionDtos.PrescriptionUploadMetadata;
import com.medexa.user.AccountStatus;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private static final int DEFAULT_VALIDITY_DAYS = 180;

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionAuditLogRepository auditLogRepository;
    private final PrescriptionOrderLinkRepository orderLinkRepository;
    private final UserRepository userRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyLicenseRepository pharmacyLicenseRepository;
    private final PharmacyService pharmacyService;
    private final MedicineRepository medicineRepository;
    private final MedicineClassificationService classificationService;
    private final FileStorageService fileStorageService;

    public record PrescriptionDocumentPayload(
            Resource resource,
            String originalFilename,
            String contentType
    ) {
    }

    @Transactional
    public PrescriptionResponse uploadPrescription(
            Long customerId,
            MultipartFile file,
            PrescriptionUploadMetadata metadata) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));

        Pharmacy targetPharmacy = null;
        if (metadata.pharmacyId() != null) {
            targetPharmacy = pharmacyRepository.findById(metadata.pharmacyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Selected pharmacy was not found."));
            if (targetPharmacy.getVerificationStatus() != VerificationStatus.APPROVED
                    || targetPharmacy.getUser().getAccountStatus() != AccountStatus.ACTIVE) {
                throw new BusinessRuleException("Prescriptions can only be submitted to verified active pharmacies.");
            }
        }

        LocalDate today = LocalDate.now();
        LocalDate issuedDate = metadata.issuedDate() != null ? metadata.issuedDate() : today;
        if (issuedDate.isAfter(today)) {
            throw new BadRequestException("Prescription issued date cannot be in the future.");
        }

        LocalDate validUntil = metadata.validUntil() != null
                ? metadata.validUntil()
                : issuedDate.plusDays(DEFAULT_VALIDITY_DAYS);
        if (validUntil.isBefore(issuedDate)) {
            throw new BadRequestException("Prescription expiry date cannot precede issued date.");
        }
        if (validUntil.isBefore(today)) {
            throw new BusinessRuleException("Expired prescriptions cannot be uploaded for verification.");
        }

        StoredFileInfo storedFile = fileStorageService.storePrescriptionDocument(file);
        String referenceCode = "RX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Prescription prescription = Prescription.builder()
                .customer(customer)
                .pharmacy(targetPharmacy)
                .referenceCode(referenceCode)
                .patientName(metadata.patientName().trim())
                .patientAge(metadata.patientAge())
                .doctorName(metadata.doctorName().trim())
                .doctorRegistrationNumber(trimToNull(metadata.doctorRegistrationNumber()))
                .clinicName(trimToNull(metadata.clinicName()))
                .issuedDate(issuedDate)
                .validUntil(validUntil)
                .fileUrl(storedFile.urlPath())
                .storedFilename(storedFile.storedFilename())
                .originalFilename(storedFile.originalFilename())
                .contentType(storedFile.contentType())
                .fileSizeBytes(storedFile.sizeBytes())
                .notes(trimToNull(metadata.notes()))
                .status(PrescriptionStatus.PENDING_REVIEW)
                .build();

        prescriptionRepository.save(prescription);

        recordAudit(
                prescription,
                customer.getId(),
                "CUSTOMER",
                customer.getFullName(),
                "UPLOADED",
                null,
                PrescriptionStatus.PENDING_REVIEW,
                targetPharmacy != null
                        ? "Uploaded for pharmacy: " + targetPharmacy.getPharmacyName()
                        : "Uploaded for pharmacist review"
        );

        return loadDetailedResponse(prescription);
    }

    @Transactional
    public Page<PrescriptionResponse> listCustomerPrescriptions(
            Long customerId,
            PrescriptionStatus status,
            Pageable pageable) {
        Page<Prescription> page = prescriptionRepository.findForCustomer(customerId, status, pageable);
        page.getContent().forEach(this::syncExpiryIfNeeded);
        return page.map(PrescriptionResponse::summary);
    }

    @Transactional
    public PrescriptionResponse getCustomerPrescription(Long customerId, Long prescriptionId) {
        Prescription prescription = requireOwnedPrescription(customerId, prescriptionId);
        syncExpiryIfNeeded(prescription);
        return loadDetailedResponse(prescription);
    }

    @Transactional
    public PrescriptionResponse cancelCustomerPrescription(Long customerId, Long prescriptionId) {
        Prescription prescription = requireOwnedPrescription(customerId, prescriptionId);
        syncExpiryIfNeeded(prescription);
        if (prescription.getStatus() == PrescriptionStatus.CANCELLED) {
            return loadDetailedResponse(prescription);
        }
        if (prescription.getStatus() == PrescriptionStatus.EXPIRED) {
            throw new BusinessRuleException("Expired prescriptions cannot be cancelled.");
        }

        PrescriptionStatus previous = prescription.getStatus();
        prescription.setStatus(PrescriptionStatus.CANCELLED);
        prescriptionRepository.save(prescription);

        recordAudit(
                prescription,
                prescription.getCustomer().getId(),
                "CUSTOMER",
                prescription.getCustomer().getFullName(),
                "CANCELLED",
                previous,
                PrescriptionStatus.CANCELLED,
                "Cancelled by customer."
        );

        return loadDetailedResponse(prescription);
    }

    @Transactional(readOnly = true)
    public PrescriptionDocumentPayload loadDocumentForCustomer(Long customerId, Long prescriptionId) {
        Prescription prescription = requireOwnedPrescription(customerId, prescriptionId);
        return loadDocumentPayload(prescription);
    }

    @Transactional
    public Page<PrescriptionResponse> listPharmacyPrescriptions(
            Long pharmacyUserId,
            PrescriptionStatus status,
            Pageable pageable) {
        Pharmacy pharmacy = requireAuthorizedPharmacyForRxReview(pharmacyUserId).pharmacy();
        Page<Prescription> page = prescriptionRepository.findForPharmacyQueue(pharmacy.getId(), status, pageable);
        page.getContent().forEach(this::syncExpiryIfNeeded);
        return page.map(PrescriptionResponse::summary);
    }

    @Transactional
    public PrescriptionResponse getPharmacyPrescription(Long pharmacyUserId, Long prescriptionId) {
        Pharmacy pharmacy = requireAuthorizedPharmacyForRxReview(pharmacyUserId).pharmacy();
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found."));
        ensurePharmacyAccess(pharmacy, prescription);
        syncExpiryIfNeeded(prescription);
        return loadDetailedResponse(prescription);
    }

    @Transactional(readOnly = true)
    public PrescriptionDocumentPayload loadDocumentForPharmacy(Long pharmacyUserId, Long prescriptionId) {
        Pharmacy pharmacy = requireAuthorizedPharmacyForRxReview(pharmacyUserId).pharmacy();
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found."));
        ensurePharmacyAccess(pharmacy, prescription);
        return loadDocumentPayload(prescription);
    }

    @Transactional
    public PrescriptionResponse reviewPrescription(
            Long pharmacyUserId,
            Long prescriptionId,
            PrescriptionReviewRequest request) {
        AuthorizedPharmacyReviewer reviewer = requireAuthorizedPharmacyForRxReview(pharmacyUserId);
        Pharmacy pharmacy = reviewer.pharmacy();
        PharmacyLicense activeLicense = reviewer.license();

        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found."));
        ensurePharmacyAccess(pharmacy, prescription);
        syncExpiryIfNeeded(prescription);

        if (prescription.getStatus() == PrescriptionStatus.CANCELLED) {
            throw new BusinessRuleException("Cancelled prescriptions cannot be reviewed.");
        }
        if (prescription.getStatus() == PrescriptionStatus.EXPIRED) {
            throw new BusinessRuleException("Expired prescriptions cannot be approved or modified.");
        }

        PrescriptionStatus previous = prescription.getStatus();
        LocalDate effectiveValidUntil = request.validUntilOverride() != null
                ? request.validUntilOverride()
                : prescription.getValidUntil();

        if (Boolean.TRUE.equals(request.approved())) {
            if (effectiveValidUntil.isBefore(LocalDate.now())) {
                throw new BusinessRuleException("Cannot approve an already expired prescription.");
            }
            if (effectiveValidUntil.isBefore(prescription.getIssuedDate())) {
                throw new BadRequestException("Prescription expiry date cannot precede issued date.");
            }
            prescription.setStatus(PrescriptionStatus.APPROVED);
            prescription.setValidUntil(effectiveValidUntil);
            prescription.setRejectionReason(null);
            if (prescription.getPharmacy() == null) {
                prescription.setPharmacy(pharmacy);
            }
        } else {
            if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
                throw new BadRequestException("A rejection reason is required when rejecting a prescription.");
            }
            prescription.setStatus(PrescriptionStatus.REJECTED);
            prescription.setRejectionReason(request.rejectionReason().trim());
        }

        prescription.setReviewNotes(trimToNull(request.reviewNotes()));
        prescription.setReviewedByUserId(pharmacyUserId);
        prescription.setReviewedByPharmacy(pharmacy);
        prescription.setPharmacistName(activeLicense.getPharmacistName());
        prescription.setPharmacistRegistrationNumber(activeLicense.getPharmacistRegistrationNumber());
        prescription.setReviewedAt(Instant.now());
        prescriptionRepository.save(prescription);

        recordAudit(
                prescription,
                pharmacyUserId,
                "PHARMACY",
                activeLicense.getPharmacistName() + " (" + pharmacy.getPharmacyName() + ")",
                Boolean.TRUE.equals(request.approved()) ? "APPROVED" : "REJECTED",
                previous,
                prescription.getStatus(),
                Boolean.TRUE.equals(request.approved())
                        ? (request.reviewNotes() != null && !request.reviewNotes().isBlank()
                            ? request.reviewNotes().trim()
                            : "Verified by registered pharmacist " + activeLicense.getPharmacistRegistrationNumber())
                        : prescription.getRejectionReason()
        );

        return loadDetailedResponse(prescription);
    }

    @Transactional
    public Page<PrescriptionResponse> listAdminPrescriptions(PrescriptionStatus status, Pageable pageable) {
        Page<Prescription> page = prescriptionRepository.findForAdmin(status, pageable);
        page.getContent().forEach(this::syncExpiryIfNeeded);
        return page.map(PrescriptionResponse::summary);
    }

    @Transactional
    public PrescriptionResponse getAdminPrescription(Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found."));
        syncExpiryIfNeeded(prescription);
        return loadDetailedResponse(prescription);
    }

    @Transactional(readOnly = true)
    public PrescriptionDocumentPayload loadDocumentForAdmin(Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found."));
        return loadDocumentPayload(prescription);
    }

    @Transactional
    public DispatchEligibilityResponse checkDispatchEligibility(
            Long customerId,
            Long medicineId,
            Long prescriptionId,
            boolean throwOnIneligible) {
        Medicine medicine = medicineRepository.findById(medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found."));
        MedicineClassificationRule rule = classificationService.resolveRule(
                medicine.getClassificationCode(),
                medicine.isPrescriptionRequired()
        );

        boolean rxRequired = medicine.isPrescriptionRequired() || rule.isPrescriptionRequired();
        boolean autoSubstitutionAllowed = !rxRequired && rule.isAutoSubstitutionAllowed();

        if (!rxRequired) {
            return new DispatchEligibilityResponse(
                    true,
                    false,
                    rule.getClassificationCode(),
                    autoSubstitutionAllowed,
                    null,
                    null,
                    null,
                    null,
                    "OTC medicine does not require prescription verification."
            );
        }

        if (prescriptionId == null) {
            String msg = "Dispatch blocked: " + medicine.getName() + " (" + rule.getDisplayName()
                    + ") requires a verified medical prescription.";
            if (throwOnIneligible) {
                throw new BusinessRuleException(msg);
            }
            return new DispatchEligibilityResponse(
                    false, true, rule.getClassificationCode(), false, null, null, null, null, msg
            );
        }

        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Referenced prescription was not found."));
        if (!prescription.getCustomer().getId().equals(customerId)) {
            throw new ForbiddenException("Prescription does not belong to the authenticated customer.");
        }

        syncExpiryIfNeeded(prescription);

        if (prescription.getPharmacy() != null
                && !prescription.getPharmacy().getId().equals(medicine.getPharmacy().getId())) {
            String msg = "Dispatch blocked: Prescription belongs to a different dispensing pharmacy.";
            recordAudit(prescription, customerId, "SYSTEM", "Compliance Guard",
                    "DISPATCH_BLOCKED_UNVERIFIED", prescription.getStatus(), prescription.getStatus(), msg);
            if (throwOnIneligible) {
                throw new BusinessRuleException(msg);
            }
            return new DispatchEligibilityResponse(
                    false, true, rule.getClassificationCode(), false, prescription.getId(),
                    prescription.getReferenceCode(), prescription.getStatus(), prescription.getValidUntil(), msg
            );
        }

        if (prescription.getStatus() != PrescriptionStatus.APPROVED) {
            String msg = "Dispatch blocked: Prescription status is " + prescription.getStatus()
                    + " and has not been approved by authorized pharmacy personnel.";
            recordAudit(prescription, customerId, "SYSTEM", "Compliance Guard",
                    "DISPATCH_BLOCKED_UNVERIFIED", prescription.getStatus(), prescription.getStatus(), msg);
            if (throwOnIneligible) {
                throw new BusinessRuleException(msg);
            }
            return new DispatchEligibilityResponse(
                    false, true, rule.getClassificationCode(), false, prescription.getId(),
                    prescription.getReferenceCode(), prescription.getStatus(), prescription.getValidUntil(), msg
            );
        }

        LocalDate issuedPlusRuleDays = prescription.getIssuedDate().plusDays(rule.getDefaultRxValidityDays());
        if (LocalDate.now().isAfter(issuedPlusRuleDays)) {
            String msg = "Dispatch blocked: Prescription exceeds maximum validity window ("
                    + rule.getDefaultRxValidityDays() + " days) for " + rule.getClassificationCode() + ".";
            recordAudit(prescription, customerId, "SYSTEM", "Compliance Guard",
                    "DISPATCH_BLOCKED_UNVERIFIED", prescription.getStatus(), prescription.getStatus(), msg);
            if (throwOnIneligible) {
                throw new BusinessRuleException(msg);
            }
            return new DispatchEligibilityResponse(
                    false, true, rule.getClassificationCode(), false, prescription.getId(),
                    prescription.getReferenceCode(), prescription.getStatus(), prescription.getValidUntil(), msg
            );
        }

        return new DispatchEligibilityResponse(
                true,
                true,
                rule.getClassificationCode(),
                false,
                prescription.getId(),
                prescription.getReferenceCode(),
                prescription.getStatus(),
                prescription.getValidUntil(),
                "Prescription verified and eligible for dispensing."
        );
    }

    @Transactional
    public PrescriptionOrderLink linkPrescriptionToOrder(
            Long customerId,
            Long medicineId,
            Long prescriptionId,
            Long orderId,
            String orderReference) {
        DispatchEligibilityResponse eligibility = checkDispatchEligibility(customerId, medicineId, prescriptionId, true);
        if (!eligibility.prescriptionRequired() || prescriptionId == null) {
            throw new BadRequestException("OTC medicines do not require prescription order linking.");
        }

        Medicine medicine = medicineRepository.findById(medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found."));
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found."));

        PrescriptionOrderLink link = PrescriptionOrderLink.builder()
                .prescription(prescription)
                .orderId(orderId)
                .orderReference(trimToNull(orderReference))
                .medicine(medicine)
                .customer(prescription.getCustomer())
                .pharmacy(medicine.getPharmacy())
                .verificationStatus(prescription.getStatus().name())
                .verifiedAt(Instant.now())
                .build();
        orderLinkRepository.save(link);

        recordAudit(
                prescription,
                customerId,
                "CUSTOMER",
                prescription.getCustomer().getFullName(),
                "LINKED_TO_ORDER",
                prescription.getStatus(),
                prescription.getStatus(),
                "Linked to order " + (orderReference != null ? orderReference : String.valueOf(orderId))
                        + " for medicine " + medicine.getName()
        );

        return link;
    }

    public Prescription requireOwnedPrescription(Long customerId, Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found."));
        if (!prescription.getCustomer().getId().equals(customerId)) {
            throw new ForbiddenException("You do not have permission to access this prescription.");
        }
        return prescription;
    }

    public void syncExpiryIfNeeded(Prescription prescription) {
        if ((prescription.getStatus() == PrescriptionStatus.PENDING_REVIEW
                || prescription.getStatus() == PrescriptionStatus.APPROVED)
                && prescription.isExpiredAsOf(LocalDate.now())) {
            PrescriptionStatus previous = prescription.getStatus();
            prescription.setStatus(PrescriptionStatus.EXPIRED);
            prescriptionRepository.save(prescription);
            recordAudit(
                    prescription,
                    null,
                    "SYSTEM",
                    "Validity Scheduler",
                    "EXPIRED",
                    previous,
                    PrescriptionStatus.EXPIRED,
                    "Prescription passed validity date (" + prescription.getValidUntil() + ")."
            );
        }
    }

    private record AuthorizedPharmacyReviewer(Pharmacy pharmacy, PharmacyLicense license) {
    }

    private AuthorizedPharmacyReviewer requireAuthorizedPharmacyForRxReview(Long pharmacyUserId) {
        Pharmacy pharmacy = pharmacyService.requireVerifiedActivePharmacy(pharmacyUserId);
        List<PharmacyLicense> licenses = pharmacyLicenseRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacy.getId());
        PharmacyLicense activeLicense = licenses.stream()
                .filter(l -> l.getVerificationStatus() == VerificationStatus.APPROVED
                        && l.getExpiryDate() != null
                        && !l.getExpiryDate().isBefore(LocalDate.now()))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException(
                        "An active, non-expired verified pharmacist drug license is required to review prescriptions."
                ));
        return new AuthorizedPharmacyReviewer(pharmacy, activeLicense);
    }

    private void ensurePharmacyAccess(Pharmacy pharmacy, Prescription prescription) {
        if (prescription.getPharmacy() != null && !prescription.getPharmacy().getId().equals(pharmacy.getId())) {
            throw new ForbiddenException("This prescription is assigned to a different pharmacy.");
        }
        if (prescription.getReviewedByPharmacy() != null
                && !prescription.getReviewedByPharmacy().getId().equals(pharmacy.getId())) {
            throw new ForbiddenException("This prescription was reviewed by another pharmacy.");
        }
    }

    private PrescriptionDocumentPayload loadDocumentPayload(Prescription prescription) {
        Resource resource = fileStorageService.loadAsResource(
                FileStorageService.BUCKET_PRESCRIPTIONS,
                prescription.getStoredFilename()
        );
        return new PrescriptionDocumentPayload(
                resource,
                prescription.getOriginalFilename(),
                prescription.getContentType()
        );
    }

    private PrescriptionResponse loadDetailedResponse(Prescription prescription) {
        List<PrescriptionAuditLog> audits = auditLogRepository
                .findByPrescriptionIdOrderByCreatedAtAscIdAsc(prescription.getId());
        List<PrescriptionOrderLink> links = orderLinkRepository
                .findByPrescriptionIdOrderByCreatedAtDesc(prescription.getId());
        return PrescriptionResponse.withDetails(prescription, audits, links);
    }

    private void recordAudit(
            Prescription prescription,
            Long actorUserId,
            String actorRole,
            String actorName,
            String action,
            PrescriptionStatus previousStatus,
            PrescriptionStatus newStatus,
            String notes) {
        PrescriptionAuditLog log = PrescriptionAuditLog.builder()
                .prescription(prescription)
                .actorUserId(actorUserId)
                .actorRole(actorRole)
                .actorName(actorName)
                .action(action)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .notes(notes)
                .build();
        auditLogRepository.save(log);
    }

    private String trimToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
