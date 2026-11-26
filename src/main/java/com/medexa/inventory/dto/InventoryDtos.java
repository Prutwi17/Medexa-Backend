package com.medexa.inventory.dto;

import com.medexa.inventory.MedicineBatch;
import com.medexa.inventory.StockMovement;
import com.medexa.inventory.StockMovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record AddBatchRequest(
            @NotNull(message = "Medicine is required.")
            Long medicineId,

            @NotBlank(message = "Batch number is required.")
            @Size(max = 100)
            String batchNumber,

            @NotNull(message = "Manufacturing date is required.")
            LocalDate manufacturingDate,

            @NotNull(message = "Expiry date is required.")
            @Future(message = "Batch expiry date must be in the future.")
            LocalDate expiryDate,

            @NotNull(message = "Initial quantity is required.")
            @Min(value = 1, message = "Initial quantity must be at least 1.")
            Integer quantity,

            @NotNull(message = "Purchase price is required.")
            @DecimalMin(value = "0.01", message = "Purchase price must be greater than zero.")
            BigDecimal purchasePrice,

            @NotNull(message = "Selling price is required.")
            @DecimalMin(value = "0.01", message = "Selling price must be greater than zero.")
            BigDecimal sellingPrice
    ) {
    }

    public record AdjustStockRequest(
            @NotNull(message = "Quantity delta is required.")
            Integer quantityDelta,

            @NotNull(message = "Movement type is required.")
            StockMovementType movementType,

            @NotBlank(message = "Adjustment reason is required.")
            @Size(max = 300)
            String reason
    ) {
    }

    public record BatchResponse(
            Long id,
            Long medicineId,
            String medicineName,
            String batchNumber,
            LocalDate manufacturingDate,
            LocalDate expiryDate,
            long daysUntilExpiry,
            boolean expired,
            Integer initialQuantity,
            Integer availableQuantity,
            Integer reservedQuantity,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            boolean active,
            Instant createdAt
    ) {
        public static BatchResponse from(MedicineBatch batch) {
            LocalDate today = LocalDate.now();
            long days = ChronoUnit.DAYS.between(today, batch.getExpiryDate());
            return new BatchResponse(
                    batch.getId(),
                    batch.getMedicine().getId(),
                    batch.getMedicine().getName(),
                    batch.getBatchNumber(),
                    batch.getManufacturingDate(),
                    batch.getExpiryDate(),
                    days,
                    !batch.getExpiryDate().isAfter(today),
                    batch.getInitialQuantity(),
                    batch.getAvailableQuantity(),
                    batch.getReservedQuantity(),
                    batch.getPurchasePrice(),
                    batch.getSellingPrice(),
                    batch.isActive(),
                    batch.getCreatedAt()
            );
        }
    }

    public record StockMovementResponse(
            Long id,
            Long medicineId,
            String medicineName,
            Long batchId,
            String batchNumber,
            StockMovementType movementType,
            Integer quantityDelta,
            Integer balanceAfter,
            String referenceType,
            String referenceId,
            String reason,
            Instant createdAt
    ) {
        public static StockMovementResponse from(StockMovement m) {
            return new StockMovementResponse(
                    m.getId(),
                    m.getMedicine().getId(),
                    m.getMedicine().getName(),
                    m.getBatch().getId(),
                    m.getBatch().getBatchNumber(),
                    m.getMovementType(),
                    m.getQuantityDelta(),
                    m.getBalanceAfter(),
                    m.getReferenceType(),
                    m.getReferenceId(),
                    m.getReason(),
                    m.getCreatedAt()
            );
        }
    }

    public record BatchAllocation(
            Long batchId,
            String batchNumber,
            LocalDate expiryDate,
            int quantityAllocated
    ) {
    }
}
