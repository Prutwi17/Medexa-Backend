package com.medexa.address.service;

import com.medexa.address.CustomerAddress;
import com.medexa.address.CustomerAddressRepository;
import com.medexa.address.dto.AddressDtos.CustomerAddressRequest;
import com.medexa.address.dto.AddressDtos.CustomerAddressResponse;
import com.medexa.address.dto.AddressDtos.DeliveryZoneCheckResponse;
import com.medexa.address.dto.AddressDtos.ServiceablePharmacyZoneResponse;
import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.pharmacy.PharmacyDeliveryZone;
import com.medexa.pharmacy.PharmacyDeliveryZoneRepository;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerAddressService {

    private final CustomerAddressRepository addressRepository;
    private final PharmacyDeliveryZoneRepository deliveryZoneRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CustomerAddressResponse> listAddresses(Long customerId) {
        return addressRepository.findByCustomerIdOrderByDefaultAddressDescUpdatedAtDesc(customerId)
                .stream()
                .map(addr -> CustomerAddressResponse.from(
                        addr,
                        deliveryZoneRepository.findServiceableZonesByPostalCode(addr.getPostalCode())
                ))
                .toList();
    }

    @Transactional
    public CustomerAddressResponse addAddress(Long customerId, CustomerAddressRequest request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));

        boolean existingEmpty = addressRepository.countByCustomerId(customerId) == 0;
        boolean makeDefault = existingEmpty || Boolean.TRUE.equals(request.defaultAddress());
        if (makeDefault) {
            clearExistingDefault(customerId);
        }

        CustomerAddress address = CustomerAddress.builder()
                .customer(customer)
                .label(request.label().trim())
                .recipientName(request.recipientName().trim())
                .recipientPhone(request.recipientPhone().trim())
                .addressLine1(request.addressLine1().trim())
                .addressLine2(trimToNull(request.addressLine2()))
                .landmark(trimToNull(request.landmark()))
                .city(request.city().trim())
                .state(request.state().trim())
                .postalCode(request.postalCode().trim())
                .country(request.country() != null && !request.country().isBlank()
                        ? request.country().trim()
                        : "India")
                .defaultAddress(makeDefault)
                .build();

        addressRepository.save(address);
        List<PharmacyDeliveryZone> zones = deliveryZoneRepository.findServiceableZonesByPostalCode(address.getPostalCode());
        return CustomerAddressResponse.from(address, zones);
    }

    @Transactional
    public CustomerAddressResponse updateAddress(
            Long customerId,
            Long addressId,
            CustomerAddressRequest request) {
        CustomerAddress address = addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery address not found."));

        if (Boolean.TRUE.equals(request.defaultAddress()) && !address.isDefaultAddress()) {
            clearExistingDefault(customerId);
            address.setDefaultAddress(true);
        }

        address.setLabel(request.label().trim());
        address.setRecipientName(request.recipientName().trim());
        address.setRecipientPhone(request.recipientPhone().trim());
        address.setAddressLine1(request.addressLine1().trim());
        address.setAddressLine2(trimToNull(request.addressLine2()));
        address.setLandmark(trimToNull(request.landmark()));
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setPostalCode(request.postalCode().trim());
        if (request.country() != null && !request.country().isBlank()) {
            address.setCountry(request.country().trim());
        }

        addressRepository.save(address);
        List<PharmacyDeliveryZone> zones = deliveryZoneRepository.findServiceableZonesByPostalCode(address.getPostalCode());
        return CustomerAddressResponse.from(address, zones);
    }

    @Transactional
    public CustomerAddressResponse setDefaultAddress(Long customerId, Long addressId) {
        CustomerAddress target = addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery address not found."));
        clearExistingDefault(customerId);
        target.setDefaultAddress(true);
        addressRepository.save(target);

        List<PharmacyDeliveryZone> zones = deliveryZoneRepository.findServiceableZonesByPostalCode(target.getPostalCode());
        return CustomerAddressResponse.from(target, zones);
    }

    @Transactional
    public void deleteAddress(Long customerId, Long addressId) {
        CustomerAddress target = addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery address not found."));
        boolean wasDefault = target.isDefaultAddress();
        addressRepository.delete(target);
        addressRepository.flush();

        if (wasDefault) {
            List<CustomerAddress> remaining = addressRepository
                    .findByCustomerIdOrderByDefaultAddressDescUpdatedAtDesc(customerId);
            if (!remaining.isEmpty()) {
                CustomerAddress nextDefault = remaining.get(0);
                nextDefault.setDefaultAddress(true);
                addressRepository.save(nextDefault);
            }
        }
    }

    @Transactional(readOnly = true)
    public DeliveryZoneCheckResponse checkPostalServiceability(String postalCode) {
        if (postalCode == null || postalCode.isBlank()) {
            throw new BadRequestException("Postal code is required to check delivery availability.");
        }
        String normalized = postalCode.trim();
        List<ServiceablePharmacyZoneResponse> zones = deliveryZoneRepository
                .findServiceableZonesByPostalCode(normalized)
                .stream()
                .map(ServiceablePharmacyZoneResponse::from)
                .toList();
        return new DeliveryZoneCheckResponse(normalized, !zones.isEmpty(), zones.size(), zones);
    }

    @Transactional(readOnly = true)
    public DeliveryZoneCheckResponse checkAddressServiceability(Long customerId, Long addressId) {
        CustomerAddress address = addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery address not found."));
        return checkPostalServiceability(address.getPostalCode());
    }

    private void clearExistingDefault(Long customerId) {
        addressRepository.findByCustomerIdAndDefaultAddressTrue(customerId)
                .ifPresent(existing -> {
                    existing.setDefaultAddress(false);
                    addressRepository.save(existing);
                });
    }

    private String trimToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
