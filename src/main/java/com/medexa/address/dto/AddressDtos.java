package com.medexa.address.dto;

import com.medexa.address.CustomerAddress;
import com.medexa.pharmacy.PharmacyDeliveryZone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class AddressDtos {

    private AddressDtos() {
    }

    public record CustomerAddressRequest(
            @NotBlank(message = "Address label is required.")
            @Size(min = 2, max = 60)
            String label,

            @NotBlank(message = "Recipient name is required.")
            @Size(min = 2, max = 150)
            String recipientName,

            @NotBlank(message = "Recipient phone is required.")
            @Size(min = 6, max = 30)
            String recipientPhone,

            @NotBlank(message = "Address line 1 is required.")
            @Size(min = 4, max = 250)
            String addressLine1,

            @Size(max = 250)
            String addressLine2,

            @Size(max = 150)
            String landmark,

            @NotBlank(message = "City is required.")
            @Size(min = 2, max = 100)
            String city,

            @NotBlank(message = "State is required.")
            @Size(min = 2, max = 100)
            String state,

            @NotBlank(message = "Postal code is required.")
            @Size(min = 4, max = 20)
            String postalCode,

            @Size(max = 80)
            String country,

            Boolean defaultAddress
    ) {
    }

    public record ServiceablePharmacyZoneResponse(
            Long pharmacyId,
            String pharmacyName,
            String pharmacySlug,
            String pharmacyCity,
            String zoneName,
            String postalCode,
            BigDecimal deliveryFee,
            BigDecimal minOrderAmount,
            Integer estimatedDeliveryMinutes
    ) {
        public static ServiceablePharmacyZoneResponse from(PharmacyDeliveryZone zone) {
            return new ServiceablePharmacyZoneResponse(
                    zone.getPharmacy().getId(),
                    zone.getPharmacy().getPharmacyName(),
                    zone.getPharmacy().getSlug(),
                    zone.getPharmacy().getCity(),
                    zone.getZoneName(),
                    zone.getPostalCode(),
                    zone.getDeliveryFee(),
                    zone.getMinOrderAmount(),
                    zone.getEstimatedDeliveryMinutes()
            );
        }
    }

    public record DeliveryZoneCheckResponse(
            String postalCode,
            boolean serviceable,
            int pharmacyCount,
            List<ServiceablePharmacyZoneResponse> zones
    ) {
    }

    public record CustomerAddressResponse(
            Long id,
            String label,
            String recipientName,
            String recipientPhone,
            String addressLine1,
            String addressLine2,
            String landmark,
            String city,
            String state,
            String postalCode,
            String country,
            boolean defaultAddress,
            boolean serviceable,
            int serviceablePharmacyCount,
            List<ServiceablePharmacyZoneResponse> serviceableZones
    ) {
        public static CustomerAddressResponse from(
                CustomerAddress address,
                List<PharmacyDeliveryZone> zones) {
            List<ServiceablePharmacyZoneResponse> mappedZones = zones.stream()
                    .map(ServiceablePharmacyZoneResponse::from)
                    .toList();
            return new CustomerAddressResponse(
                    address.getId(),
                    address.getLabel(),
                    address.getRecipientName(),
                    address.getRecipientPhone(),
                    address.getAddressLine1(),
                    address.getAddressLine2(),
                    address.getLandmark(),
                    address.getCity(),
                    address.getState(),
                    address.getPostalCode(),
                    address.getCountry(),
                    address.isDefaultAddress(),
                    !mappedZones.isEmpty(),
                    mappedZones.size(),
                    mappedZones
            );
        }
    }
}
