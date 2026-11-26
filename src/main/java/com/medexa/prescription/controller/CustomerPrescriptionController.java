package com.medexa.prescription.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.config.OpenApiConfig;
import com.medexa.prescription.PrescriptionStatus;
import com.medexa.prescription.dto.PrescriptionDtos.DispatchEligibilityCheckRequest;
import com.medexa.prescription.dto.PrescriptionDtos.DispatchEligibilityResponse;
import com.medexa.prescription.dto.PrescriptionDtos.PrescriptionResponse;
import com.medexa.prescription.dto.PrescriptionDtos.PrescriptionUploadMetadata;
import com.medexa.prescription.service.PrescriptionService;
import com.medexa.prescription.service.PrescriptionService.PrescriptionDocumentPayload;
import com.medexa.security.MedexaUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/customer/prescriptions")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "Customer Prescriptions", description = "Upload, track, and verify prescriptions")
public class CustomerPrescriptionController {

    private final PrescriptionService prescriptionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload prescription PDF or image with clinical metadata")
    public ApiResponse<PrescriptionResponse> upload(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long pharmacyId,
            @RequestParam String patientName,
            @RequestParam(required = false) Integer patientAge,
            @RequestParam String doctorName,
            @RequestParam(required = false) String doctorRegistrationNumber,
            @RequestParam(required = false) String clinicName,
            @RequestParam(required = false) LocalDate issuedDate,
            @RequestParam(required = false) LocalDate validUntil,
            @RequestParam(required = false) String notes) {
        PrescriptionUploadMetadata metadata = new PrescriptionUploadMetadata(
                pharmacyId,
                patientName,
                patientAge,
                doctorName,
                doctorRegistrationNumber,
                clinicName,
                issuedDate,
                validUntil,
                notes
        );
        return ApiResponse.ok(
                "Prescription uploaded and queued for pharmacist review.",
                prescriptionService.uploadPrescription(principal.getUserId(), file, metadata)
        );
    }

    @GetMapping
    @Operation(summary = "List authenticated customer's uploaded prescriptions")
    public ApiResponse<List<PrescriptionResponse>> list(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @RequestParam(required = false) PrescriptionStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PrescriptionResponse> page = prescriptionService
                .listCustomerPrescriptions(principal.getUserId(), status, pageable);
        return ApiResponse.paged("Prescriptions fetched.", page);
    }

    @GetMapping("/{prescriptionId}")
    @Operation(summary = "Get prescription details and audit history for authenticated customer")
    public ApiResponse<PrescriptionResponse> getDetail(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long prescriptionId) {
        return ApiResponse.ok(prescriptionService.getCustomerPrescription(principal.getUserId(), prescriptionId));
    }

    @PostMapping("/{prescriptionId}/cancel")
    @Operation(summary = "Cancel an uploaded prescription")
    public ApiResponse<PrescriptionResponse> cancel(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long prescriptionId) {
        return ApiResponse.ok(
                "Prescription cancelled.",
                prescriptionService.cancelCustomerPrescription(principal.getUserId(), prescriptionId)
        );
    }

    @PostMapping("/validate-dispatch")
    @Operation(summary = "Check dispatch eligibility of a medicine with a prescription")
    public ApiResponse<DispatchEligibilityResponse> validateDispatch(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @Valid @RequestBody DispatchEligibilityCheckRequest request) {
        return ApiResponse.ok(
                prescriptionService.checkDispatchEligibility(
                        principal.getUserId(),
                        request.medicineId(),
                        request.prescriptionId(),
                        false
                )
        );
    }

    @GetMapping("/{prescriptionId}/document")
    @Operation(summary = "Download or view uploaded prescription file (ownership verified)")
    public ResponseEntity<Resource> downloadDocument(
            @AuthenticationPrincipal MedexaUserPrincipal principal,
            @PathVariable Long prescriptionId) {
        PrescriptionDocumentPayload payload = prescriptionService
                .loadDocumentForCustomer(principal.getUserId(), prescriptionId);
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
