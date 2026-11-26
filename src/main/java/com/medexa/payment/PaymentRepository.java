package com.medexa.payment;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentReference(String paymentReference);

    Optional<Payment> findByPaymentReferenceAndCustomerId(String paymentReference, Long customerId);

    Optional<Payment> findByCheckoutSessionId(Long checkoutSessionId);

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.gatewayOrderId = :gatewayOrderId")
    Optional<Payment> findByGatewayOrderIdForUpdate(@Param("gatewayOrderId") String gatewayOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.checkoutSession.id = :checkoutSessionId")
    Optional<Payment> findByCheckoutSessionIdForUpdate(@Param("checkoutSessionId") Long checkoutSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentReference = :ref")
    Optional<Payment> findByPaymentReferenceForUpdate(@Param("ref") String paymentReference);

    @Query(
            value = """
                    SELECT p FROM Payment p
                    JOIN FETCH p.checkoutSession cs
                    WHERE p.customer.id = :customerId
                      AND (:status IS NULL OR p.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Payment p
                    WHERE p.customer.id = :customerId
                      AND (:status IS NULL OR p.status = :status)
                    """
    )
    Page<Payment> findForCustomer(
            @Param("customerId") Long customerId,
            @Param("status") PaymentStatus status,
            Pageable pageable);

    @Query(
            value = """
                    SELECT p FROM Payment p
                    JOIN FETCH p.customer c
                    JOIN FETCH p.checkoutSession cs
                    WHERE (:status IS NULL OR p.status = :status)
                      AND (:q IS NULL OR :q = ''
                           OR LOWER(p.paymentReference) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(p.gatewayOrderId) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%')))
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Payment p
                    JOIN p.customer c
                    WHERE (:status IS NULL OR p.status = :status)
                      AND (:q IS NULL OR :q = ''
                           OR LOWER(p.paymentReference) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(p.gatewayOrderId) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%')))
                    """
    )
    Page<Payment> findForAdmin(
            @Param("status") PaymentStatus status,
            @Param("q") String q,
            Pageable pageable);
}
