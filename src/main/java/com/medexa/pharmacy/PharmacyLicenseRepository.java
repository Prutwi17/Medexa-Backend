package com.medexa.pharmacy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyLicenseRepository extends JpaRepository<PharmacyLicense, Long> {

    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);

    List<PharmacyLicense> findByPharmacyIdOrderByCreatedAtDesc(Long pharmacyId);

    Optional<PharmacyLicense> findFirstByPharmacyIdOrderByCreatedAtDesc(Long pharmacyId);
}
