package com.medexa.pharmacy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PharmacyDeliveryZoneRepository extends JpaRepository<PharmacyDeliveryZone, Long> {

    List<PharmacyDeliveryZone> findByPharmacyIdOrderByZoneNameAsc(Long pharmacyId);

    List<PharmacyDeliveryZone> findByPharmacyIdAndActiveTrueOrderByZoneNameAsc(Long pharmacyId);

    Optional<PharmacyDeliveryZone> findByIdAndPharmacyId(Long id, Long pharmacyId);

    boolean existsByPharmacyIdAndPostalCodeIgnoreCase(Long pharmacyId, String postalCode);

    boolean existsByPharmacyIdAndPostalCodeIgnoreCaseAndIdNot(Long pharmacyId, String postalCode, Long id);

    @Query("""
            SELECT z FROM PharmacyDeliveryZone z
            JOIN FETCH z.pharmacy p
            JOIN p.user u
            WHERE LOWER(z.postalCode) = LOWER(:postalCode)
              AND z.active = TRUE
              AND p.verificationStatus = 'APPROVED'
              AND u.accountStatus = 'ACTIVE'
              AND p.acceptingOrders = TRUE
            ORDER BY z.deliveryFee ASC, z.estimatedDeliveryMinutes ASC
            """)
    List<PharmacyDeliveryZone> findServiceableZonesByPostalCode(@Param("postalCode") String postalCode);

    @Query("""
            SELECT z FROM PharmacyDeliveryZone z
            JOIN z.pharmacy p
            JOIN p.user u
            WHERE p.id = :pharmacyId
              AND LOWER(z.postalCode) = LOWER(:postalCode)
              AND z.active = TRUE
              AND p.verificationStatus = 'APPROVED'
              AND u.accountStatus = 'ACTIVE'
            """)
    Optional<PharmacyDeliveryZone> findActiveZoneForPharmacyAndPostalCode(
            @Param("pharmacyId") Long pharmacyId,
            @Param("postalCode") String postalCode);
}
