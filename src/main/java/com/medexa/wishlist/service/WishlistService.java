package com.medexa.wishlist.service;

import com.medexa.common.exception.BusinessRuleException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.medicine.Medicine;
import com.medexa.medicine.MedicineApprovalStatus;
import com.medexa.medicine.MedicineClassificationRule;
import com.medexa.medicine.MedicineImage;
import com.medexa.medicine.MedicineImageRepository;
import com.medexa.medicine.MedicineRepository;
import com.medexa.medicine.dto.MedicineDtos.PublicMedicineResponse;
import com.medexa.medicine.service.MedicineClassificationService;
import com.medexa.pharmacy.VerificationStatus;
import com.medexa.user.AccountStatus;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import com.medexa.wishlist.WishlistItem;
import com.medexa.wishlist.WishlistItemRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final MedicineRepository medicineRepository;
    private final MedicineImageRepository medicineImageRepository;
    private final UserRepository userRepository;
    private final MedicineClassificationService classificationService;

    public record WishlistEntryResponse(
            Long wishlistItemId,
            Instant savedAt,
            PublicMedicineResponse medicine
    ) {
    }

    @Transactional(readOnly = true)
    public List<WishlistEntryResponse> listWishlist(Long customerId) {
        List<WishlistItem> items = wishlistItemRepository.findAllForCustomer(customerId)
                .stream()
                .filter(w -> isCatalogVisible(w.getMedicine()))
                .toList();
        if (items.isEmpty()) {
            return List.of();
        }

        List<Long> medIds = items.stream().map(w -> w.getMedicine().getId()).toList();
        Map<Long, List<MedicineImage>> imagesByMed = medicineImageRepository
                .findByMedicineIdInOrderByPrimaryImageDescDisplayOrderAscIdAsc(medIds)
                .stream()
                .collect(Collectors.groupingBy(img -> img.getMedicine().getId()));
        Map<String, MedicineClassificationRule> ruleMap = classificationService.loadRuleMap();

        return items.stream()
                .map(w -> {
                    Medicine m = w.getMedicine();
                    String code = m.getClassificationCode() != null ? m.getClassificationCode().toUpperCase() : "OTC";
                    return new WishlistEntryResponse(
                            w.getId(),
                            w.getCreatedAt(),
                            PublicMedicineResponse.from(
                                    m,
                                    imagesByMed.getOrDefault(m.getId(), List.of()),
                                    ruleMap.get(code)
                            )
                    );
                })
                .toList();
    }

    @Transactional
    public WishlistEntryResponse addToWishlist(Long customerId, Long medicineId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));
        Medicine medicine = medicineRepository.findById(medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found."));
        if (!isCatalogVisible(medicine)) {
            throw new BusinessRuleException("Only approved active catalog medicines can be saved to wishlist.");
        }

        WishlistItem item = wishlistItemRepository.findByCustomerIdAndMedicineId(customerId, medicineId)
                .orElseGet(() -> wishlistItemRepository.save(
                        WishlistItem.builder().customer(customer).medicine(medicine).build()
                ));

        List<MedicineImage> images = medicineImageRepository
                .findByMedicineIdOrderByPrimaryImageDescDisplayOrderAscIdAsc(medicine.getId());
        Map<String, MedicineClassificationRule> ruleMap = classificationService.loadRuleMap();
        String code = medicine.getClassificationCode() != null ? medicine.getClassificationCode().toUpperCase() : "OTC";
        return new WishlistEntryResponse(
                item.getId(),
                item.getCreatedAt(),
                PublicMedicineResponse.from(medicine, images, ruleMap.get(code))
        );
    }

    @Transactional
    public void removeFromWishlist(Long customerId, Long medicineId) {
        wishlistItemRepository.findByCustomerIdAndMedicineId(customerId, medicineId)
                .ifPresent(wishlistItemRepository::delete);
    }

    private boolean isCatalogVisible(Medicine m) {
        return m.getApprovalStatus() == MedicineApprovalStatus.APPROVED
                && m.isActive()
                && m.getCategory().isActive()
                && m.getPharmacy().getVerificationStatus() == VerificationStatus.APPROVED
                && m.getPharmacy().getUser().getAccountStatus() == AccountStatus.ACTIVE;
    }
}
