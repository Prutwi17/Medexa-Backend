package com.medexa.pharmacy;

import com.medexa.user.AccountStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {

    Optional<Pharmacy> findByUserId(Long userId);

    Optional<Pharmacy> findBySlugAndVerificationStatusAndUserAccountStatus(
            String slug,
            VerificationStatus verificationStatus,
            AccountStatus accountStatus);

    boolean existsBySlug(String slug);

    @Query("""
            SELECT p FROM Pharmacy p
            JOIN p.user u
            WHERE p.verificationStatus = 'APPROVED'
              AND u.accountStatus = 'ACTIVE'
              AND (:q IS NULL OR :q = ''
                   OR LOWER(p.pharmacyName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.city) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Pharmacy> findPublicVerifiedPharmacies(@Param("q") String q, Pageable pageable);

    @Query("""
            SELECT p FROM Pharmacy p
            JOIN FETCH p.user u
            WHERE (:status IS NULL OR p.verificationStatus = :status)
              AND (:q IS NULL OR :q = ''
                   OR LOWER(p.pharmacyName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.city) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Pharmacy> findForAdmin(
            @Param("status") VerificationStatus status,
            @Param("q") String q,
            Pageable pageable);
}
