package com.medexa.refund;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, Long> {

    Optional<RefundRecord> findByIdAndCustomerId(Long id, Long customerId);

    Optional<RefundRecord> findByOrderId(Long orderId);

    Optional<RefundRecord> findByGatewayRefundId(String gatewayRefundId);

    Optional<RefundRecord> findByIdempotencyKeyAndCustomerId(String idempotencyKey, Long customerId);

    boolean existsByOrderIdAndStatusIn(Long orderId, List<RefundRecordStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefundRecord r WHERE r.id = :id")
    Optional<RefundRecord> findByIdForUpdate(@Param("id") Long id);

    @Query(
            value = """
                    SELECT r FROM RefundRecord r
                    JOIN FETCH r.order o
                    JOIN FETCH r.payment p
                    WHERE r.customer.id = :customerId
                      AND (:status IS NULL OR r.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(r) FROM RefundRecord r
                    WHERE r.customer.id = :customerId
                      AND (:status IS NULL OR r.status = :status)
                    """
    )
    Page<RefundRecord> findForCustomer(
            @Param("customerId") Long customerId,
            @Param("status") RefundRecordStatus status,
            Pageable pageable);

    @Query(
            value = """
                    SELECT r FROM RefundRecord r
                    JOIN FETCH r.order o
                    JOIN FETCH r.customer c
                    JOIN FETCH r.payment p
                    WHERE (:status IS NULL OR r.status = :status)
                      AND (:q IS NULL OR :q = ''
                           OR LOWER(r.refundReference) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%')))
                    """,
            countQuery = """
                    SELECT COUNT(r) FROM RefundRecord r
                    JOIN r.order o
                    JOIN r.customer c
                    WHERE (:status IS NULL OR r.status = :status)
                      AND (:q IS NULL OR :q = ''
                           OR LOWER(r.refundReference) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%')))
                    """
    )
    Page<RefundRecord> findForAdmin(
            @Param("status") RefundRecordStatus status,
            @Param("q") String q,
            Pageable pageable);
}
