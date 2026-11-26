package com.medexa.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
            SELECT oi FROM OrderItem oi
            JOIN FETCH oi.medicine m
            LEFT JOIN FETCH oi.prescription rx
            WHERE oi.order.id = :orderId
            ORDER BY oi.id ASC
            """)
    List<OrderItem> findByOrderIdWithRefs(@Param("orderId") Long orderId);

    @Query("""
            SELECT oi FROM OrderItem oi
            JOIN FETCH oi.medicine m
            LEFT JOIN FETCH oi.prescription rx
            WHERE oi.order.id IN :orderIds
            ORDER BY oi.id ASC
            """)
    List<OrderItem> findByOrderIdInWithRefs(@Param("orderIds") List<Long> orderIds);
}
