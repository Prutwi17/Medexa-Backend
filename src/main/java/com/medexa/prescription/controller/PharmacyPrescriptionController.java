package com.medexa.prescription.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.prescription.PrescriptionStatus;
import com.medexa.prescription.dto.PrescriptionDtos.PrescriptionResponse;
import com.medexa.prescription.dto.PrescriptionDtos.PrescriptionReviewRequest;
import com.medexa.prescription.service.PrescriptionService;
import com.medexa.prescription.service.PrescriptionService.PrescriptionDocumentPayload;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacy/prescriptions")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Pharmacy Prescription Reviews", description = "Pharmacist verification and rejection queue")
public class PharmacyPrescriptionController {

    private final PrescriptionService prescriptionService;

    @GetMapping
    @Operation(summary = "List prescriptions assigned to or reviewable by this verified pharmacy")
    public ApiResponse<List<PrescriptionResponse>> listQueue(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @RequestParam(required = false) PrescriptionStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PrescriptionResponse> page = prescriptionService
                .listPharmacyPrescriptions(principal.getUserId(), status, pageable);
        return ApiResponse.paged("Pharmacy prescription queue fetched.", page);
    }

    @GetMapping("/{prescriptionId}")
    @Operation(summary = "View prescription details and audit log for authorized pharmacist review")
    public ApiResponse<PrescriptionResponse> getDetail(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long prescriptionId) {
        return ApiResponse.ok(prescriptionService.getPharmacyPrescription(principal.getUserId(), prescriptionId));
    }

    @PostMapping("/{prescriptionId}/review")
    @Operation(summary = "Approve or reject a prescription with pharmacist license verification")
    public ApiResponse<PrescriptionResponse> review(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long prescriptionId,
            @Valid @RequestBody PrescriptionReviewRequest request) {
        return ApiResponse.ok(
                "Prescription review decision recorded.",
                prescriptionService.reviewPrescription(principal.getUserId(), prescriptionId, request)
        );
    }

    @GetMapping("/{prescriptionId}/document")
    @Operation(summary = "View customer prescription file as authorized pharmacy personnel")
    public ResponseEntity<Resource> downloadDocument(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long prescriptionId) {
        PrescriptionDocumentPayload payload = prescriptionService
                .loadDocumentForPharmacy(principal.getUserId(), prescriptionId);
        MediaType mediaType = MediaType.parseMediaType(payload.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(payload.originalFilename()).build().toString()
                )
                .body(payload.resource());
    }
}
