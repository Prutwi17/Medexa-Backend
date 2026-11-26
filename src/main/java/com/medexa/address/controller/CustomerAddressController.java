package com.medexa.address.controller;

import com.medexa.address.dto.AddressDtos.CustomerAddressRequest;
import com.medexa.address.dto.AddressDtos.CustomerAddressResponse;
import com.medexa.address.dto.AddressDtos.DeliveryZoneCheckResponse;
import com.medexa.address.service.CustomerAddressService;
import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/addresses")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Customer Addresses", description = "Multiple delivery addresses and zone validation")
public class CustomerAddressController {

    private final CustomerAddressService addressService;

    @GetMapping
    @Operation(summary = "List saved delivery addresses with zone serviceability")
    public ApiResponse<List<CustomerAddressResponse>> list(
            @AuthenticationPrincipal MedexaUserPrincipal principal) {
        return ApiResponse.ok(addressService.listAddresses(principal.getUserId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new delivery address")
    public ApiResponse<CustomerAddressResponse> create(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody CustomerAddressRequest request) {
        return ApiResponse.ok("Delivery address saved.", addressService.addAddress(principal.getUserId(), request));
    }

    @PutMapping("/{addressId}")
    @Operation(summary = "Update a saved delivery address")
    public ApiResponse<CustomerAddressResponse> update(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long addressId,
            @Valid @RequestBody CustomerAddressRequest request) {
        return ApiResponse.ok(
                "Delivery address updated.",
                addressService.updateAddress(principal.getUserId(), addressId, request)
        );
    }

    @PatchMapping("/{addressId}/default")
    @Operation(summary = "Mark a delivery address as default")
    public ApiResponse<CustomerAddressResponse> setDefault(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long addressId) {
        return ApiResponse.ok(
                "Default delivery address updated.",
                addressService.setDefaultAddress(principal.getUserId(), addressId)
        );
    }

    @GetMapping("/{addressId}/serviceability")
    @Operation(summary = "Validate pharmacy delivery zone coverage for an address")
    public ApiResponse<DeliveryZoneCheckResponse> validateServiceability(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long addressId) {
        return ApiResponse.ok(addressService.checkAddressServiceability(principal.getUserId(), addressId));
    }

    @DeleteMapping("/{addressId}")
    @Operation(summary = "Delete a saved delivery address")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long addressId) {
        addressService.deleteAddress(principal.getUserId(), addressId);
        return ApiResponse.ok("Delivery address removed.", null);
    }
}
