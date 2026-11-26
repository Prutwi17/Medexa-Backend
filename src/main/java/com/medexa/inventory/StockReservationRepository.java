package com.medexa.inventory;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    boolean existsByReservationKeyAndStatus(String reservationKey, ReservationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM StockReservation r
            JOIN FETCH r.batch b
            JOIN FETCH r.medicine m
            WHERE r.reservationKey = :reservationKey
              AND r.status = 'ACTIVE'
            ORDER BY r.id ASC
            """)
    List<StockReservation> findActiveByReservationKeyForUpdate(@Param("reservationKey") String reservationKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM StockReservation r
            JOIN FETCH r.batch b
            JOIN FETCH r.medicine m
            WHERE r.reservationKey = :reservationKey
              AND r.medicine.id = :medicineId
              AND r.status = 'COMMITTED'
            ORDER BY r.id ASC
            """)
    List<StockReservation> findCommittedByKeyAndMedicineForUpdate(
            @Param("reservationKey") String reservationKey,
            @Param("medicineId") Long medicineId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM StockReservation r
            JOIN FETCH r.batch b
            JOIN FETCH r.medicine m
            WHERE r.status = 'ACTIVE'
              AND r.expiresAt <= :now
            """)
    List<StockReservation> findExpiredActiveReservationsForUpdate(@Param("now") Instant now);
}
