package com.medexa.customer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecentlyViewedMedicineRepository extends JpaRepository<RecentlyViewedMedicine, Long> {

    Optional<RecentlyViewedMedicine> findByCustomerIdAndMedicineId(Long customerId, Long medicineId);

    @Query("""
            SELECT rv FROM RecentlyViewedMedicine rv
            JOIN FETCH rv.medicine m
            JOIN FETCH m.category c
            JOIN FETCH m.pharmacy p
            JOIN p.user u
            WHERE rv.customer.id = :customerId
              AND m.approvalStatus = 'APPROVED'
              AND m.active = TRUE
              AND c.active = TRUE
              AND p.verificationStatus = 'APPROVED'
              AND u.accountStatus = 'ACTIVE'
            ORDER BY rv.viewedAt DESC
            """)
    List<RecentlyViewedMedicine> findRecentApprovedForCustomer(@Param("customerId") Long customerId);
}
