package com.medexa.pharmacy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyDeliveryZoneRepository extends JpaRepository<PharmacyDeliveryZone, Long> {

    List<PharmacyDeliveryZone> findByPharmacyIdOrderByZoneNameAsc(Long pharmacyId);

    List<PharmacyDeliveryZone> findByPharmacyIdAndActiveTrueOrderByZoneNameAsc(Long pharmacyId);

    Optional<PharmacyDeliveryZone> findByIdAndPharmacyId(Long id, Long pharmacyId);

    boolean existsByPharmacyIdAndPostalCodeIgnoreCase(Long pharmacyId, String postalCode);

    boolean existsByPharmacyIdAndPostalCodeIgnoreCaseAndIdNot(Long pharmacyId, String postalCode, Long id);
}
