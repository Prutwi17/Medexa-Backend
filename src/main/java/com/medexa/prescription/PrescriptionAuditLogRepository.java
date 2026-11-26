package com.medexa.prescription;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionAuditLogRepository extends JpaRepository<PrescriptionAuditLog, Long> {

    List<PrescriptionAuditLog> findByPrescriptionIdOrderByCreatedAtAscIdAsc(Long prescriptionId);
}
