package com.medexa.admin.service;

import com.medexa.admin.dto.AdminDtos.PharmacyStatusUpdateRequest;
import com.medexa.admin.dto.AdminDtos.PharmacyVerificationRequest;
import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.BusinessRuleException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.pharmacy.Pharmacy;
import com.medexa.pharmacy.PharmacyLicense;
import com.medexa.pharmacy.PharmacyLicenseRepository;
import com.medexa.pharmacy.PharmacyRepository;
import com.medexa.pharmacy.VerificationStatus;
import com.medexa.pharmacy.dto.PharmacyDtos.PharmacyProfileResponse;
import com.medexa.pharmacy.service.PharmacyService;
import com.medexa.user.AccountStatus;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOversightService {

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyLicenseRepository licenseRepository;
    private final UserRepository userRepository;
    private final PharmacyService pharmacyService;

    @Transactional(readOnly = true)
    public Page<PharmacyProfileResponse> listPharmacies(
            VerificationStatus status,
            String query,
            Pageable pageable) {
        return pharmacyRepository.findForAdmin(status, query, pageable)
                .map(pharmacyService::buildProfileResponse);
    }

    @Transactional(readOnly = true)
    public PharmacyProfileResponse getPharmacyDetail(Long pharmacyId) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found."));
        return pharmacyService.buildProfileResponse(pharmacy);
    }

    @Transactional
    public PharmacyProfileResponse verifyPharmacy(
            Long adminUserId,
            Long pharmacyId,
            PharmacyVerificationRequest request) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found."));
        User owner = pharmacy.getUser();
        Instant now = Instant.now();

        List<PharmacyLicense> licenses = licenseRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacyId);
        PharmacyLicense latestLicense = licenses.isEmpty() ? null : licenses.get(0);

        if (Boolean.TRUE.equals(request.approved())) {
            pharmacy.setVerificationStatus(VerificationStatus.APPROVED);
            pharmacy.setRejectionReason(null);
            pharmacy.setAcceptingOrders(true);
            pharmacy.setVerifiedAt(now);
            pharmacy.setVerifiedBy(adminUserId);

            owner.setAccountStatus(AccountStatus.ACTIVE);
            if (latestLicense != null) {
                latestLicense.setVerificationStatus(VerificationStatus.APPROVED);
                latestLicense.setReviewedBy(adminUserId);
                latestLicense.setReviewedAt(now);
                latestLicense.setReviewNotes(request.notes());
                licenseRepository.save(latestLicense);
            }
        } else {
            if (request.notes() == null || request.notes().isBlank()) {
                throw new BadRequestException("A rejection reason is required when rejecting pharmacy verification.");
            }
            String reason = request.notes().trim();
            pharmacy.setVerificationStatus(VerificationStatus.REJECTED);
            pharmacy.setRejectionReason(reason);
            pharmacy.setAcceptingOrders(false);

            owner.setAccountStatus(AccountStatus.REJECTED);
            if (latestLicense != null) {
                latestLicense.setVerificationStatus(VerificationStatus.REJECTED);
                latestLicense.setReviewedBy(adminUserId);
                latestLicense.setReviewedAt(now);
                latestLicense.setReviewNotes(reason);
                licenseRepository.save(latestLicense);
            }
        }

        userRepository.save(owner);
        pharmacyRepository.save(pharmacy);
        return pharmacyService.buildProfileResponse(pharmacy);
    }

    @Transactional
    public PharmacyProfileResponse updatePharmacyStatus(Long pharmacyId, PharmacyStatusUpdateRequest request) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found."));
        User owner = pharmacy.getUser();

        if (Boolean.TRUE.equals(request.suspended())) {
            pharmacy.setVerificationStatus(VerificationStatus.SUSPENDED);
            pharmacy.setAcceptingOrders(false);
            if (request.reason() != null && !request.reason().isBlank()) {
                pharmacy.setRejectionReason(request.reason().trim());
            }
            owner.setAccountStatus(AccountStatus.SUSPENDED);
        } else {
            if (pharmacy.getVerifiedAt() == null) {
                throw new BusinessRuleException("Pharmacy must pass initial license verification before activation.");
            }
            pharmacy.setVerificationStatus(VerificationStatus.APPROVED);
            pharmacy.setRejectionReason(null);
            pharmacy.setAcceptingOrders(true);
            owner.setAccountStatus(AccountStatus.ACTIVE);
        }

        userRepository.save(owner);
        pharmacyRepository.save(pharmacy);
        return pharmacyService.buildProfileResponse(pharmacy);
    }
}
