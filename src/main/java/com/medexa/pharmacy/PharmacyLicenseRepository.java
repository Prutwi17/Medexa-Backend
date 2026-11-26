package com.medexa.pharmacy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyLicenseRepository extends JpaRepository<PharmacyLicense, Long> {

    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);
}
