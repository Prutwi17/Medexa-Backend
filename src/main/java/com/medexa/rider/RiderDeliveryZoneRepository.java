package com.medexa.rider;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiderDeliveryZoneRepository extends JpaRepository<RiderDeliveryZone, Long> {

    List<RiderDeliveryZone> findByRiderIdOrderByPriorityOrderAscIdAsc(Long riderId);

    List<RiderDeliveryZone> findByRiderIdInAndActiveTrue(List<Long> riderIds);

    Optional<RiderDeliveryZone> findByIdAndRiderId(Long id, Long riderId);

    boolean existsByRiderIdAndPostalCodeIgnoreCase(Long riderId, String postalCode);
}
