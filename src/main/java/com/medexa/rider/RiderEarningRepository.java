package com.medexa.rider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiderEarningRepository extends JpaRepository<RiderEarning, Long> {

    Optional<RiderEarning> findByDeliveryId(Long deliveryId);

    Page<RiderEarning> findByRiderIdOrderByEarnedAtDesc(Long riderId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(e.totalEarning), 0)
            FROM RiderEarning e
            WHERE e.rider.id = :riderId
              AND e.earnedAt >= :since
            """)
    BigDecimal sumEarningsSince(@Param("riderId") Long riderId, @Param("since") Instant since);

    @Query("""
            SELECT COALESCE(SUM(e.totalEarning), 0)
            FROM RiderEarning e
            WHERE e.rider.id = :riderId
            """)
    BigDecimal sumTotalEarnings(@Param("riderId") Long riderId);

    long countByRiderIdAndEarnedAtGreaterThanEqual(Long riderId, Instant since);

    long countByRiderId(Long riderId);
}
