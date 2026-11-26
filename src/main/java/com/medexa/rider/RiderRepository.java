package com.medexa.rider;

import com.medexa.pharmacy.VerificationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiderRepository extends JpaRepository<Rider, Long> {

    Optional<Rider> findByUserId(Long userId);

    boolean existsByDrivingLicenseNumberIgnoreCase(String drivingLicenseNumber);

    @Query("""
            SELECT r FROM Rider r
            JOIN FETCH r.user u
            WHERE r.verificationStatus = 'APPROVED'
              AND r.availabilityStatus = 'ONLINE'
              AND u.accountStatus = 'ACTIVE'
            """)
    List<Rider> findAllEligibleOnlineRiders();

    @Query(
            value = """
                    SELECT r FROM Rider r
                    JOIN FETCH r.user u
                    WHERE (:verificationStatus IS NULL OR r.verificationStatus = :verificationStatus)
                      AND (:availabilityStatus IS NULL OR r.availabilityStatus = :availabilityStatus)
                      AND (:city IS NULL OR :city = '' OR LOWER(r.serviceCity) LIKE LOWER(CONCAT('%', :city, '%')))
                      AND (:q IS NULL OR :q = ''
                           OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(r.drivingLicenseNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(r.vehicleRegistrationNumber) LIKE LOWER(CONCAT('%', :q, '%')))
                    """,
            countQuery = """
                    SELECT COUNT(r) FROM Rider r
                    JOIN r.user u
                    WHERE (:verificationStatus IS NULL OR r.verificationStatus = :verificationStatus)
                      AND (:availabilityStatus IS NULL OR r.availabilityStatus = :availabilityStatus)
                      AND (:city IS NULL OR :city = '' OR LOWER(r.serviceCity) LIKE LOWER(CONCAT('%', :city, '%')))
                      AND (:q IS NULL OR :q = ''
                           OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(r.drivingLicenseNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(r.vehicleRegistrationNumber) LIKE LOWER(CONCAT('%', :q, '%')))
                    """
    )
    Page<Rider> findForAdmin(
            @Param("verificationStatus") VerificationStatus verificationStatus,
            @Param("availabilityStatus") RiderAvailabilityStatus availabilityStatus,
            @Param("city") String city,
            @Param("q") String q,
            Pageable pageable);
}
