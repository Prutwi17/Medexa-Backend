package com.medexa.prescription;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    Optional<Prescription> findByIdAndCustomerId(Long id, Long customerId);

    @Query(
            value = """
                    SELECT p FROM Prescription p
                    JOIN FETCH p.customer c
                    LEFT JOIN FETCH p.pharmacy ph
                    LEFT JOIN FETCH p.reviewedByPharmacy rph
                    WHERE p.customer.id = :customerId
                      AND (:status IS NULL OR p.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Prescription p
                    WHERE p.customer.id = :customerId
                      AND (:status IS NULL OR p.status = :status)
                    """
    )
    Page<Prescription> findForCustomer(
            @Param("customerId") Long customerId,
            @Param("status") PrescriptionStatus status,
            Pageable pageable);

    @Query(
            value = """
                    SELECT p FROM Prescription p
                    JOIN FETCH p.customer c
                    LEFT JOIN FETCH p.pharmacy ph
                    LEFT JOIN FETCH p.reviewedByPharmacy rph
                    WHERE (p.pharmacy.id = :pharmacyId OR p.pharmacy IS NULL)
                      AND (:status IS NULL OR p.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Prescription p
                    WHERE (p.pharmacy.id = :pharmacyId OR p.pharmacy IS NULL)
                      AND (:status IS NULL OR p.status = :status)
                    """
    )
    Page<Prescription> findForPharmacyQueue(
            @Param("pharmacyId") Long pharmacyId,
            @Param("status") PrescriptionStatus status,
            Pageable pageable);

    @Query(
            value = """
                    SELECT p FROM Prescription p
                    JOIN FETCH p.customer c
                    LEFT JOIN FETCH p.pharmacy ph
                    LEFT JOIN FETCH p.reviewedByPharmacy rph
                    WHERE (:status IS NULL OR p.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Prescription p
                    WHERE (:status IS NULL OR p.status = :status)
                    """
    )
    Page<Prescription> findForAdmin(
            @Param("status") PrescriptionStatus status,
            Pageable pageable);
}
