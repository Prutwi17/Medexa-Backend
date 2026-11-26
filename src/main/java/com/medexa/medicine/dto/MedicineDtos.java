package com.medexa.medicine.dto;

import com.medexa.medicine.Medicine;
import com.medexa.medicine.MedicineApprovalStatus;
import com.medexa.medicine.MedicineClassificationRule;
import com.medexa.medicine.MedicineImage;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class MedicineDtos {

    private MedicineDtos() {
    }

    public record MedicineUpsertRequest(
            @NotNull(message = "Category is required.")
            Long categoryId,

            @NotBlank(message = "Medicine name is required.")
            @Size(min = 2, max = 200)
            String name,

            @NotBlank(message = "Brand is required.")
            @Size(min = 1, max = 150)
            String brand,

            @NotBlank(message = "Manufacturer is required.")
            @Size(min = 2, max = 180)
            String manufacturer,

            @NotBlank(message = "Composition is required.")
            @Size(min = 2, max = 500)
            String composition,

            @NotBlank(message = "Dosage form is required.")
            @Size(max = 80)
            String dosageForm,

            @NotBlank(message = "Pack size is required.")
            @Size(max = 80)
            String packSize,

            @Size(max = 4000)
            String description,

            @NotNull(message = "MRP price is required.")
            @DecimalMin(value = "0.01", message = "MRP price must be greater than zero.")
            BigDecimal mrpPrice,

            @NotNull(message = "Selling price is required.")
            @DecimalMin(value = "0.01", message = "Selling price must be greater than zero.")
            BigDecimal sellingPrice,

            @NotNull(message = "Prescription requirement flag is required.")
            Boolean prescriptionRequired,

            @Size(max = 40)
            String classificationCode,

            Boolean active
    ) {
    }

    public record ClassificationRuleResponse(
            String classificationCode,
            String displayName,
            boolean prescriptionRequired,
            Integer defaultRxValidityDays,
            Integer maxUnitsPerOrder,
            boolean autoSubstitutionAllowed,
            boolean requiresPharmacistLicenseCheck,
            String warningText,
            boolean active
    ) {
        public static ClassificationRuleResponse from(MedicineClassificationRule rule) {
            return new ClassificationRuleResponse(
                    rule.getClassificationCode(),
                    rule.getDisplayName(),
                    rule.isPrescriptionRequired(),
                    rule.getDefaultRxValidityDays(),
                    rule.getMaxUnitsPerOrder(),
                    rule.isAutoSubstitutionAllowed(),
                    rule.isRequiresPharmacistLicenseCheck(),
                    rule.getWarningText(),
                    rule.isActive()
            );
        }
    }

    public record ClassificationRuleUpdateRequest(
            @NotBlank @Size(max = 120) String displayName,
            @NotNull Boolean prescriptionRequired,
            @NotNull @Min(1) @Max(730) Integer defaultRxValidityDays,
            @NotNull @Min(1) @Max(500) Integer maxUnitsPerOrder,
            Boolean autoSubstitutionAllowed,
            @NotNull Boolean requiresPharmacistLicenseCheck,
            @Size(max = 500) String warningText,
            Boolean active
    ) {
    }

    public record MedicineReviewRequest(
            @NotNull(message = "Approval decision is required.")
            Boolean approved,

            @Size(max = 500)
            String rejectionReason
    ) {
    }

    public record MedicineImageResponse(
            Long id,
            String imageUrl,
            String altText,
            boolean primaryImage,
            Integer displayOrder
    ) {
        public static MedicineImageResponse from(MedicineImage image) {
            return new MedicineImageResponse(
                    image.getId(),
                    image.getImageUrl(),
                    image.getAltText(),
                    image.isPrimaryImage(),
                    image.getDisplayOrder()
            );
        }
    }

    public record PharmacyMedicineResponse(
            Long id,
            Long categoryId,
            String categoryName,
            Long pharmacyId,
            String pharmacyName,
            String name,
            String slug,
            String brand,
            String manufacturer,
            String composition,
            String dosageForm,
            String packSize,
            String description,
            BigDecimal mrpPrice,
            BigDecimal sellingPrice,
            boolean prescriptionRequired,
            String classificationCode,
            MedicineApprovalStatus approvalStatus,
            String rejectionReason,
            Instant reviewedAt,
            boolean active,
            Integer totalAvailableQuantity,
            List<MedicineImageResponse> images,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static PharmacyMedicineResponse from(Medicine m, List<MedicineImage> images) {
            return new PharmacyMedicineResponse(
                    m.getId(),
                    m.getCategory().getId(),
                    m.getCategory().getName(),
                    m.getPharmacy().getId(),
                    m.getPharmacy().getPharmacyName(),
                    m.getName(),
                    m.getSlug(),
                    m.getBrand(),
                    m.getManufacturer(),
                    m.getComposition(),
                    m.getDosageForm(),
                    m.getPackSize(),
                    m.getDescription(),
                    m.getMrpPrice(),
                    m.getSellingPrice(),
                    m.isPrescriptionRequired(),
                    m.getClassificationCode(),
                    m.getApprovalStatus(),
                    m.getRejectionReason(),
                    m.getReviewedAt(),
                    m.isActive(),
                    m.getTotalAvailableQuantity(),
                    images.stream().map(MedicineImageResponse::from).toList(),
                    m.getCreatedAt(),
                    m.getUpdatedAt()
            );
        }
    }

    public record PublicMedicineResponse(
            Long id,
            String name,
            String slug,
            String brand,
            String manufacturer,
            String composition,
            String dosageForm,
            String packSize,
            String description,
            BigDecimal mrpPrice,
            BigDecimal sellingPrice,
            boolean prescriptionRequired,
            String classificationCode,
            String classificationDisplayName,
            boolean autoSubstitutionAllowed,
            Integer maxUnitsPerOrder,
            String classificationWarning,
            boolean inStock,
            Integer availableQuantity,
            Long categoryId,
            String categoryName,
            String categorySlug,
            Long pharmacyId,
            String pharmacyName,
            String pharmacySlug,
            String pharmacyCity,
            String primaryImageUrl,
            List<MedicineImageResponse> images
    ) {
        public static PublicMedicineResponse from(Medicine m, List<MedicineImage> images) {
            return from(m, images, null);
        }

        public static PublicMedicineResponse from(
                Medicine m,
                List<MedicineImage> images,
                MedicineClassificationRule rule) {
            List<MedicineImageResponse> imageDtos = images.stream().map(MedicineImageResponse::from).toList();
            String primaryUrl = imageDtos.stream()
                    .filter(MedicineImageResponse::primaryImage)
                    .map(MedicineImageResponse::imageUrl)
                    .findFirst()
                    .orElse(imageDtos.isEmpty() ? null : imageDtos.get(0).imageUrl());

            int available = m.getTotalAvailableQuantity() != null ? m.getTotalAvailableQuantity() : 0;
            String classCode = m.getClassificationCode() != null ? m.getClassificationCode() : (m.isPrescriptionRequired() ? "SCHEDULE_H" : "OTC");
            String className = rule != null ? rule.getDisplayName() : (m.isPrescriptionRequired() ? "Prescription Medicine" : "Over-the-Counter (OTC)");
            boolean autoSub = rule != null && !m.isPrescriptionRequired() && rule.isAutoSubstitutionAllowed();
            Integer maxUnits = rule != null ? rule.getMaxUnitsPerOrder() : (m.isPrescriptionRequired() ? 30 : 50);
            String warning = rule != null ? rule.getWarningText() : null;

            return new PublicMedicineResponse(
                    m.getId(),
                    m.getName(),
                    m.getSlug(),
                    m.getBrand(),
                    m.getManufacturer(),
                    m.getComposition(),
                    m.getDosageForm(),
                    m.getPackSize(),
                    m.getDescription(),
                    m.getMrpPrice(),
                    m.getSellingPrice(),
                    m.isPrescriptionRequired() || (rule != null && rule.isPrescriptionRequired()),
                    classCode,
                    className,
                    autoSub,
                    maxUnits,
                    warning,
                    available > 0,
                    available,
                    m.getCategory().getId(),
                    m.getCategory().getName(),
                    m.getCategory().getSlug(),
                    m.getPharmacy().getId(),
                    m.getPharmacy().getPharmacyName(),
                    m.getPharmacy().getSlug(),
                    m.getPharmacy().getCity(),
                    primaryUrl,
                    imageDtos
            );
        }
    }
}
