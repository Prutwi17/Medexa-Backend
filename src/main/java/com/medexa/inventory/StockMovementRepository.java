package com.medexa.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("""
            SELECT sm FROM StockMovement sm
            JOIN FETCH sm.medicine m
            JOIN FETCH sm.batch b
            WHERE sm.pharmacy.id = :pharmacyId
              AND (:medicineId IS NULL OR m.id = :medicineId)
            ORDER BY sm.createdAt DESC, sm.id DESC
            """)
    Page<StockMovement> findByPharmacy(
            @Param("pharmacyId") Long pharmacyId,
            @Param("medicineId") Long medicineId,
            Pageable pageable);
}
