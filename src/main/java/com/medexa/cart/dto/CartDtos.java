package com.medexa.cart.dto;

import com.medexa.prescription.PrescriptionStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    public record AddToCartRequest(
            @NotNull(message = "Medicine ID is required.")
            Long medicineId,

            @NotNull(message = "Quantity is required.")
            @Min(value = 1, message = "Quantity must be at least 1.")
            Integer quantity,

            Long prescriptionId
    ) {
    }

    public record UpdateCartItemRequest(
            @NotNull(message = "Quantity is required.")
            @Min(value = 1, message = "Quantity must be at least 1.")
            Integer quantity,

            Long prescriptionId,

            Boolean clearPrescription
    ) {
    }

    public record CartItemResponse(
            Long cartItemId,
            Long medicineId,
            String medicineName,
            String medicineSlug,
            String brand,
            String packSize,
            String primaryImageUrl,
            Integer quantity,
            Integer availableStock,
            boolean inStock,
            Integer maxUnitsPerOrder,
            BigDecimal mrpPrice,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            boolean prescriptionRequired,
            String classificationCode,
            Long prescriptionId,
            String prescriptionReference,
            PrescriptionStatus prescriptionStatus,
            boolean dispatchEligible,
            String blockerMessage
    ) {
    }

    public record PharmacyCartGroupResponse(
            Long pharmacyId,
            String pharmacyName,
            String pharmacySlug,
            String pharmacyCity,
            List<CartItemResponse> items,
            BigDecimal itemsSubtotal,
            Boolean deliverableToSelectedAddress,
            BigDecimal deliveryFee,
            BigDecimal minOrderAmount,
            Integer estimatedDeliveryMinutes,
            BigDecimal estimatedGroupTotal,
            boolean readyForCheckout,
            List<String> groupBlockers
    ) {
    }

    public record CartSummaryResponse(
            Long selectedAddressId,
            String selectedPostalCode,
            int totalItemCount,
            int pharmacyCount,
            BigDecimal itemsSubtotal,
            BigDecimal totalDeliveryFee,
            BigDecimal grandTotal,
            boolean readyForCheckout,
            List<String> checkoutBlockers,
            List<PharmacyCartGroupResponse> pharmacyGroups
    ) {
    }
}
