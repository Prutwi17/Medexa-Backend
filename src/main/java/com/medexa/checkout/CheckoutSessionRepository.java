package com.medexa.checkout;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, Long> {

    Optional<CheckoutSession> findByCheckoutReference(String checkoutReference);

    Optional<CheckoutSession> findByCheckoutReferenceAndCustomerId(String checkoutReference, Long customerId);

    Optional<CheckoutSession> findByIdempotencyKeyAndCustomerId(String idempotencyKey, Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CheckoutSession c WHERE c.checkoutReference = :ref")
    Optional<CheckoutSession> findByCheckoutReferenceForUpdate(@Param("ref") String checkoutReference);

    List<CheckoutSession> findByStatusAndExpiresAtBefore(CheckoutStatus status, Instant cutoff);
}
