package com.medexa.delivery;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByOrderId(Long orderId);

    Optional<Delivery> findByDeliveryReference(String deliveryReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Delivery d WHERE d.id = :id")
    Optional<Delivery> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Delivery d WHERE d.order.id = :orderId")
    Optional<Delivery> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    long countByAssignedRiderIdAndStatusIn(Long riderId, Collection<DeliveryStatus> statuses);

    @Query("SELECT d FROM Delivery d WHERE d.status = 'ASSIGNED' AND d.assignmentExpiresAt IS NOT NULL AND d.assignmentExpiresAt < :now")
    List<Delivery> findExpiredAssignedDeliveries(@Param("now") Instant now);

    @Query(
            value = """
                    SELECT d FROM Delivery d
                    JOIN FETCH d.order o
                    JOIN FETCH d.pharmacy p
                    JOIN FETCH d.customer c
                    WHERE d.assignedRider.id = :riderId
                      AND (:status IS NULL OR d.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(d) FROM Delivery d
                    WHERE d.assignedRider.id = :riderId
                      AND (:status IS NULL OR d.status = :status)
                    """
    )
    Page<Delivery> findForRider(
            @Param("riderId") Long riderId,
            @Param("status") DeliveryStatus status,
            Pageable pageable);

    @Query("""
            SELECT d FROM Delivery d
            JOIN FETCH d.order o
            JOIN FETCH d.pharmacy p
            JOIN FETCH d.customer c
            WHERE d.assignedRider.id = :riderId
              AND d.status IN :statuses
            ORDER BY d.assignedAt DESC, d.id DESC
            """)
    List<Delivery> findByAssignedRiderIdAndStatusInWithRefs(
            @Param("riderId") Long riderId,
            @Param("statuses") Collection<DeliveryStatus> statuses);

    @Query(
            value = """
                    SELECT d FROM Delivery d
                    JOIN FETCH d.order o
                    JOIN FETCH d.pharmacy p
                    JOIN FETCH d.customer c
                    LEFT JOIN FETCH d.assignedRider r
                    LEFT JOIN FETCH r.user ru
                    WHERE (:status IS NULL OR d.status = :status)
                      AND (:riderId IS NULL OR r.id = :riderId)
                      AND (:exceptionsOnly = FALSE OR d.status IN ('WAITING_FOR_RIDER', 'REASSIGNMENT_REQUIRED', 'FAILED'))
                      AND (:q IS NULL OR :q = ''
                           OR LOWER(d.deliveryReference) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(p.pharmacyName) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%')))
                    """,
            countQuery = """
                    SELECT COUNT(d) FROM Delivery d
                    JOIN d.order o
                    JOIN d.pharmacy p
                    JOIN d.customer c
                    LEFT JOIN d.assignedRider r
                    WHERE (:status IS NULL OR d.status = :status)
                      AND (:riderId IS NULL OR r.id = :riderId)
                      AND (:exceptionsOnly = FALSE OR d.status IN ('WAITING_FOR_RIDER', 'REASSIGNMENT_REQUIRED', 'FAILED'))
                      AND (:q IS NULL OR :q = ''
                           OR LOWER(d.deliveryReference) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(p.pharmacyName) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%')))
                    """
    )
    Page<Delivery> findForAdmin(
            @Param("status") DeliveryStatus status,
            @Param("riderId") Long riderId,
            @Param("exceptionsOnly") boolean exceptionsOnly,
            @Param("q") String q,
            Pageable pageable);
}
