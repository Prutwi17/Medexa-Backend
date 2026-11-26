package com.medexa.prescription;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionOrderLinkRepository extends JpaRepository<PrescriptionOrderLink, Long> {

    List<PrescriptionOrderLink> findByPrescriptionIdOrderByCreatedAtDesc(Long prescriptionId);

    List<PrescriptionOrderLink> findByOrderId(Long orderId);
}
