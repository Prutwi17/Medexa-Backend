package com.medexa.admin.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.medicine.MedicineApprovalStatus;
import com.medexa.medicine.dto.MedicineDtos.ClassificationRuleResponse;
import com.medexa.medicine.dto.MedicineDtos.ClassificationRuleUpdateRequest;
import com.medexa.medicine.dto.MedicineDtos.MedicineReviewRequest;
import com.medexa.medicine.dto.MedicineDtos.PharmacyMedicineResponse;
import com.medexa.medicine.service.MedicineClassificationService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/medicines")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Admin Medicines", description = "Medicine approval and classification rules")
public class AdminMedicineController {

    private final MedicineService medicineService;
    private final MedicineClassificationService classificationService;

    @GetMapping
    @Operation(summary = "List medicine listings in approval queue")
    public ApiResponse<List<PharmacyMedicineResponse>> list(
            @RequestParam(required = false) MedicineApprovalStatus status,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        Page<PharmacyMedicineResponse> page = medicineService.listAdminQueue(status, q, pageable);
        return ApiResponse.paged("Medicine approval queue fetched.", page);
    }

    @PostMapping("/{medicineId}/review")
    @Operation(summary = "Approve or reject a medicine listing")
    public ApiResponse<PharmacyMedicineResponse> review(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long medicineId,
            @Valid @RequestBody MedicineReviewRequest request) {
        return ApiResponse.ok(
                "Medicine review decision saved.",
                medicineService.reviewMedicine(principal.getUserId(), medicineId, request)
        );
    }

    @GetMapping("/classifications")
    @Operation(summary = "List configurable medicine classification rules")
    public ApiResponse<List<ClassificationRuleResponse>> listClassifications() {
        return ApiResponse.ok(classificationService.listAllRules());
    }

    @PutMapping("/classifications/{code}")
    @Operation(summary = "Update medicine classification rule configuration")
    public ApiResponse<ClassificationRuleResponse> updateClassification(
            @PathVariable String code,
            @Valid @RequestBody ClassificationRuleUpdateRequest request) {
        return ApiResponse.ok(
                "Classification rule updated.",
                classificationService.updateRule(code, request)
        );
    }
}
