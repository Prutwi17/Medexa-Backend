package com.medexa.delivery;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryAssignmentAttemptRepository extends JpaRepository<DeliveryAssignmentAttempt, Long> {

    @Query("""
            SELECT a FROM DeliveryAssignmentAttempt a
            JOIN FETCH a.rider r
            JOIN FETCH r.user u
            WHERE a.delivery.id = :deliveryId
            ORDER BY a.attemptNumber ASC, a.id ASC
            """)
    List<DeliveryAssignmentAttempt> findByDeliveryIdWithRiderOrderByAttemptNumberAsc(@Param("deliveryId") Long deliveryId);

    Optional<DeliveryAssignmentAttempt> findFirstByDeliveryIdAndRiderIdAndStatusOrderByIdDesc(
            Long deliveryId,
            Long riderId,
            AssignmentAttemptStatus status);

    @Query("""
            SELECT DISTINCT a.rider.id FROM DeliveryAssignmentAttempt a
            WHERE a.delivery.id = :deliveryId
              AND a.status IN ('REJECTED', 'TIMED_OUT')
            """)
    List<Long> findDeclinedOrTimedOutRiderIds(@Param("deliveryId") Long deliveryId);
}
