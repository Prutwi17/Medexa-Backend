package com.medexa.medicine;

import com.medexa.category.Category;
import com.medexa.common.entity.BaseEntity;
import com.medexa.pharmacy.Pharmacy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "medicines")
public class Medicine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 230)
    private String slug;

    @Column(name = "brand", nullable = false, length = 150)
    private String brand;

    @Column(name = "manufacturer", nullable = false, length = 180)
    private String manufacturer;

    @Column(name = "composition", nullable = false, length = 500)
    private String composition;

    @Column(name = "dosage_form", nullable = false, length = 80)
    private String dosageForm;

    @Column(name = "pack_size", nullable = false, length = 80)
    private String packSize;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "mrp_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal mrpPrice;

    @Column(name = "selling_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "prescription_required", nullable = false)
    @Builder.Default
    private boolean prescriptionRequired = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 30)
    @Builder.Default
    private MedicineApprovalStatus approvalStatus = MedicineApprovalStatus.PENDING_APPROVAL;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "total_available_quantity", nullable = false)
    @Builder.Default
    private Integer totalAvailableQuantity = 0;
}
