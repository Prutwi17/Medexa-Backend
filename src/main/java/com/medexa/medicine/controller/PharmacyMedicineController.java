package com.medexa.medicine.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.medicine.MedicineApprovalStatus;
import com.medexa.medicine.dto.MedicineDtos.MedicineImageResponse;
import com.medexa.medicine.dto.MedicineDtos.MedicineUpsertRequest;
import com.medexa.medicine.dto.MedicineDtos.PharmacyMedicineResponse;
import com.medexa.medicine.service.MedicineService;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/pharmacy/medicines")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Pharmacy Medicines", description = "Pharmacy medicine listings and image uploads")
public class PharmacyMedicineController {

    private final MedicineService medicineService;

    @GetMapping
    @Operation(summary = "List authenticated pharmacy's medicine listings")
    public ApiResponse<List<PharmacyMedicineResponse>> list(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @RequestParam(required = false) MedicineApprovalStatus status,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        Page<PharmacyMedicineResponse> page = medicineService.listOwnMedicines(
                principal.getUserId(), status, q, pageable
        );
        return ApiResponse.paged("Pharmacy medicines fetched.", page);
    }

    @GetMapping("/{medicineId}")
    @Operation(summary = "Get medicine listing owned by authenticated pharmacy")
    public ApiResponse<PharmacyMedicineResponse> getOne(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long medicineId) {
        return ApiResponse.ok(medicineService.getOwnMedicine(principal.getUserId(), medicineId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new medicine listing (requires verified pharmacy)")
    public ApiResponse<PharmacyMedicineResponse> create(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody MedicineUpsertRequest request) {
        return ApiResponse.ok(
                "Medicine submitted for administrator approval.",
                medicineService.createMedicine(principal.getUserId(), request)
        );
    }

    @PutMapping("/{medicineId}")
    @Operation(summary = "Update an existing medicine listing owned by the pharmacy")
    public ApiResponse<PharmacyMedicineResponse> update(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long medicineId,
            @Valid @RequestBody MedicineUpsertRequest request) {
        return ApiResponse.ok(
                "Medicine updated and queued for approval.",
                medicineService.updateMedicine(principal.getUserId(), medicineId, request)
        );
    }

    @DeleteMapping("/{medicineId}")
    @Operation(summary = "Deactivate a medicine listing owned by the pharmacy")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long medicineId) {
        medicineService.deleteMedicine(principal.getUserId(), medicineId);
        return ApiResponse.ok("Medicine listing deactivated.", null);
    }

    @PostMapping(value = "/{medicineId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload medicine product image")
    public ApiResponse<MedicineImageResponse> uploadImage(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long medicineId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean primary,
            @RequestParam(required = false) String altText) {
        return ApiResponse.ok(
                "Medicine image uploaded.",
                medicineService.uploadImage(principal.getUserId(), medicineId, file, primary, altText)
        );
    }

    @DeleteMapping("/{medicineId}/images/{imageId}")
    @Operation(summary = "Remove a medicine product image")
    public ApiResponse<Void> deleteImage(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long medicineId,
            @PathVariable Long imageId) {
        medicineService.deleteImage(principal.getUserId(), medicineId, imageId);
        return ApiResponse.ok("Medicine image deleted.", null);
    }
}
