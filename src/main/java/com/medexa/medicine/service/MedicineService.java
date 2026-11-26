package com.medexa.medicine.service;

import com.medexa.category.Category;
import com.medexa.category.CategoryRepository;
import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.BusinessRuleException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.common.storage.FileStorageService;
import com.medexa.common.util.SlugUtils;
import com.medexa.medicine.Medicine;
import com.medexa.medicine.MedicineApprovalStatus;
import com.medexa.medicine.MedicineImage;
import com.medexa.medicine.MedicineImageRepository;
import com.medexa.medicine.MedicineRepository;
import com.medexa.medicine.dto.MedicineDtos.MedicineImageResponse;
import com.medexa.medicine.dto.MedicineDtos.MedicineReviewRequest;
import com.medexa.medicine.dto.MedicineDtos.MedicineUpsertRequest;
import com.medexa.medicine.dto.MedicineDtos.PharmacyMedicineResponse;
import com.medexa.medicine.dto.MedicineDtos.PublicMedicineResponse;
import com.medexa.pharmacy.Pharmacy;
import com.medexa.pharmacy.service.PharmacyService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MedicineService {

    private final MedicineRepository medicineRepository;
    private final MedicineImageRepository medicineImageRepository;
    private final CategoryRepository categoryRepository;
    private final PharmacyService pharmacyService;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public Page<PharmacyMedicineResponse> listOwnMedicines(
            Long userId,
            MedicineApprovalStatus status,
            String query,
            Pageable pageable) {
        Pharmacy pharmacy = pharmacyService.requirePharmacyByUserId(userId);
        Page<Medicine> page = medicineRepository.findByPharmacy(pharmacy.getId(), status, query, pageable);
        Map<Long, List<MedicineImage>> imagesByMedicine = loadImagesMap(page.getContent());
        return page.map(m -> PharmacyMedicineResponse.from(m, imagesByMedicine.getOrDefault(m.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public PharmacyMedicineResponse getOwnMedicine(Long userId, Long medicineId) {
        Pharmacy pharmacy = pharmacyService.requirePharmacyByUserId(userId);
        Medicine medicine = medicineRepository.findByIdAndPharmacyId(medicineId, pharmacy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine listing not found."));
        List<MedicineImage> images = medicineImageRepository
                .findByMedicineIdOrderByPrimaryImageDescDisplayOrderAscIdAsc(medicine.getId());
        return PharmacyMedicineResponse.from(medicine, images);
    }

    @Transactional
    public PharmacyMedicineResponse createMedicine(Long userId, MedicineUpsertRequest request) {
        Pharmacy pharmacy = pharmacyService.requireVerifiedActivePharmacy(userId);
        Category category = requireActiveCategory(request.categoryId());
        validatePricing(request.mrpPrice(), request.sellingPrice());

        String slug = SlugUtils.toUniqueSlug(
                request.name() + "-" + pharmacy.getId(),
                "medicine",
                medicineRepository::existsBySlug
        );

        Medicine medicine = Medicine.builder()
                .pharmacy(pharmacy)
                .category(category)
                .name(request.name().trim())
                .slug(slug)
                .brand(request.brand().trim())
                .manufacturer(request.manufacturer().trim())
                .composition(request.composition().trim())
                .dosageForm(request.dosageForm().trim())
                .packSize(request.packSize().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .mrpPrice(request.mrpPrice())
                .sellingPrice(request.sellingPrice())
                .prescriptionRequired(Boolean.TRUE.equals(request.prescriptionRequired()))
                .approvalStatus(MedicineApprovalStatus.PENDING_APPROVAL)
                .active(request.active() == null || request.active())
                .totalAvailableQuantity(0)
                .build();

        medicineRepository.save(medicine);
        return PharmacyMedicineResponse.from(medicine, List.of());
    }

    @Transactional
    public PharmacyMedicineResponse updateMedicine(Long userId, Long medicineId, MedicineUpsertRequest request) {
        Pharmacy pharmacy = pharmacyService.requireVerifiedActivePharmacy(userId);
        Medicine medicine = medicineRepository.findByIdAndPharmacyId(medicineId, pharmacy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine listing not found."));
        Category category = requireActiveCategory(request.categoryId());
        validatePricing(request.mrpPrice(), request.sellingPrice());

        medicine.setCategory(category);
        medicine.setName(request.name().trim());
        medicine.setBrand(request.brand().trim());
        medicine.setManufacturer(request.manufacturer().trim());
        medicine.setComposition(request.composition().trim());
        medicine.setDosageForm(request.dosageForm().trim());
        medicine.setPackSize(request.packSize().trim());
        medicine.setDescription(request.description() != null ? request.description().trim() : null);
        medicine.setMrpPrice(request.mrpPrice());
        medicine.setSellingPrice(request.sellingPrice());
        medicine.setPrescriptionRequired(Boolean.TRUE.equals(request.prescriptionRequired()));
        if (request.active() != null) {
            medicine.setActive(request.active());
        }

        // Edits to a rejected or approved listing re-enter compliance review
        medicine.setApprovalStatus(MedicineApprovalStatus.PENDING_APPROVAL);
        medicine.setRejectionReason(null);
        medicine.setReviewedBy(null);
        medicine.setReviewedAt(null);

        medicineRepository.save(medicine);
        List<MedicineImage> images = medicineImageRepository
                .findByMedicineIdOrderByPrimaryImageDescDisplayOrderAscIdAsc(medicine.getId());
        return PharmacyMedicineResponse.from(medicine, images);
    }

    @Transactional
    public void deleteMedicine(Long userId, Long medicineId) {
        Pharmacy pharmacy = pharmacyService.requirePharmacyByUserId(userId);
        Medicine medicine = medicineRepository.findByIdAndPharmacyId(medicineId, pharmacy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine listing not found."));
        medicine.setActive(false);
        medicineRepository.save(medicine);
    }

    @Transactional
    public MedicineImageResponse uploadImage(
            Long userId,
            Long medicineId,
            MultipartFile file,
            boolean primary,
            String altText) {
        Pharmacy pharmacy = pharmacyService.requireVerifiedActivePharmacy(userId);
        Medicine medicine = medicineRepository.findByIdAndPharmacyId(medicineId, pharmacy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine listing not found."));

        String imageUrl = fileStorageService.storeImage(FileStorageService.BUCKET_MEDICINES, file);
        List<MedicineImage> existingImages = medicineImageRepository
                .findByMedicineIdOrderByPrimaryImageDescDisplayOrderAscIdAsc(medicine.getId());

        boolean makePrimary = primary || existingImages.isEmpty();
        if (makePrimary) {
            for (MedicineImage existing : existingImages) {
                if (existing.isPrimaryImage()) {
                    existing.setPrimaryImage(false);
                    medicineImageRepository.save(existing);
                }
            }
        }

        MedicineImage image = MedicineImage.builder()
                .medicine(medicine)
                .imageUrl(imageUrl)
                .altText(altText != null && !altText.isBlank() ? altText.trim() : medicine.getName())
                .primaryImage(makePrimary)
                .displayOrder(existingImages.size())
                .build();
        medicineImageRepository.save(image);
        return MedicineImageResponse.from(image);
    }

    @Transactional
    public void deleteImage(Long userId, Long medicineId, Long imageId) {
        Pharmacy pharmacy = pharmacyService.requirePharmacyByUserId(userId);
        Medicine medicine = medicineRepository.findByIdAndPharmacyId(medicineId, pharmacy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine listing not found."));
        MedicineImage image = medicineImageRepository.findByIdAndMedicineId(imageId, medicine.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine image not found."));
        medicineImageRepository.delete(image);
    }

    @Transactional(readOnly = true)
    public Page<PublicMedicineResponse> searchPublicCatalog(
            String query,
            Long categoryId,
            Long pharmacyId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean inStockOnly,
            Boolean prescriptionRequired,
            Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("Minimum price cannot exceed maximum price.");
        }
        Page<Medicine> page = medicineRepository.searchPublicCatalog(
                query, categoryId, pharmacyId, minPrice, maxPrice, inStockOnly, prescriptionRequired, pageable
        );
        Map<Long, List<MedicineImage>> imagesByMedicine = loadImagesMap(page.getContent());
        return page.map(m -> PublicMedicineResponse.from(m, imagesByMedicine.getOrDefault(m.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public PublicMedicineResponse getPublicMedicineDetail(String slug) {
        Medicine medicine = medicineRepository.findPublicDetailBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found in catalog."));
        List<MedicineImage> images = medicineImageRepository
                .findByMedicineIdOrderByPrimaryImageDescDisplayOrderAscIdAsc(medicine.getId());
        return PublicMedicineResponse.from(medicine, images);
    }

    @Transactional(readOnly = true)
    public Page<PharmacyMedicineResponse> listAdminQueue(
            MedicineApprovalStatus status,
            String query,
            Pageable pageable) {
        Page<Medicine> page = medicineRepository.findForAdminQueue(status, query, pageable);
        Map<Long, List<MedicineImage>> imagesByMedicine = loadImagesMap(page.getContent());
        return page.map(m -> PharmacyMedicineResponse.from(m, imagesByMedicine.getOrDefault(m.getId(), List.of())));
    }

    @Transactional
    public PharmacyMedicineResponse reviewMedicine(Long adminUserId, Long medicineId, MedicineReviewRequest request) {
        Medicine medicine = medicineRepository.findById(medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine listing not found."));

        if (Boolean.TRUE.equals(request.approved())) {
            medicine.setApprovalStatus(MedicineApprovalStatus.APPROVED);
            medicine.setRejectionReason(null);
        } else {
            if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
                throw new BadRequestException("A rejection reason is required when rejecting a medicine listing.");
            }
            medicine.setApprovalStatus(MedicineApprovalStatus.REJECTED);
            medicine.setRejectionReason(request.rejectionReason().trim());
        }
        medicine.setReviewedBy(adminUserId);
        medicine.setReviewedAt(Instant.now());
        medicineRepository.save(medicine);

        List<MedicineImage> images = medicineImageRepository
                .findByMedicineIdOrderByPrimaryImageDescDisplayOrderAscIdAsc(medicine.getId());
        return PharmacyMedicineResponse.from(medicine, images);
    }

    private Category requireActiveCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Selected category was not found."));
        if (!category.isActive()) {
            throw new BusinessRuleException("Selected category is inactive and cannot be used for listings.");
        }
        return category;
    }

    private void validatePricing(BigDecimal mrpPrice, BigDecimal sellingPrice) {
        if (sellingPrice.compareTo(mrpPrice) > 0) {
            throw new BadRequestException("Selling price cannot exceed MRP price.");
        }
    }

    private Map<Long, List<MedicineImage>> loadImagesMap(List<Medicine> medicines) {
        if (medicines.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = medicines.stream().map(Medicine::getId).toList();
        return medicineImageRepository.findByMedicineIdInOrderByPrimaryImageDescDisplayOrderAscIdAsc(ids)
                .stream()
                .collect(Collectors.groupingBy(img -> img.getMedicine().getId()));
    }
}
