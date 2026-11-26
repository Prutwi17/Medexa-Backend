package com.medexa.pharmacy;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {

    Optional<Pharmacy> findByUserId(Long userId);

    boolean existsBySlug(String slug);
}
