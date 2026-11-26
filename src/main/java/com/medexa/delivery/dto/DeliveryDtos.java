package com.medexa.delivery.dto;

import com.medexa.delivery.AssignmentAttemptStatus;
import com.medexa.delivery.Delivery;
import com.medexa.delivery.DeliveryAssignmentAttempt;
import com.medexa.delivery.DeliveryEvent;
import com.medexa.delivery.DeliveryStatus;
import com.medexa.delivery.ProofType;
import com.medexa.order.DeliveryAssignmentStatus;
import com.medexa.order.Order;
import com.medexa.order.OrderStatus;
import com.medexa.order.dto.OrderDtos.OrderStatusHistoryResponse;
import com.medexa.rider.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class DeliveryDtos {

    private DeliveryDtos() {
    }

    public record RiderDecisionRequest(
            @NotNull Boolean accepted,
            @Size(max = 500) String reason
    ) {
    }

    public record CompleteDeliveryOtpRequest(
            @NotBlank(message = "Delivery OTP is required.") @Size(min = 4, max = 10) String otp,
            @Size(max = 150) String recipientName,
            @Size(max = 500) String notes
    ) {
    }

    public record ReportDeliveryFailureRequest(
            @NotBlank(message = "Failure reason is required.") @Size(min = 3, max = 500) String reason
    ) {
    }

    public record AdminAssignRiderRequest(
            @NotNull Long riderId,
            @Size(max = 500) String notes
    ) {
    }

    public record DeliveryEventResponse(
            Long id,
            String eventType,
            DeliveryStatus previousStatus,
            DeliveryStatus newStatus,
            String actorRole,
            String actorName,
            String notes,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant createdAt
    ) {
        public static DeliveryEventResponse from(DeliveryEvent e) {
            return new DeliveryEventResponse(
                    e.getId(),
                    e.getEventType(),
                    e.getPreviousStatus(),
                    e.getNewStatus(),
                    e.getActorRole(),
                    e.getActorName(),
                    e.getNotes(),
                    e.getLatitude(),
                    e.getLongitude(),
                    e.getCreatedAt()
            );
        }
    }

    public record DeliveryAttemptResponse(
            Long id,
            Long riderId,
            String riderName,
            int attemptNumber,
            AssignmentAttemptStatus status,
            BigDecimal distanceKm,
            BigDecimal rankingScore,
            Instant offeredAt,
            Instant expiresAt,
            Instant respondedAt,
            String rejectionReason
    ) {
        public static DeliveryAttemptResponse from(DeliveryAssignmentAttempt a) {
            return new DeliveryAttemptResponse(
                    a.getId(),
                    a.getRider().getId(),
                    a.getRider().getUser().getFullName(),
                    a.getAttemptNumber(),
                    a.getStatus(),
                    a.getDistanceKm(),
                    a.getRankingScore(),
                    a.getOfferedAt(),
                    a.getExpiresAt(),
                    a.getRespondedAt(),
                    a.getRejectionReason()
            );
        }
    }

    public record RiderDeliveryResponse(
            Long id,
            String deliveryReference,
            Long orderId,
            String orderNumber,
            DeliveryStatus status,
            int assignmentAttempts,
            Instant assignedAt,
            Instant assignmentExpiresAt,
            Instant acceptedAt,
            Instant pickedUpAt,
            Instant outForDeliveryAt,
            Instant deliveredAt,
            Instant failedAt,
            BigDecimal distanceKm,
            BigDecimal estimatedEarning,
            String pickupPostalCode,
            String dropPostalCode,
            Long pharmacyId,
            String pharmacyName,
            String pharmacyPhone,
            String pharmacyAddress,
            String pharmacyCity,
            String pharmacyPostalCode,
            boolean fullCustomerAddressRevealed,
            String customerName,
            String customerPhone,
            String customerAddress,
            String customerCity,
            String customerState,
            String customerPostalCode,
            boolean otpPendingVerification,
            int otpAttemptsRemaining,
            Instant otpExpiresAt,
            ProofType proofType,
            boolean hasDeliveryProof,
            String deliveryNotes,
            String failureReason,
            List<DeliveryEventResponse> events
    ) {
        public static RiderDeliveryResponse from(
                Delivery d,
                BigDecimal estimatedEarning,
                List<DeliveryEvent> events) {
            Order o = d.getOrder();
            // Reveal full doorstep customer address & phone strictly once rider has accepted
            boolean revealDropDetails = d.getStatus() == DeliveryStatus.ACCEPTED
                    || d.getStatus() == DeliveryStatus.PICKED_UP
                    || d.getStatus() == DeliveryStatus.OUT_FOR_DELIVERY
                    || d.getStatus() == DeliveryStatus.DELIVERED;

            return new RiderDeliveryResponse(
                    d.getId(),
                    d.getDeliveryReference(),
                    o.getId(),
                    o.getOrderNumber(),
                    d.getStatus(),
                    d.getAssignmentAttempts(),
                    d.getAssignedAt(),
                    d.getAssignmentExpiresAt(),
                    d.getAcceptedAt(),
                    d.getPickedUpAt(),
                    d.getOutForDeliveryAt(),
                    d.getDeliveredAt(),
                    d.getFailedAt(),
                    d.getDistanceKm(),
                    estimatedEarning,
                    d.getPickupPostalCode(),
                    d.getDropPostalCode(),
                    d.getPharmacy().getId(),
                    d.getPharmacy().getPharmacyName(),
                    d.getPharmacy().getContactPhone(),
                    d.getPharmacy().getAddressLine(),
                    d.getPharmacy().getCity(),
                    d.getPharmacy().getPostalCode(),
                    revealDropDetails,
                    revealDropDetails ? o.getShippingRecipientName() : "Customer (Revealed upon acceptance)",
                    revealDropDetails ? o.getShippingPhone() : null,
                    revealDropDetails
                            ? o.getShippingAddress()
                            : "Area: " + o.getShippingCity() + " - " + o.getShippingPostalCode() + " (Accept pickup to unlock doorstep address)",
                    o.getShippingCity(),
                    o.getShippingState(),
                    o.getShippingPostalCode(),
                    d.getStatus() == DeliveryStatus.OUT_FOR_DELIVERY && d.getOtpHash() != null,
                    Math.max(0, d.getMaxOtpAttempts() - d.getOtpAttempts()),
                    d.getOtpExpiresAt(),
                    d.getProofType(),
                    d.getDeliveryProofUrl() != null && !d.getDeliveryProofUrl().isBlank(),
                    d.getDeliveryNotes(),
                    d.getFailureReason(),
                    events.stream().map(DeliveryEventResponse::from).toList()
            );
        }
    }

    public record CustomerTrackingResponse(
            Long orderId,
            String orderNumber,
            OrderStatus orderStatus,
            DeliveryAssignmentStatus deliveryAssignmentStatus,
            boolean hasDelivery,
            Long deliveryId,
            String deliveryReference,
            DeliveryStatus deliveryStatus,
            Integer deliveryEtaMinutes,
            BigDecimal distanceKm,
            Instant placedAt,
            Instant acceptedAt,
            Instant preparedAt,
            Instant readyAt,
            String assignedRiderName,
            VehicleType assignedRiderVehicleType,
            String assignedRiderVehicleNumber,
            String assignedRiderPhone,
            BigDecimal riderLatitude,
            BigDecimal riderLongitude,
            Instant riderLocationUpdatedAt,
            String deliveryOtp,
            Instant otpExpiresAt,
            int otpAttempts,
            int maxOtpAttempts,
            String otpInstructions,
            boolean hasDeliveryProof,
            ProofType proofType,
            Instant deliveredAt,
            String failureReason,
            List<OrderStatusHistoryResponse> orderTimeline,
            List<DeliveryEventResponse> deliveryTimeline
    ) {
    }

    public record AdminDeliveryResponse(
            Long id,
            String deliveryReference,
            Long orderId,
            String orderNumber,
            String checkoutReference,
            OrderStatus orderStatus,
            Long pharmacyId,
            String pharmacyName,
            String pharmacyPostalCode,
            Long customerId,
            String customerName,
            String dropPostalCode,
            Long assignedRiderId,
            String assignedRiderName,
            String assignedRiderPhone,
            DeliveryStatus status,
            BigDecimal distanceKm,
            int assignmentAttempts,
            Instant assignedAt,
            Instant assignmentExpiresAt,
            Instant acceptedAt,
            Instant pickedUpAt,
            Instant outForDeliveryAt,
            Instant deliveredAt,
            Instant failedAt,
            int otpAttempts,
            int maxOtpAttempts,
            ProofType proofType,
            boolean hasDeliveryProof,
            String recipientConfirmedName,
            String failureReason,
            BigDecimal riderEarningAmount,
            List<DeliveryAttemptResponse> attempts,
            List<DeliveryEventResponse> events
    ) {
    }
}
