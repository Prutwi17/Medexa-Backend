package com.medexa.customer.controller;

import com.medexa.category.dto.CategoryDtos.CategoryResponse;
import com.medexa.category.service.CategoryService;
import com.medexa.common.api.ApiResponse;
import com.medexa.medicine.dto.MedicineDtos.PublicMedicineResponse;
import com.medexa.medicine.service.MedicineService;
import com.medexa.pharmacy.dto.PharmacyDtos.PublicPharmacyResponse;
import com.medexa.pharmacy.service.PharmacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/storefront")
@RequiredArgsConstructor
@Tag(name = "Public Storefront", description = "DB-backed customer home storefront sections")
public class PublicStorefrontController {

    private final CategoryService categoryService;
    private final MedicineService medicineService;
    private final PharmacyService pharmacyService;

    public record StorefrontHomeResponse(
            List<CategoryResponse> categories,
            List<PublicMedicineResponse> popularMedicines,
            List<PublicMedicineResponse> wellnessMedicines,
            List<PublicMedicineResponse> recentMedicines,
            List<PublicPharmacyResponse> verifiedPharmacies
    ) {
    }

    @GetMapping("/home")
    @Operation(summary = "Fetch live categories, popular/wellness/recent approved medicines, and verified pharmacies")
    public ApiResponse<StorefrontHomeResponse> getHome() {
        List<CategoryResponse> categories = categoryService.listActiveCategories();

        List<PublicMedicineResponse> popular = medicineService.searchPublicCatalog(
                null, null, null, null, null, true, null,
                PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "totalAvailableQuantity"))
        ).getContent();

        List<PublicMedicineResponse> wellness = medicineService.searchPublicCatalog(
                null, null, null, null, null, true, false,
                PageRequest.of(0, 8, Sort.by(Sort.Direction.ASC, "sellingPrice"))
        ).getContent();

        List<PublicMedicineResponse> recent = medicineService.searchPublicCatalog(
                null, null, null, null, null, false, null,
                PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        List<PublicPharmacyResponse> pharmacies = pharmacyService.listPublicPharmacies(
                null,
                PageRequest.of(0, 6, Sort.by(Sort.Direction.ASC, "pharmacyName"))
        ).getContent();

        return ApiResponse.ok(new StorefrontHomeResponse(categories, popular, wellness, recent, pharmacies));
    }
}
