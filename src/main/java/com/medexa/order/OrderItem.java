package com.medexa.order;

import com.medexa.medicine.Medicine;
import com.medexa.prescription.Prescription;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_items")
@EntityListeners(AuditingEntityListener.class)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicine_id", nullable = false)
    private Medicine medicine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id")
    private Prescription prescription;

    @Column(name = "medicine_name", nullable = false, length = 200)
    private String medicineName;

    @Column(name = "medicine_slug", nullable = false, length = 230)
    private String medicineSlug;

    @Column(name = "brand", nullable = false, length = 150)
    private String brand;

    @Column(name = "pack_size", nullable = false, length = 80)
    private String packSize;

    @Column(name = "classification_code", nullable = false, length = 40)
    private String classificationCode;

    @Column(name = "prescription_required", nullable = false)
    @Builder.Default
    private boolean prescriptionRequired = false;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "mrp_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal mrpPrice;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineSubtotal;

    @Column(name = "discount_allocated", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAllocated = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "batch_allocations_summary", length = 1000)
    private String batchAllocationsSummary;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
