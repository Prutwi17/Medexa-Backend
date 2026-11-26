package com.medexa.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCheckoutSessionIdOrderByIdAsc(Long checkoutSessionId);

    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Order> findByIdAndPharmacyId(Long id, Long pharmacyId);

    @Query(
            value = """
                    SELECT o FROM Order o
                    JOIN FETCH o.pharmacy p
                    JOIN FETCH o.checkoutSession cs
                    WHERE o.customer.id = :customerId
                      AND (:status IS NULL OR o.status = :status)
                      AND (:q IS NULL OR :q = ''
                           OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(p.pharmacyName) LIKE LOWER(CONCAT('%', :q, '%')))
                    """,
            countQuery = """
                    SELECT COUNT(o) FROM Order o
                    JOIN o.pharmacy p
                    WHERE o.customer.id = :customerId
                      AND (:status IS NULL OR o.status = :status)
                      AND (:q IS NULL OR :q = ''
                           OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(p.pharmacyName) LIKE LOWER(CONCAT('%', :q, '%')))
                    """
    )
    Page<Order> findForCustomer(
            @Param("customerId") Long customerId,
            @Param("status") OrderStatus status,
            @Param("q") String q,
            Pageable pageable);

    @Query(
            value = """
                    SELECT o FROM Order o
                    JOIN FETCH o.customer c
                    JOIN FETCH o.pharmacy p
                    JOIN FETCH o.checkoutSession cs
                    WHERE o.pharmacy.id = :pharmacyId
                      AND (:status IS NULL OR o.status = :status)
                      AND (:q IS NULL OR :q = ''
                           OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%')))
                    """,
            countQuery = """
                    SELECT COUNT(o) FROM Order o
                    JOIN o.customer c
                    WHERE o.pharmacy.id = :pharmacyId
                      AND (:status IS NULL OR o.status = :status)
                      AND (:q IS NULL OR :q = ''
                           OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%')))
                    """
    )
    Page<Order> findForPharmacy(
            @Param("pharmacyId") Long pharmacyId,
            @Param("status") OrderStatus status,
            @Param("q") String q,
            Pageable pageable);

    @Query(
            value = """
                    SELECT o FROM Order o
                    JOIN FETCH o.customer c
                    JOIN FETCH o.pharmacy p
                    JOIN FETCH o.checkoutSession cs
                    WHERE (:status IS NULL OR o.status = :status)
                      AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus)
                      AND (:pharmacyId IS NULL OR o.pharmacy.id = :pharmacyId)
                      AND (:q IS NULL OR :q = ''
                           OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(p.pharmacyName) LIKE LOWER(CONCAT('%', :q, '%')))
                    """,
            countQuery = """
                    SELECT COUNT(o) FROM Order o
                    JOIN o.customer c
                    JOIN o.pharmacy p
                    WHERE (:status IS NULL OR o.status = :status)
                      AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus)
                      AND (:pharmacyId IS NULL OR o.pharmacy.id = :pharmacyId)
                      AND (:q IS NULL OR :q = ''
                           OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(p.pharmacyName) LIKE LOWER(CONCAT('%', :q, '%')))
                    """
    )
    Page<Order> findForAdmin(
            @Param("status") OrderStatus status,
            @Param("paymentStatus") OrderPaymentStatus paymentStatus,
            @Param("pharmacyId") Long pharmacyId,
            @Param("q") String q,
            Pageable pageable);
}
