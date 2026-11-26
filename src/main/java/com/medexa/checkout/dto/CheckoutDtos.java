package com.medexa.checkout.dto;

import com.medexa.checkout.CheckoutSession;
import com.medexa.checkout.CheckoutStatus;
import com.medexa.order.dto.OrderDtos.OrderResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CheckoutDtos {

    private CheckoutDtos() {
    }

    public record CheckoutPreviewRequest(
            Long addressId,

            @Size(max = 40)
            String couponCode
    ) {
    }

    public record CreateCheckoutSessionRequest(
            @NotNull(message = "Delivery address is required for checkout.")
            Long addressId,

            @Size(max = 40)
            String couponCode,

            @Size(max = 100)
            String idempotencyKey
    ) {
    }

    public record ConfirmPaymentRequest(
            @NotBlank(message = "Payment ID is required.")
            String razorpayPaymentId,

            @NotBlank(message = "Gateway order ID is required.")
            String razorpayOrderId,

            @NotBlank(message = "Payment verification signature is required.")
            String razorpaySignature
    ) {
    }

    public record CancelCheckoutRequest(
            @Size(max = 500)
            String reason
    ) {
    }

    public record PharmacyCheckoutBreakdown(
            Long pharmacyId,
            String pharmacyName,
            String pharmacyCity,
            int itemCount,
            BigDecimal itemsSubtotal,
            BigDecimal discountAllocated,
            BigDecimal deliveryFee,
            BigDecimal taxAmount,
            BigDecimal grandTotal,
            BigDecimal estimatedCommissionAmount,
            BigDecimal estimatedPayoutAmount,
            Integer estimatedDeliveryMinutes,
            boolean serviceable,
            boolean readyForCheckout,
            List<String> blockers
    ) {
    }

    public record CheckoutPreviewResponse(
            Long addressId,
            String postalCode,
            String couponCode,
            boolean couponApplied,
            String couponMessage,
            BigDecimal itemsSubtotal,
            BigDecimal discountAmount,
            BigDecimal deliveryFeeTotal,
            BigDecimal taxRatePercent,
            BigDecimal taxAmount,
            BigDecimal grandTotal,
            boolean checkoutAllowed,
            List<String> blockers,
            List<PharmacyCheckoutBreakdown> pharmacyBreakdowns
    ) {
    }

    public record CheckoutSessionResponse(
            Long id,
            String checkoutReference,
            CheckoutStatus status,
            Long addressId,
            String deliveryRecipientName,
            String deliveryPhone,
            String deliveryAddressLine1,
            String deliveryAddressLine2,
            String deliveryCity,
            String deliveryState,
            String deliveryPostalCode,
            String couponCode,
            BigDecimal itemsSubtotal,
            BigDecimal discountAmount,
            BigDecimal deliveryFeeTotal,
            BigDecimal taxAmount,
            BigDecimal grandTotal,
            long amountInPaise,
            String currency,
            String razorpayKeyId,
            String razorpayOrderId,
            String paymentReference,
            Instant expiresAt,
            Instant paidAt,
            Instant cancelledAt,
            String failureReason,
            Instant createdAt,
            List<OrderResponse> orders
    ) {
        public static CheckoutSessionResponse from(
                CheckoutSession session,
                String razorpayKeyId,
                List<OrderResponse> orders) {
            long paise = session.getGrandTotal()
                    .multiply(BigDecimal.valueOf(100))
                    .longValue();
            return new CheckoutSessionResponse(
                    session.getId(),
                    session.getCheckoutReference(),
                    session.getStatus(),
                    session.getAddress() != null ? session.getAddress().getId() : null,
                    session.getDeliveryRecipientName(),
                    session.getDeliveryPhone(),
                    session.getDeliveryAddressLine1(),
                    session.getDeliveryAddressLine2(),
                    session.getDeliveryCity(),
                    session.getDeliveryState(),
                    session.getDeliveryPostalCode(),
                    session.getCouponCode(),
                    session.getItemsSubtotal(),
                    session.getDiscountAmount(),
                    session.getDeliveryFeeTotal(),
                    session.getTaxAmount(),
                    session.getGrandTotal(),
                    paise,
                    session.getCurrency(),
                    razorpayKeyId != null && !razorpayKeyId.isBlank() ? razorpayKeyId : null,
                    session.getRazorpayOrderId(),
                    session.getPaymentReference(),
                    session.getExpiresAt(),
                    session.getPaidAt(),
                    session.getCancelledAt(),
                    session.getFailureReason(),
                    session.getCreatedAt(),
                    orders
            );
        }
    }
}
