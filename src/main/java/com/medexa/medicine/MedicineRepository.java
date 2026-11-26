package com.medexa.medicine;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    boolean existsBySlug(String slug);

    Optional<Medicine> findByIdAndPharmacyId(Long id, Long pharmacyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Medicine m WHERE m.id = :id")
    Optional<Medicine> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT m FROM Medicine m
            JOIN FETCH m.category c
            JOIN FETCH m.pharmacy p
            WHERE m.pharmacy.id = :pharmacyId
              AND (:status IS NULL OR m.approvalStatus = :status)
              AND (:q IS NULL OR :q = ''
                   OR LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(m.brand) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(m.composition) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Medicine> findByPharmacy(
            @Param("pharmacyId") Long pharmacyId,
            @Param("status") MedicineApprovalStatus status,
            @Param("q") String q,
            Pageable pageable);

    @Query("""
            SELECT m FROM Medicine m
            JOIN FETCH m.category c
            JOIN FETCH m.pharmacy p
            WHERE (:status IS NULL OR m.approvalStatus = :status)
              AND (:q IS NULL OR :q = ''
                   OR LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(m.brand) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.pharmacyName) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Medicine> findForAdminQueue(
            @Param("status") MedicineApprovalStatus status,
            @Param("q") String q,
            Pageable pageable);

    @Query("""
            SELECT m FROM Medicine m
            JOIN m.category c
            JOIN m.pharmacy p
            JOIN p.user u
            WHERE m.approvalStatus = 'APPROVED'
              AND m.active = TRUE
              AND c.active = TRUE
              AND p.verificationStatus = 'APPROVED'
              AND u.accountStatus = 'ACTIVE'
              AND (:q IS NULL OR :q = ''
                   OR LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(m.brand) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(m.composition) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (:pharmacyId IS NULL OR p.id = :pharmacyId)
              AND (:minPrice IS NULL OR m.sellingPrice >= :minPrice)
              AND (:maxPrice IS NULL OR m.sellingPrice <= :maxPrice)
              AND (:inStockOnly = FALSE OR m.totalAvailableQuantity > 0)
              AND (:prescriptionRequired IS NULL OR m.prescriptionRequired = :prescriptionRequired)
            """)
    Page<Medicine> searchPublicCatalog(
            @Param("q") String q,
            @Param("categoryId") Long categoryId,
            @Param("pharmacyId") Long pharmacyId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("inStockOnly") boolean inStockOnly,
            @Param("prescriptionRequired") Boolean prescriptionRequired,
            Pageable pageable);

    @Query("""
            SELECT m FROM Medicine m
            JOIN FETCH m.category c
            JOIN FETCH m.pharmacy p
            JOIN p.user u
            WHERE m.slug = :slug
              AND m.approvalStatus = 'APPROVED'
              AND m.active = TRUE
              AND c.active = TRUE
              AND p.verificationStatus = 'APPROVED'
              AND u.accountStatus = 'ACTIVE'
            """)
    Optional<Medicine> findPublicDetailBySlug(@Param("slug") String slug);
}
