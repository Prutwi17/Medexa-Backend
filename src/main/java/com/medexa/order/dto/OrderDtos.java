package com.medexa.order.dto;

import com.medexa.order.DeliveryAssignmentStatus;
import com.medexa.order.Order;
import com.medexa.order.OrderItem;
import com.medexa.order.OrderPaymentStatus;
import com.medexa.order.OrderStatus;
import com.medexa.order.OrderStatusHistory;
import com.medexa.order.RefundStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CancelOrderRequest(
            @NotBlank(message = "Cancellation reason is required.")
            @Size(min = 3, max = 500)
            String reason
    ) {
    }

    public record RefundRequestPayload(
            @NotBlank(message = "Refund reason is required.")
            @Size(min = 3, max = 500)
            String reason
    ) {
    }

    public record PharmacyOrderDecisionRequest(
            @NotNull(message = "Accept or reject decision is required.")
            Boolean accepted,

            @Size(max = 500)
            String reason
    ) {
    }

    public record PharmacyOrderStatusUpdateRequest(
            @NotNull(message = "Next order status is required.")
            OrderStatus status,

            @Size(max = 500)
            String notes
    ) {
    }

    public record OrderItemResponse(
            Long id,
            Long medicineId,
            String medicineName,
            String medicineSlug,
            String brand,
            String packSize,
            String classificationCode,
            boolean prescriptionRequired,
            Long prescriptionId,
            String prescriptionReference,
            Integer quantity,
            BigDecimal mrpPrice,
            BigDecimal unitPrice,
            BigDecimal lineSubtotal,
            BigDecimal discountAllocated,
            BigDecimal taxAmount,
            String batchAllocationsSummary
    ) {
        public static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                    item.getId(),
                    item.getMedicine().getId(),
                    item.getMedicineName(),
                    item.getMedicineSlug(),
                    item.getBrand(),
                    item.getPackSize(),
                    item.getClassificationCode(),
                    item.isPrescriptionRequired(),
                    item.getPrescription() != null ? item.getPrescription().getId() : null,
                    item.getPrescription() != null ? item.getPrescription().getReferenceCode() : null,
                    item.getQuantity(),
                    item.getMrpPrice(),
                    item.getUnitPrice(),
                    item.getLineSubtotal(),
                    item.getDiscountAllocated(),
                    item.getTaxAmount(),
                    item.getBatchAllocationsSummary()
            );
        }
    }

    public record OrderStatusHistoryResponse(
            Long id,
            OrderStatus previousStatus,
            OrderStatus newStatus,
            String actorRole,
            String actorName,
            String notes,
            Instant createdAt
    ) {
        public static OrderStatusHistoryResponse from(OrderStatusHistory history) {
            return new OrderStatusHistoryResponse(
                    history.getId(),
                    history.getPreviousStatus(),
                    history.getNewStatus(),
                    history.getActorRole(),
                    history.getActorName(),
                    history.getNotes(),
                    history.getCreatedAt()
            );
        }
    }

    public record OrderResponse(
            Long id,
            String orderNumber,
            String checkoutReference,
            Long customerId,
            String customerName,
            String customerEmail,
            Long pharmacyId,
            String pharmacyName,
            String pharmacyCity,
            String shippingRecipientName,
            String shippingPhone,
            String shippingAddress,
            String shippingCity,
            String shippingState,
            String shippingPostalCode,
            OrderStatus status,
            OrderPaymentStatus paymentStatus,
            BigDecimal itemsSubtotal,
            BigDecimal discountAmount,
            BigDecimal deliveryFee,
            BigDecimal taxAmount,
            BigDecimal grandTotal,
            BigDecimal platformCommissionAmount,
            BigDecimal pharmacyPayoutAmount,
            DeliveryAssignmentStatus deliveryAssignmentStatus,
            Integer deliveryEtaMinutes,
            boolean requiresPrescription,
            boolean allPrescriptionsVerified,
            boolean cancellableByCustomer,
            boolean refundEligible,
            RefundStatus refundStatus,
            String refundReason,
            String cancellationReason,
            String cancelledByRole,
            Instant placedAt,
            Instant acceptedAt,
            Instant preparedAt,
            Instant readyAt,
            Instant cancelledAt,
            Instant createdAt,
            List<OrderItemResponse> items,
            List<OrderStatusHistoryResponse> statusHistory
    ) {
        public static OrderResponse from(
                Order o,
                List<OrderItem> items,
                List<OrderStatusHistory> history,
                boolean includePayoutDetails) {
            boolean cancellable = o.getStatus() == OrderStatus.PAYMENT_PENDING
                    || o.getStatus() == OrderStatus.PLACED
                    || o.getStatus() == OrderStatus.PHARMACY_ACCEPTED
                    || o.getStatus() == OrderStatus.PREPARING;
            return new OrderResponse(
                    o.getId(),
                    o.getOrderNumber(),
                    o.getCheckoutSession().getCheckoutReference(),
                    o.getCustomer().getId(),
                    o.getCustomer().getFullName(),
                    o.getCustomer().getEmail(),
                    o.getPharmacy().getId(),
                    o.getPharmacy().getPharmacyName(),
                    o.getPharmacy().getCity(),
                    o.getShippingRecipientName(),
                    o.getShippingPhone(),
                    o.getShippingAddress(),
                    o.getShippingCity(),
                    o.getShippingState(),
                    o.getShippingPostalCode(),
                    o.getStatus(),
                    o.getPaymentStatus(),
                    o.getItemsSubtotal(),
                    o.getDiscountAmount(),
                    o.getDeliveryFee(),
                    o.getTaxAmount(),
                    o.getGrandTotal(),
                    includePayoutDetails ? o.getPlatformCommissionAmount() : null,
                    includePayoutDetails ? o.getPharmacyPayoutAmount() : null,
                    o.getDeliveryAssignmentStatus(),
                    o.getDeliveryEtaMinutes(),
                    o.isRequiresPrescription(),
                    o.isAllPrescriptionsVerified(),
                    cancellable,
                    o.isRefundEligible(),
                    o.getRefundStatus(),
                    o.getRefundReason(),
                    o.getCancellationReason(),
                    o.getCancelledByRole(),
                    o.getPlacedAt(),
                    o.getAcceptedAt(),
                    o.getPreparedAt(),
                    o.getReadyAt(),
                    o.getCancelledAt(),
                    o.getCreatedAt(),
                    items.stream().map(OrderItemResponse::from).toList(),
                    history.stream().map(OrderStatusHistoryResponse::from).toList()
            );
        }
    }
}
