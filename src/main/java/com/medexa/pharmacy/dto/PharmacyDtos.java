package com.medexa.pharmacy.dto;

import com.medexa.pharmacy.Pharmacy;
import com.medexa.pharmacy.PharmacyDeliveryZone;
import com.medexa.pharmacy.PharmacyLicense;
import com.medexa.pharmacy.VerificationStatus;
import com.medexa.user.AccountStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PharmacyDtos {

    private PharmacyDtos() {
    }

    public record UpdatePharmacyProfileRequest(
            @NotBlank(message = "Pharmacy name is required.")
            @Size(min = 2, max = 200)
            String pharmacyName,

            @NotBlank(message = "Contact phone is required.")
            @Pattern(regexp = "^[+0-9\\s-]{7,20}$", message = "Provide a valid phone number.")
            String contactPhone,

            @NotBlank(message = "Address is required.")
            @Size(max = 300)
            String addressLine,

            @NotBlank(message = "City is required.")
            @Size(max = 100)
            String city,

            @NotBlank(message = "State is required.")
            @Size(max = 100)
            String state,

            @NotBlank(message = "Postal code is required.")
            @Size(max = 20)
            String postalCode,

            Boolean acceptingOrders
    ) {
    }

    public record SubmitPharmacyLicenseRequest(
            @NotBlank(message = "Drug license number is required.")
            @Size(max = 120)
            String licenseNumber,

            @NotBlank(message = "Issuing authority is required.")
            @Size(max = 180)
            String issuingAuthority,

            @NotBlank(message = "Pharmacist name is required.")
            @Size(max = 150)
            String pharmacistName,

            @NotBlank(message = "Pharmacist registration number is required.")
            @Size(max = 120)
            String pharmacistRegistrationNumber,

            @NotNull(message = "Expiry date is required.")
            @Future(message = "License expiry date must be in the future.")
            LocalDate expiryDate,

            @Size(max = 500)
            String documentUrl
    ) {
    }

    public record DeliveryZoneRequest(
            @NotBlank(message = "Zone name is required.")
            @Size(max = 120)
            String zoneName,

            @NotBlank(message = "Postal code is required.")
            @Size(max = 20)
            String postalCode,

            @NotBlank(message = "City is required.")
            @Size(max = 100)
            String city,

            @NotNull(message = "Delivery fee is required.")
            @DecimalMin(value = "0.00", message = "Delivery fee cannot be negative.")
            BigDecimal deliveryFee,

            @NotNull(message = "Minimum order amount is required.")
            @DecimalMin(value = "0.00", message = "Minimum order amount cannot be negative.")
            BigDecimal minOrderAmount,

            @NotNull(message = "Estimated delivery minutes is required.")
            @Min(value = 10, message = "Estimated delivery minutes must be at least 10.")
            @Max(value = 1440, message = "Estimated delivery minutes cannot exceed 1440.")
            Integer estimatedDeliveryMinutes
    ) {
    }

    public record DeliveryZoneStatusRequest(@NotNull Boolean active) {
    }

    public record PharmacyLicenseResponse(
            Long id,
            String licenseNumber,
            String issuingAuthority,
            String pharmacistName,
            String pharmacistRegistrationNumber,
            LocalDate expiryDate,
            String documentUrl,
            VerificationStatus verificationStatus,
            Instant reviewedAt,
            String reviewNotes,
            Instant createdAt
    ) {
        public static PharmacyLicenseResponse from(PharmacyLicense license) {
            return new PharmacyLicenseResponse(
                    license.getId(),
                    license.getLicenseNumber(),
                    license.getIssuingAuthority(),
                    license.getPharmacistName(),
                    license.getPharmacistRegistrationNumber(),
                    license.getExpiryDate(),
                    license.getDocumentUrl(),
                    license.getVerificationStatus(),
                    license.getReviewedAt(),
                    license.getReviewNotes(),
                    license.getCreatedAt()
            );
        }
    }

    public record DeliveryZoneResponse(
            Long id,
            String zoneName,
            String postalCode,
            String city,
            BigDecimal deliveryFee,
            BigDecimal minOrderAmount,
            Integer estimatedDeliveryMinutes,
            boolean active
    ) {
        public static DeliveryZoneResponse from(PharmacyDeliveryZone zone) {
            return new DeliveryZoneResponse(
                    zone.getId(),
                    zone.getZoneName(),
                    zone.getPostalCode(),
                    zone.getCity(),
                    zone.getDeliveryFee(),
                    zone.getMinOrderAmount(),
                    zone.getEstimatedDeliveryMinutes(),
                    zone.isActive()
            );
        }
    }

    public record PharmacyProfileResponse(
            Long id,
            Long userId,
            String ownerName,
            String ownerEmail,
            AccountStatus accountStatus,
            String pharmacyName,
            String slug,
            String contactPhone,
            String addressLine,
            String city,
            String state,
            String postalCode,
            VerificationStatus verificationStatus,
            String rejectionReason,
            boolean acceptingOrders,
            Instant verifiedAt,
            List<PharmacyLicenseResponse> licenses,
            List<DeliveryZoneResponse> deliveryZones
    ) {
        public static PharmacyProfileResponse from(
                Pharmacy pharmacy,
                List<PharmacyLicense> licenses,
                List<PharmacyDeliveryZone> zones) {
            return new PharmacyProfileResponse(
                    pharmacy.getId(),
                    pharmacy.getUser().getId(),
                    pharmacy.getUser().getFullName(),
                    pharmacy.getUser().getEmail(),
                    pharmacy.getUser().getAccountStatus(),
                    pharmacy.getPharmacyName(),
                    pharmacy.getSlug(),
                    pharmacy.getContactPhone(),
                    pharmacy.getAddressLine(),
                    pharmacy.getCity(),
                    pharmacy.getState(),
                    pharmacy.getPostalCode(),
                    pharmacy.getVerificationStatus(),
                    pharmacy.getRejectionReason(),
                    pharmacy.isAcceptingOrders(),
                    pharmacy.getVerifiedAt(),
                    licenses.stream().map(PharmacyLicenseResponse::from).toList(),
                    zones.stream().map(DeliveryZoneResponse::from).toList()
            );
        }
    }

    public record PublicPharmacyResponse(
            Long id,
            String pharmacyName,
            String slug,
            String city,
            String state,
            String postalCode,
            boolean acceptingOrders,
            List<DeliveryZoneResponse> deliveryZones
    ) {
        public static PublicPharmacyResponse from(Pharmacy pharmacy, List<PharmacyDeliveryZone> activeZones) {
            return new PublicPharmacyResponse(
                    pharmacy.getId(),
                    pharmacy.getPharmacyName(),
                    pharmacy.getSlug(),
                    pharmacy.getCity(),
                    pharmacy.getState(),
                    pharmacy.getPostalCode(),
                    pharmacy.isAcceptingOrders(),
                    activeZones.stream().map(DeliveryZoneResponse::from).toList()
            );
        }
    }
}
