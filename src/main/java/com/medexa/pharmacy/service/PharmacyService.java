package com.medexa.pharmacy.service;

import com.medexa.common.exception.BusinessRuleException;
import com.medexa.common.exception.ConflictException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.common.storage.FileStorageService;
import com.medexa.pharmacy.Pharmacy;
import com.medexa.pharmacy.PharmacyDeliveryZone;
import com.medexa.pharmacy.PharmacyDeliveryZoneRepository;
import com.medexa.pharmacy.PharmacyLicense;
import com.medexa.pharmacy.PharmacyLicenseRepository;
import com.medexa.pharmacy.PharmacyRepository;
import com.medexa.pharmacy.VerificationStatus;
import com.medexa.pharmacy.dto.PharmacyDtos.DeliveryZoneRequest;
import com.medexa.pharmacy.dto.PharmacyDtos.DeliveryZoneResponse;
import com.medexa.pharmacy.dto.PharmacyDtos.PharmacyLicenseResponse;
import com.medexa.pharmacy.dto.PharmacyDtos.PharmacyProfileResponse;
import com.medexa.pharmacy.dto.PharmacyDtos.PublicPharmacyResponse;
import com.medexa.pharmacy.dto.PharmacyDtos.SubmitPharmacyLicenseRequest;
import com.medexa.pharmacy.dto.PharmacyDtos.UpdatePharmacyProfileRequest;
import com.medexa.user.AccountStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PharmacyService {

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyLicenseRepository pharmacyLicenseRepository;
    private final PharmacyDeliveryZoneRepository deliveryZoneRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public PharmacyProfileResponse getOwnProfile(Long userId) {
        Pharmacy pharmacy = requirePharmacyByUserId(userId);
        return buildProfileResponse(pharmacy);
    }

    @Transactional
    public PharmacyProfileResponse updateOwnProfile(Long userId, UpdatePharmacyProfileRequest request) {
        Pharmacy pharmacy = requirePharmacyByUserId(userId);
        pharmacy.setPharmacyName(request.pharmacyName().trim());
        pharmacy.setContactPhone(request.contactPhone().trim());
        pharmacy.setAddressLine(request.addressLine().trim());
        pharmacy.setCity(request.city().trim());
        pharmacy.setState(request.state().trim());
        pharmacy.setPostalCode(request.postalCode().trim());

        if (request.acceptingOrders() != null) {
            if (request.acceptingOrders() && pharmacy.getVerificationStatus() != VerificationStatus.APPROVED) {
                throw new BusinessRuleException("Only verified pharmacies can accept customer orders.");
            }
            pharmacy.setAcceptingOrders(request.acceptingOrders());
        }

        pharmacyRepository.save(pharmacy);
        return buildProfileResponse(pharmacy);
    }

    @Transactional
    public PharmacyLicenseResponse submitLicense(Long userId, SubmitPharmacyLicenseRequest request) {
        Pharmacy pharmacy = requirePharmacyByUserId(userId);
        String licenseNum = request.licenseNumber().trim();
        if (pharmacyLicenseRepository.existsByLicenseNumberIgnoreCase(licenseNum)) {
            throw new ConflictException("This drug license number is already registered.");
        }

        PharmacyLicense license = PharmacyLicense.builder()
                .pharmacy(pharmacy)
                .licenseNumber(licenseNum)
                .issuingAuthority(request.issuingAuthority().trim())
                .pharmacistName(request.pharmacistName().trim())
                .pharmacistRegistrationNumber(request.pharmacistRegistrationNumber().trim())
                .expiryDate(request.expiryDate())
                .documentUrl(request.documentUrl() != null && !request.documentUrl().isBlank()
                        ? request.documentUrl().trim()
                        : null)
                .verificationStatus(VerificationStatus.PENDING_VERIFICATION)
                .build();
        pharmacyLicenseRepository.save(license);

        if (pharmacy.getVerificationStatus() == VerificationStatus.REJECTED) {
            pharmacy.setVerificationStatus(VerificationStatus.PENDING_VERIFICATION);
            pharmacy.setRejectionReason(null);
            pharmacyRepository.save(pharmacy);
        }

        return PharmacyLicenseResponse.from(license);
    }

    @Transactional
    public PharmacyLicenseResponse uploadLicenseDocument(Long userId, Long licenseId, MultipartFile file) {
        Pharmacy pharmacy = requirePharmacyByUserId(userId);
        PharmacyLicense license = pharmacyLicenseRepository.findById(licenseId)
                .filter(l -> l.getPharmacy().getId().equals(pharmacy.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy license record not found."));

        String documentUrl = fileStorageService.storeDocument(FileStorageService.BUCKET_LICENSES, file);
        license.setDocumentUrl(documentUrl);
        pharmacyLicenseRepository.save(license);
        return PharmacyLicenseResponse.from(license);
    }

    @Transactional(readOnly = true)
    public List<DeliveryZoneResponse> listDeliveryZones(Long userId) {
        Pharmacy pharmacy = requirePharmacyByUserId(userId);
        return deliveryZoneRepository.findByPharmacyIdOrderByZoneNameAsc(pharmacy.getId()).stream()
                .map(DeliveryZoneResponse::from)
                .toList();
    }

    @Transactional
    public DeliveryZoneResponse createDeliveryZone(Long userId, DeliveryZoneRequest request) {
        Pharmacy pharmacy = requirePharmacyByUserId(userId);
        String postalCode = request.postalCode().trim();
        if (deliveryZoneRepository.existsByPharmacyIdAndPostalCodeIgnoreCase(pharmacy.getId(), postalCode)) {
            throw new ConflictException("A delivery zone for postal code '" + postalCode + "' already exists.");
        }

        PharmacyDeliveryZone zone = PharmacyDeliveryZone.builder()
                .pharmacy(pharmacy)
                .zoneName(request.zoneName().trim())
                .postalCode(postalCode)
                .city(request.city().trim())
                .deliveryFee(request.deliveryFee())
                .minOrderAmount(request.minOrderAmount())
                .estimatedDeliveryMinutes(request.estimatedDeliveryMinutes())
                .active(true)
                .build();
        deliveryZoneRepository.save(zone);
        return DeliveryZoneResponse.from(zone);
    }

    @Transactional
    public DeliveryZoneResponse updateDeliveryZone(Long userId, Long zoneId, DeliveryZoneRequest request) {
        Pharmacy pharmacy = requirePharmacyByUserId(userId);
        PharmacyDeliveryZone zone = deliveryZoneRepository.findByIdAndPharmacyId(zoneId, pharmacy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Delivery zone not found."));

        String postalCode = request.postalCode().trim();
        if (deliveryZoneRepository.existsByPharmacyIdAndPostalCodeIgnoreCaseAndIdNot(
                pharmacy.getId(), postalCode, zoneId)) {
            throw new ConflictException("Another delivery zone already covers postal code '" + postalCode + "'.");
        }

        zone.setZoneName(request.zoneName().trim());
        zone.setPostalCode(postalCode);
        zone.setCity(request.city().trim());
        zone.setDeliveryFee(request.deliveryFee());
        zone.setMinOrderAmount(request.minOrderAmount());
        zone.setEstimatedDeliveryMinutes(request.estimatedDeliveryMinutes());
        deliveryZoneRepository.save(zone);
        return DeliveryZoneResponse.from(zone);
    }

    @Transactional
    public DeliveryZoneResponse toggleDeliveryZoneStatus(Long userId, Long zoneId, boolean active) {
        Pharmacy pharmacy = requirePharmacyByUserId(userId);
        PharmacyDeliveryZone zone = deliveryZoneRepository.findByIdAndPharmacyId(zoneId, pharmacy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Delivery zone not found."));
        zone.setActive(active);
        deliveryZoneRepository.save(zone);
        return DeliveryZoneResponse.from(zone);
    }

    @Transactional
    public void deleteDeliveryZone(Long userId, Long zoneId) {
        Pharmacy pharmacy = requirePharmacyByUserId(userId);
        PharmacyDeliveryZone zone = deliveryZoneRepository.findByIdAndPharmacyId(zoneId, pharmacy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Delivery zone not found."));
        deliveryZoneRepository.delete(zone);
    }

    @Transactional(readOnly = true)
    public Page<PublicPharmacyResponse> listPublicPharmacies(String query, Pageable pageable) {
        return pharmacyRepository.findPublicVerifiedPharmacies(query, pageable)
                .map(pharmacy -> PublicPharmacyResponse.from(
                        pharmacy,
                        deliveryZoneRepository.findByPharmacyIdAndActiveTrueOrderByZoneNameAsc(pharmacy.getId())
                ));
    }

    @Transactional(readOnly = true)
    public PublicPharmacyResponse getPublicPharmacyBySlug(String slug) {
        Pharmacy pharmacy = pharmacyRepository.findBySlugAndVerificationStatusAndUserAccountStatus(
                        slug, VerificationStatus.APPROVED, AccountStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Verified pharmacy not found."));
        return PublicPharmacyResponse.from(
                pharmacy,
                deliveryZoneRepository.findByPharmacyIdAndActiveTrueOrderByZoneNameAsc(pharmacy.getId())
        );
    }

    public Pharmacy requirePharmacyByUserId(Long userId) {
        return pharmacyRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy profile not found for this account."));
    }

    public Pharmacy requireVerifiedActivePharmacy(Long userId) {
        Pharmacy pharmacy = requirePharmacyByUserId(userId);
        if (pharmacy.getVerificationStatus() != VerificationStatus.APPROVED
                || pharmacy.getUser().getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Only active, license-verified pharmacies can perform catalog and inventory operations."
            );
        }
        return pharmacy;
    }

    public PharmacyProfileResponse buildProfileResponse(Pharmacy pharmacy) {
        List<PharmacyLicense> licenses = pharmacyLicenseRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacy.getId());
        List<PharmacyDeliveryZone> zones = deliveryZoneRepository.findByPharmacyIdOrderByZoneNameAsc(pharmacy.getId());
        return PharmacyProfileResponse.from(pharmacy, licenses, zones);
    }
}
