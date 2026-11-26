package com.medexa.rider.service;

import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.BusinessRuleException;
import com.medexa.common.exception.ConflictException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.common.storage.FileStorageService;
import com.medexa.delivery.DeliveryRepository;
import com.medexa.delivery.DeliveryStatus;
import com.medexa.pharmacy.VerificationStatus;
import com.medexa.rider.Rider;
import com.medexa.rider.RiderAvailabilityStatus;
import com.medexa.rider.RiderDeliveryZone;
import com.medexa.rider.RiderDeliveryZoneRepository;
import com.medexa.rider.RiderEarning;
import com.medexa.rider.RiderEarningRepository;
import com.medexa.rider.RiderRepository;
import com.medexa.rider.dto.RiderDtos.AdminRiderStatusRequest;
import com.medexa.rider.dto.RiderDtos.AdminVerifyRiderRequest;
import com.medexa.rider.dto.RiderDtos.RiderDeliveryZoneRequest;
import com.medexa.rider.dto.RiderDtos.RiderDeliveryZoneResponse;
import com.medexa.rider.dto.RiderDtos.RiderEarningResponse;
import com.medexa.rider.dto.RiderDtos.RiderEarningsSummaryResponse;
import com.medexa.rider.dto.RiderDtos.RiderProfileResponse;
import com.medexa.rider.dto.RiderDtos.RiderZoneStatusRequest;
import com.medexa.rider.dto.RiderDtos.UpdateRiderAvailabilityRequest;
import com.medexa.rider.dto.RiderDtos.UpdateRiderLocationRequest;
import com.medexa.rider.dto.RiderDtos.UpdateRiderProfileRequest;
import com.medexa.user.AccountStatus;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class RiderService {

    public static final Set<DeliveryStatus> ACTIVE_WORKLOAD_STATUSES = EnumSet.of(
            DeliveryStatus.ASSIGNED,
            DeliveryStatus.ACCEPTED,
            DeliveryStatus.PICKED_UP,
            DeliveryStatus.OUT_FOR_DELIVERY
    );

    private final RiderRepository riderRepository;
    private final RiderDeliveryZoneRepository zoneRepository;
    private final RiderEarningRepository earningRepository;
    private final DeliveryRepository deliveryRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public Rider requireRiderByUserId(Long userId) {
        return riderRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Rider profile not found."));
    }

    @Transactional(readOnly = true)
    public Rider requireApprovedActiveRider(Long userId) {
        Rider rider = requireRiderByUserId(userId);
        if (rider.getVerificationStatus() != VerificationStatus.APPROVED
                || rider.getUser().getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Your rider account must be verified and active.");
        }
        return rider;
    }

    @Transactional(readOnly = true)
    public RiderProfileResponse getOwnProfile(Long userId) {
        Rider rider = requireRiderByUserId(userId);
        return buildProfileResponse(rider);
    }

    @Transactional
    public RiderProfileResponse updateOwnProfile(Long userId, UpdateRiderProfileRequest request) {
        Rider rider = requireRiderByUserId(userId);
        String newLicense = request.drivingLicenseNumber().trim().toUpperCase(Locale.ROOT);
        if (!newLicense.equalsIgnoreCase(rider.getDrivingLicenseNumber())
                && riderRepository.existsByDrivingLicenseNumberIgnoreCase(newLicense)) {
            throw new ConflictException("Another rider is already registered with this driving license.");
        }

        rider.setPhoneNumber(request.phoneNumber().trim());
        rider.setVehicleType(request.vehicleType());
        rider.setVehicleRegistrationNumber(request.vehicleRegistrationNumber().trim().toUpperCase(Locale.ROOT));
        rider.setVehicleModel(request.vehicleModel() != null ? request.vehicleModel().trim() : null);
        rider.setServiceCity(request.serviceCity().trim());
        rider.setDrivingLicenseNumber(newLicense);
        rider.setDrivingLicenseExpiry(request.drivingLicenseExpiry());
        if (request.identityDocumentType() != null) {
            rider.setIdentityDocumentType(request.identityDocumentType().trim().toUpperCase(Locale.ROOT));
        }
        if (request.identityDocumentNumber() != null) {
            rider.setIdentityDocumentNumber(request.identityDocumentNumber().trim());
        }
        if (request.currentLatitude() != null && request.currentLongitude() != null) {
            rider.setCurrentLatitude(request.currentLatitude());
            rider.setCurrentLongitude(request.currentLongitude());
            rider.setLocationUpdatedAt(Instant.now());
        }
        if (request.maxConcurrentDeliveries() != null) {
            rider.setMaxConcurrentDeliveries(request.maxConcurrentDeliveries());
        }
        riderRepository.save(rider);

        User user = rider.getUser();
        user.setPhoneNumber(rider.getPhoneNumber());
        userRepository.save(user);

        return buildProfileResponse(rider);
    }

    @Transactional
    public RiderProfileResponse uploadDocument(Long userId, String docType, MultipartFile file) {
        Rider rider = requireRiderByUserId(userId);
        String storedPath = fileStorageService.storeDocument(FileStorageService.BUCKET_RIDER_DOCS, file);
        if ("IDENTITY".equalsIgnoreCase(docType)) {
            rider.setIdentityDocumentUrl(storedPath);
        } else if ("DRIVING_LICENSE".equalsIgnoreCase(docType)) {
            rider.setDrivingLicenseUrl(storedPath);
        } else {
            throw new BadRequestException("Unsupported document type. Use IDENTITY or DRIVING_LICENSE.");
        }
        riderRepository.save(rider);
        return buildProfileResponse(rider);
    }

    @Transactional(readOnly = true)
    public Resource loadRiderDocumentForAdminOrOwner(Long riderId, String docType) {
        Rider rider = riderRepository.findById(riderId)
                .orElseThrow(() -> new ResourceNotFoundException("Rider not found."));
        String path = "IDENTITY".equalsIgnoreCase(docType)
                ? rider.getIdentityDocumentUrl()
                : rider.getDrivingLicenseUrl();
        if (path == null || path.isBlank()) {
            throw new ResourceNotFoundException("Requested rider document was not uploaded.");
        }
        String filename = extractFilename(path);
        return fileStorageService.loadAsResource(FileStorageService.BUCKET_RIDER_DOCS, filename);
    }

    @Transactional
    public RiderProfileResponse updateAvailability(Long userId, UpdateRiderAvailabilityRequest request) {
        Rider rider = requireApprovedActiveRider(userId);
        if (request.availabilityStatus() == RiderAvailabilityStatus.ONLINE) {
            long activeZones = zoneRepository.findByRiderIdOrderByPriorityOrderAscIdAsc(rider.getId())
                    .stream().filter(RiderDeliveryZone::isActive).count();
            if (activeZones == 0) {
                throw new BusinessRuleException("Add at least one active delivery zone before going ONLINE.");
            }
        }
        if (request.availabilityStatus() == RiderAvailabilityStatus.OFFLINE) {
            long activeCount = deliveryRepository.countByAssignedRiderIdAndStatusIn(
                    rider.getId(), ACTIVE_WORKLOAD_STATUSES
            );
            if (activeCount > 0) {
                throw new BusinessRuleException("Complete or resolve active deliveries before going OFFLINE.");
            }
        }
        rider.setAvailabilityStatus(request.availabilityStatus());
        if (request.currentLatitude() != null && request.currentLongitude() != null) {
            rider.setCurrentLatitude(request.currentLatitude());
            rider.setCurrentLongitude(request.currentLongitude());
            rider.setLocationUpdatedAt(Instant.now());
        }
        riderRepository.save(rider);
        return buildProfileResponse(rider);
    }

    @Transactional
    public RiderProfileResponse updateLocation(Long userId, UpdateRiderLocationRequest request) {
        Rider rider = requireApprovedActiveRider(userId);
        rider.setCurrentLatitude(request.latitude());
        rider.setCurrentLongitude(request.longitude());
        rider.setLocationUpdatedAt(Instant.now());
        riderRepository.save(rider);
        return buildProfileResponse(rider);
    }

    @Transactional(readOnly = true)
    public List<RiderDeliveryZoneResponse> listZones(Long userId) {
        Rider rider = requireRiderByUserId(userId);
        return zoneRepository.findByRiderIdOrderByPriorityOrderAscIdAsc(rider.getId())
                .stream().map(RiderDeliveryZoneResponse::from).toList();
    }

    @Transactional
    public RiderDeliveryZoneResponse addZone(Long userId, RiderDeliveryZoneRequest request) {
        Rider rider = requireRiderByUserId(userId);
        String normalizedPostal = request.postalCode().trim().toUpperCase(Locale.ROOT);
        if (zoneRepository.existsByRiderIdAndPostalCodeIgnoreCase(rider.getId(), normalizedPostal)) {
            throw new ConflictException("Delivery zone for postal code " + normalizedPostal + " already exists.");
        }
        RiderDeliveryZone zone = RiderDeliveryZone.builder()
                .rider(rider)
                .zoneName(request.zoneName().trim())
                .city(request.city().trim())
                .postalCode(normalizedPostal)
                .priorityOrder(request.priorityOrder() != null ? request.priorityOrder() : 1)
                .active(request.active() == null || request.active())
                .build();
        return RiderDeliveryZoneResponse.from(zoneRepository.save(zone));
    }

    @Transactional
    public RiderDeliveryZoneResponse toggleZoneStatus(Long userId, Long zoneId, RiderZoneStatusRequest request) {
        Rider rider = requireRiderByUserId(userId);
        RiderDeliveryZone zone = zoneRepository.findByIdAndRiderId(zoneId, rider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Rider delivery zone not found."));
        zone.setActive(Boolean.TRUE.equals(request.active()));
        return RiderDeliveryZoneResponse.from(zoneRepository.save(zone));
    }

    @Transactional
    public void deleteZone(Long userId, Long zoneId) {
        Rider rider = requireRiderByUserId(userId);
        RiderDeliveryZone zone = zoneRepository.findByIdAndRiderId(zoneId, rider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Rider delivery zone not found."));
        zoneRepository.delete(zone);
    }

    @Transactional(readOnly = true)
    public RiderEarningsSummaryResponse getEarningsSummary(Long userId, Pageable pageable) {
        Rider rider = requireRiderByUserId(userId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startOfDay = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal daily = earningRepository.sumEarningsSince(rider.getId(), startOfDay);
        BigDecimal monthly = earningRepository.sumEarningsSince(rider.getId(), startOfMonth);
        BigDecimal total = earningRepository.sumTotalEarnings(rider.getId());
        long todayCount = earningRepository.countByRiderIdAndEarnedAtGreaterThanEqual(rider.getId(), startOfDay);
        long monthCount = earningRepository.countByRiderIdAndEarnedAtGreaterThanEqual(rider.getId(), startOfMonth);
        long totalCount = earningRepository.countByRiderId(rider.getId());

        Page<RiderEarning> recent = earningRepository.findByRiderIdOrderByEarnedAtDesc(
                rider.getId(), pageable != null ? pageable : PageRequest.of(0, 20)
        );
        return new RiderEarningsSummaryResponse(
                daily,
                monthly,
                total,
                todayCount,
                monthCount,
                totalCount,
                recent.getContent().stream().map(RiderEarningResponse::from).toList()
        );
    }

    // Admin Operations

    @Transactional(readOnly = true)
    public Page<RiderProfileResponse> listRidersForAdmin(
            VerificationStatus verificationStatus,
            RiderAvailabilityStatus availabilityStatus,
            String city,
            String query,
            Pageable pageable) {
        return riderRepository.findForAdmin(verificationStatus, availabilityStatus, city, query, pageable)
                .map(this::buildProfileResponse);
    }

    @Transactional(readOnly = true)
    public RiderProfileResponse getRiderForAdmin(Long riderId) {
        Rider rider = riderRepository.findById(riderId)
                .orElseThrow(() -> new ResourceNotFoundException("Rider not found."));
        return buildProfileResponse(rider);
    }

    @Transactional
    public RiderProfileResponse verifyRider(Long adminUserId, Long riderId, AdminVerifyRiderRequest request) {
        Rider rider = riderRepository.findById(riderId)
                .orElseThrow(() -> new ResourceNotFoundException("Rider not found."));
        User user = rider.getUser();
        Instant now = Instant.now();

        if (Boolean.TRUE.equals(request.approved())) {
            rider.setVerificationStatus(VerificationStatus.APPROVED);
            rider.setRejectionReason(null);
            rider.setVerifiedAt(now);
            rider.setVerifiedBy(adminUserId);
            if (request.baseFeePerDelivery() != null) {
                rider.setBaseFeePerDelivery(request.baseFeePerDelivery());
            }
            if (request.maxConcurrentDeliveries() != null) {
                rider.setMaxConcurrentDeliveries(request.maxConcurrentDeliveries());
            }
            user.setAccountStatus(AccountStatus.ACTIVE);
        } else {
            if (request.notes() == null || request.notes().isBlank()) {
                throw new BadRequestException("A rejection reason is required when rejecting rider verification.");
            }
            rider.setVerificationStatus(VerificationStatus.REJECTED);
            rider.setRejectionReason(request.notes().trim());
            rider.setAvailabilityStatus(RiderAvailabilityStatus.OFFLINE);
            user.setAccountStatus(AccountStatus.REJECTED);
        }

        userRepository.save(user);
        riderRepository.save(rider);
        return buildProfileResponse(rider);
    }

    @Transactional
    public RiderProfileResponse updateRiderStatus(Long riderId, AdminRiderStatusRequest request) {
        Rider rider = riderRepository.findById(riderId)
                .orElseThrow(() -> new ResourceNotFoundException("Rider not found."));
        User user = rider.getUser();
        if (Boolean.TRUE.equals(request.suspended())) {
            rider.setVerificationStatus(VerificationStatus.SUSPENDED);
            rider.setAvailabilityStatus(RiderAvailabilityStatus.OFFLINE);
            if (request.reason() != null && !request.reason().isBlank()) {
                rider.setRejectionReason(request.reason().trim());
            }
            user.setAccountStatus(AccountStatus.SUSPENDED);
        } else {
            if (rider.getVerifiedAt() == null) {
                throw new BusinessRuleException("Rider must pass initial verification before reactivation.");
            }
            rider.setVerificationStatus(VerificationStatus.APPROVED);
            rider.setRejectionReason(null);
            user.setAccountStatus(AccountStatus.ACTIVE);
        }
        userRepository.save(user);
        riderRepository.save(rider);
        return buildProfileResponse(rider);
    }

    public RiderProfileResponse buildProfileResponse(Rider rider) {
        long activeCount = deliveryRepository.countByAssignedRiderIdAndStatusIn(
                rider.getId(), ACTIVE_WORKLOAD_STATUSES
        );
        List<RiderDeliveryZone> zones = zoneRepository.findByRiderIdOrderByPriorityOrderAscIdAsc(rider.getId());
        return RiderProfileResponse.from(rider, activeCount, zones);
    }

    private static String extractFilename(String urlPath) {
        int idx = urlPath.lastIndexOf('/');
        return idx >= 0 ? urlPath.substring(idx + 1) : urlPath;
    }
}
