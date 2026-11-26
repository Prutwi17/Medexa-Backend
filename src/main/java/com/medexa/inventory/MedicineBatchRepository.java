package com.medexa.inventory;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicineBatchRepository extends JpaRepository<MedicineBatch, Long> {

    boolean existsByMedicineIdAndBatchNumberIgnoreCase(Long medicineId, String batchNumber);

    Optional<MedicineBatch> findByIdAndPharmacyId(Long id, Long pharmacyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM MedicineBatch b WHERE b.id = :id")
    Optional<MedicineBatch> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM MedicineBatch b
            WHERE b.medicine.id = :medicineId
              AND b.active = TRUE
              AND b.availableQuantity > 0
              AND b.expiryDate > :today
            ORDER BY b.expiryDate ASC, b.id ASC
            """)
    List<MedicineBatch> findEligibleBatchesForUpdate(
            @Param("medicineId") Long medicineId,
            @Param("today") LocalDate today);

    @Query("""
            SELECT COALESCE(SUM(b.availableQuantity), 0)
            FROM MedicineBatch b
            WHERE b.medicine.id = :medicineId
              AND b.active = TRUE
              AND b.expiryDate > :today
            """)
    Integer sumSellableQuantity(
            @Param("medicineId") Long medicineId,
            @Param("today") LocalDate today);

    @Query("""
            SELECT b FROM MedicineBatch b
            JOIN FETCH b.medicine m
            WHERE b.pharmacy.id = :pharmacyId
              AND (:medicineId IS NULL OR m.id = :medicineId)
              AND (:includeExpired = TRUE OR b.expiryDate > :today)
            ORDER BY b.expiryDate ASC, b.id ASC
            """)
    Page<MedicineBatch> findByPharmacy(
            @Param("pharmacyId") Long pharmacyId,
            @Param("medicineId") Long medicineId,
            @Param("includeExpired") boolean includeExpired,
            @Param("today") LocalDate today,
            Pageable pageable);

    @Query("""
            SELECT b FROM MedicineBatch b
            JOIN FETCH b.medicine m
            WHERE b.pharmacy.id = :pharmacyId
              AND b.active = TRUE
              AND (b.availableQuantity > 0 OR b.reservedQuantity > 0)
              AND b.expiryDate <= :thresholdDate
            ORDER BY b.expiryDate ASC
            """)
    List<MedicineBatch> findExpiringOrExpiredBatches(
            @Param("pharmacyId") Long pharmacyId,
            @Param("thresholdDate") LocalDate thresholdDate);
}
