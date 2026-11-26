package com.medexa.delivery;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, Long> {

    List<DeliveryEvent> findByDeliveryIdOrderByCreatedAtAscIdAsc(Long deliveryId);
}
