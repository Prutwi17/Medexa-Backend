package com.medexa.prescription.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.prescription.PrescriptionStatus;
import com.medexa.prescription.dto.PrescriptionDtos.PrescriptionResponse;
import com.medexa.prescription.service.PrescriptionService;
import com.medexa.prescription.service.PrescriptionService.PrescriptionDocumentPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/prescriptions")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Admin Prescription Oversight", description = "Platform prescription compliance audit view")
public class AdminPrescriptionController {

    private final PrescriptionService prescriptionService;

    @GetMapping
    @Operation(summary = "List prescriptions across the platform for compliance auditing")
    public ApiResponse<List<PrescriptionResponse>> list(
            @RequestParam(required = false) PrescriptionStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PrescriptionResponse> page = prescriptionService.listAdminPrescriptions(status, pageable);
        return ApiResponse.paged("Platform prescriptions fetched.", page);
    }

    @GetMapping("/{prescriptionId}")
    @Operation(summary = "Inspect prescription details and audit log")
    public ApiResponse<PrescriptionResponse> getDetail(@PathVariable Long prescriptionId) {
        return ApiResponse.ok(prescriptionService.getAdminPrescription(prescriptionId));
    }

    @GetMapping("/{prescriptionId}/document")
    @Operation(summary = "Download or view prescription file for compliance review")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long prescriptionId) {
        PrescriptionDocumentPayload payload = prescriptionService.loadDocumentForAdmin(prescriptionId);
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
