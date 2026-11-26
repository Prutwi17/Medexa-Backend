package com.medexa.pharmacy.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.pharmacy.dto.PharmacyDtos.PublicPharmacyResponse;
import com.medexa.pharmacy.service.PharmacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/pharmacies")
@RequiredArgsConstructor
@Tag(name = "Public Pharmacies", description = "Verified partner pharmacy directory")
public class PublicPharmacyController {

    private final PharmacyService pharmacyService;

    @GetMapping
    @Operation(summary = "List verified and active partner pharmacies")
    public ApiResponse<List<PublicPharmacyResponse>> listPharmacies(
            @RequestParam(required = false) String q,
            Pageable pageable) {
        Page<PublicPharmacyResponse> page = pharmacyService.listPublicPharmacies(q, pageable);
        return ApiResponse.paged("Verified pharmacies fetched.", page);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get public profile for a verified pharmacy")
    public ApiResponse<PublicPharmacyResponse> getPharmacy(@PathVariable String slug) {
        return ApiResponse.ok(pharmacyService.getPublicPharmacyBySlug(slug));
    }
}
