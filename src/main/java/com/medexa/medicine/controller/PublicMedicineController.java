package com.medexa.medicine.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.medicine.dto.MedicineDtos.PublicMedicineResponse;
import com.medexa.medicine.service.MedicineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/medicines")
@RequiredArgsConstructor
@Tag(name = "Public Medicine Catalog", description = "Approved customer-facing medicine catalog")
public class PublicMedicineController {

    private final MedicineService medicineService;

    @GetMapping
    @Operation(summary = "Search and filter approved medicines from verified active pharmacies")
    public ApiResponse<List<PublicMedicineResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long pharmacyId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @RequestParam(required = false) Boolean prescriptionRequired,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PublicMedicineResponse> page = medicineService.searchPublicCatalog(
                q, categoryId, pharmacyId, minPrice, maxPrice, inStockOnly, prescriptionRequired, pageable
        );
        return ApiResponse.paged("Catalog medicines fetched.", page);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get approved medicine details by slug")
    public ApiResponse<PublicMedicineResponse> getBySlug(@PathVariable String slug) {
        return ApiResponse.ok(medicineService.getPublicMedicineDetail(slug));
    }
}
