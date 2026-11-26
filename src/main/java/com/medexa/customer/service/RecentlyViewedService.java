package com.medexa.customer.service;

import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.customer.RecentlyViewedMedicine;
import com.medexa.customer.RecentlyViewedMedicineRepository;
import com.medexa.medicine.Medicine;
import com.medexa.medicine.MedicineApprovalStatus;
import com.medexa.medicine.MedicineClassificationRule;
import com.medexa.medicine.MedicineImage;
import com.medexa.medicine.MedicineImageRepository;
import com.medexa.medicine.MedicineRepository;
import com.medexa.medicine.dto.MedicineDtos.PublicMedicineResponse;
import com.medexa.medicine.service.MedicineClassificationService;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecentlyViewedService {

    private static final int MAX_RECENT_ITEMS = 12;

    private final RecentlyViewedMedicineRepository recentlyViewedRepository;
    private final MedicineRepository medicineRepository;
    private final MedicineImageRepository medicineImageRepository;
    private final UserRepository userRepository;
    private final MedicineClassificationService classificationService;

    @Transactional
    public void recordView(Long customerId, Long medicineId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));
        Medicine medicine = medicineRepository.findById(medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found."));
        if (medicine.getApprovalStatus() != MedicineApprovalStatus.APPROVED || !medicine.isActive()) {
            return;
        }

        RecentlyViewedMedicine entry = recentlyViewedRepository
                .findByCustomerIdAndMedicineId(customerId, medicineId)
                .orElseGet(() -> RecentlyViewedMedicine.builder()
                        .customer(customer)
                        .medicine(medicine)
                        .build());
        entry.setViewedAt(Instant.now());
        recentlyViewedRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<PublicMedicineResponse> listRecentlyViewed(Long customerId) {
        List<RecentlyViewedMedicine> rows = recentlyViewedRepository.findRecentApprovedForCustomer(customerId);
        List<Medicine> medicines = rows.stream()
                .limit(MAX_RECENT_ITEMS)
                .map(RecentlyViewedMedicine::getMedicine)
                .toList();
        if (medicines.isEmpty()) {
            return List.of();
        }

        List<Long> ids = medicines.stream().map(Medicine::getId).toList();
        Map<Long, List<MedicineImage>> imagesByMedicine = medicineImageRepository
                .findByMedicineIdInOrderByPrimaryImageDescDisplayOrderAscIdAsc(ids)
                .stream()
                .collect(Collectors.groupingBy(img -> img.getMedicine().getId()));
        Map<String, MedicineClassificationRule> ruleMap = classificationService.loadRuleMap();

        return medicines.stream()
                .map(m -> PublicMedicineResponse.from(
                        m,
                        imagesByMedicine.getOrDefault(m.getId(), List.of()),
                        ruleMap.get(m.getClassificationCode() != null ? m.getClassificationCode().toUpperCase() : "OTC")
                ))
                .toList();
    }
}
