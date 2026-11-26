package com.medexa.rider.dto;

import com.medexa.pharmacy.VerificationStatus;
import com.medexa.rider.EarningStatus;
import com.medexa.rider.Rider;
import com.medexa.rider.RiderAvailabilityStatus;
import com.medexa.rider.RiderDeliveryZone;
import com.medexa.rider.RiderEarning;
import com.medexa.rider.VehicleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class RiderDtos {

    private RiderDtos() {
    }

    public record UpdateRiderProfileRequest(
            @NotBlank @Size(max = 30) String phoneNumber,
            @NotNull VehicleType vehicleType,
            @NotBlank @Size(max = 60) String vehicleRegistrationNumber,
            @Size(max = 120) String vehicleModel,
            @NotBlank @Size(max = 100) String serviceCity,
            @Size(max = 50) String identityDocumentType,
            @Size(max = 100) String identityDocumentNumber,
            @NotBlank @Size(max = 100) String drivingLicenseNumber,
            LocalDate drivingLicenseExpiry,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal currentLatitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal currentLongitude,
            @Min(1) @Max(5) Integer maxConcurrentDeliveries
    ) {
    }

    public record UpdateRiderAvailabilityRequest(
            @NotNull RiderAvailabilityStatus availabilityStatus,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal currentLatitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal currentLongitude
    ) {
    }

    public record UpdateRiderLocationRequest(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude
    ) {
    }

    public record RiderDeliveryZoneRequest(
            @NotBlank @Size(max = 120) String zoneName,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 20) String postalCode,
            @Min(1) @Max(20) Integer priorityOrder,
            Boolean active
    ) {
    }

    public record RiderZoneStatusRequest(
            @NotNull Boolean active
    ) {
    }

    public record AdminVerifyRiderRequest(
            @NotNull Boolean approved,
            @Size(max = 500) String notes,
            @DecimalMin("10.00") @DecimalMax("500.00") BigDecimal baseFeePerDelivery,
            @Min(1) @Max(5) Integer maxConcurrentDeliveries
    ) {
    }

    public record AdminRiderStatusRequest(
            @NotNull Boolean suspended,
            @Size(max = 500) String reason
    ) {
    }

    public record RiderDeliveryZoneResponse(
            Long id,
            String zoneName,
            String city,
            String postalCode,
            int priorityOrder,
            boolean active
    ) {
        public static RiderDeliveryZoneResponse from(RiderDeliveryZone z) {
            return new RiderDeliveryZoneResponse(
                    z.getId(),
                    z.getZoneName(),
                    z.getCity(),
                    z.getPostalCode(),
                    z.getPriorityOrder(),
                    z.isActive()
            );
        }
    }

    public record RiderProfileResponse(
            Long id,
            Long userId,
            String fullName,
            String email,
            String phoneNumber,
            VehicleType vehicleType,
            String vehicleRegistrationNumber,
            String vehicleModel,
            String drivingLicenseNumber,
            LocalDate drivingLicenseExpiry,
            boolean hasDrivingLicenseDocument,
            String identityDocumentType,
            String identityDocumentNumber,
            boolean hasIdentityDocument,
            String serviceCity,
            VerificationStatus verificationStatus,
            RiderAvailabilityStatus availabilityStatus,
            BigDecimal currentLatitude,
            BigDecimal currentLongitude,
            Instant locationUpdatedAt,
            int maxConcurrentDeliveries,
            BigDecimal baseFeePerDelivery,
            String rejectionReason,
            Instant verifiedAt,
            long activeDeliveryCount,
            List<RiderDeliveryZoneResponse> deliveryZones
    ) {
        public static RiderProfileResponse from(
                Rider r,
                long activeDeliveryCount,
                List<RiderDeliveryZone> zones) {
            return new RiderProfileResponse(
                    r.getId(),
                    r.getUser().getId(),
                    r.getUser().getFullName(),
                    r.getUser().getEmail(),
                    r.getPhoneNumber(),
                    r.getVehicleType(),
                    r.getVehicleRegistrationNumber(),
                    r.getVehicleModel(),
                    r.getDrivingLicenseNumber(),
                    r.getDrivingLicenseExpiry(),
                    r.getDrivingLicenseUrl() != null && !r.getDrivingLicenseUrl().isBlank(),
                    r.getIdentityDocumentType(),
                    r.getIdentityDocumentNumber(),
                    r.getIdentityDocumentUrl() != null && !r.getIdentityDocumentUrl().isBlank(),
                    r.getServiceCity(),
                    r.getVerificationStatus(),
                    r.getAvailabilityStatus(),
                    r.getCurrentLatitude(),
                    r.getCurrentLongitude(),
                    r.getLocationUpdatedAt(),
                    r.getMaxConcurrentDeliveries(),
                    r.getBaseFeePerDelivery(),
                    r.getRejectionReason(),
                    r.getVerifiedAt(),
                    activeDeliveryCount,
                    zones.stream().map(RiderDeliveryZoneResponse::from).toList()
            );
        }
    }

    public record RiderEarningResponse(
            Long id,
            String earningReference,
            Long deliveryId,
            String deliveryReference,
            Long orderId,
            String orderNumber,
            BigDecimal baseFee,
            BigDecimal distanceBonus,
            BigDecimal incentiveAmount,
            BigDecimal totalEarning,
            String currency,
            EarningStatus status,
            Instant earnedAt
    ) {
        public static RiderEarningResponse from(RiderEarning e) {
            return new RiderEarningResponse(
                    e.getId(),
                    e.getEarningReference(),
                    e.getDelivery().getId(),
                    e.getDelivery().getDeliveryReference(),
                    e.getOrder().getId(),
                    e.getOrder().getOrderNumber(),
                    e.getBaseFee(),
                    e.getDistanceBonus(),
                    e.getIncentiveAmount(),
                    e.getTotalEarning(),
                    e.getCurrency(),
                    e.getStatus(),
                    e.getEarnedAt()
            );
        }
    }

    public record RiderEarningsSummaryResponse(
            BigDecimal dailyEarnings,
            BigDecimal monthlyEarnings,
            BigDecimal totalEarnings,
            long todayDeliveries,
            long monthDeliveries,
            long totalDeliveries,
            List<RiderEarningResponse> recentEarnings
    ) {
    }
}
