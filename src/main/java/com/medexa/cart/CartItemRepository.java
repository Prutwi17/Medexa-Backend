package com.medexa.cart;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByIdAndCustomerId(Long id, Long customerId);

    Optional<CartItem> findByCustomerIdAndMedicineId(Long customerId, Long medicineId);

    @Query("""
            SELECT c FROM CartItem c
            JOIN FETCH c.medicine m
            JOIN FETCH m.category cat
            JOIN FETCH c.pharmacy p
            JOIN FETCH p.user pu
            LEFT JOIN FETCH c.prescription rx
            WHERE c.customer.id = :customerId
            ORDER BY p.pharmacyName ASC, c.createdAt ASC
            """)
    List<CartItem> findAllForCustomer(@Param("customerId") Long customerId);

    void deleteByCustomerId(Long customerId);
}
