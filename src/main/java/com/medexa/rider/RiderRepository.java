package com.medexa.rider;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiderRepository extends JpaRepository<Rider, Long> {

    Optional<Rider> findByUserId(Long userId);

    boolean existsByDrivingLicenseNumberIgnoreCase(String drivingLicenseNumber);
}
