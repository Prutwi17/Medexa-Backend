package com.medexa.medicine.dto;

import com.medexa.medicine.Medicine;
import com.medexa.medicine.MedicineApprovalStatus;
import com.medexa.medicine.MedicineImage;
import jakarta.validation.constraints.DecimalMin;
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
            List<MedicineImageResponse> imageDtos = images.stream().map(MedicineImageResponse::from).toList();
            String primaryUrl = imageDtos.stream()
                    .filter(MedicineImageResponse::primaryImage)
                    .map(MedicineImageResponse::imageUrl)
                    .findFirst()
                    .orElse(imageDtos.isEmpty() ? null : imageDtos.get(0).imageUrl());

            int available = m.getTotalAvailableQuantity() != null ? m.getTotalAvailableQuantity() : 0;

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
                    m.isPrescriptionRequired(),
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
