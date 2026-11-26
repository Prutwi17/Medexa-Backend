package com.medexa.medicine;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicineImageRepository extends JpaRepository<MedicineImage, Long> {

    List<MedicineImage> findByMedicineIdOrderByPrimaryImageDescDisplayOrderAscIdAsc(Long medicineId);

    List<MedicineImage> findByMedicineIdInOrderByPrimaryImageDescDisplayOrderAscIdAsc(List<Long> medicineIds);

    Optional<MedicineImage> findByIdAndMedicineId(Long id, Long medicineId);
}
