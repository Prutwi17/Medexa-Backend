package com.medexa.wishlist;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    Optional<WishlistItem> findByCustomerIdAndMedicineId(Long customerId, Long medicineId);

    boolean existsByCustomerIdAndMedicineId(Long customerId, Long medicineId);

    @Query("""
            SELECT w FROM WishlistItem w
            JOIN FETCH w.medicine m
            JOIN FETCH m.category c
            JOIN FETCH m.pharmacy p
            JOIN p.user u
            WHERE w.customer.id = :customerId
            ORDER BY w.createdAt DESC
            """)
    List<WishlistItem> findAllForCustomer(@Param("customerId") Long customerId);
}
