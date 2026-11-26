package com.medexa.address.controller;

import com.medexa.address.dto.AddressDtos.DeliveryZoneCheckResponse;
import com.medexa.address.service.CustomerAddressService;
import com.medexa.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/delivery-zones")
@RequiredArgsConstructor
@Tag(name = "Public Delivery Zones", description = "Check verified pharmacy delivery serviceability by postal code")
public class PublicDeliveryZoneController {

    private final CustomerAddressService addressService;

    @GetMapping("/check")
    @Operation(summary = "Check verified pharmacy delivery availability for a postal code")
    public ApiResponse<DeliveryZoneCheckResponse> check(@RequestParam String postalCode) {
        return ApiResponse.ok(addressService.checkPostalServiceability(postalCode));
    }
}
