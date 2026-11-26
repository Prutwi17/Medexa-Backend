package com.medexa.payment;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RazorpayWebhookEventRepository extends JpaRepository<RazorpayWebhookEvent, Long> {

    boolean existsByEventId(String eventId);

    Optional<RazorpayWebhookEvent> findByEventId(String eventId);

    Page<RazorpayWebhookEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
