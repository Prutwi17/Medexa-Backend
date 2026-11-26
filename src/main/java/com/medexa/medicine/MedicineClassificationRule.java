package com.medexa.medicine;

import com.medexa.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
@Table(name = "medicine_classification_rules")
public class MedicineClassificationRule extends BaseEntity {

    @Column(name = "classification_code", nullable = false, unique = true, length = 40)
    private String classificationCode;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "prescription_required", nullable = false)
    @Builder.Default
    private boolean prescriptionRequired = false;

    @Column(name = "default_rx_validity_days", nullable = false)
    @Builder.Default
    private Integer defaultRxValidityDays = 180;

    @Column(name = "max_units_per_order", nullable = false)
    @Builder.Default
    private Integer maxUnitsPerOrder = 30;

    @Column(name = "auto_substitution_allowed", nullable = false)
    @Builder.Default
    private boolean autoSubstitutionAllowed = false;

    @Column(name = "requires_pharmacist_license_check", nullable = false)
    @Builder.Default
    private boolean requiresPharmacistLicenseCheck = true;

    @Column(name = "warning_text", length = 500)
    private String warningText;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
