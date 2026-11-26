package com.medexa.delivery.service;

import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.BusinessRuleException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.common.storage.FileStorageService;
import com.medexa.delivery.AssignmentAttemptStatus;
import com.medexa.delivery.Delivery;
import com.medexa.delivery.DeliveryAssignmentAttempt;
import com.medexa.delivery.DeliveryAssignmentAttemptRepository;
import com.medexa.delivery.DeliveryEvent;
import com.medexa.delivery.DeliveryEventRepository;
import com.medexa.delivery.DeliveryRepository;
import com.medexa.delivery.DeliveryStatus;
import com.medexa.delivery.ProofType;
import com.medexa.delivery.config.DeliveryProperties;
import com.medexa.delivery.dto.DeliveryDtos.AdminDeliveryResponse;
import com.medexa.delivery.dto.DeliveryDtos.CompleteDeliveryOtpRequest;
import com.medexa.delivery.dto.DeliveryDtos.CustomerTrackingResponse;
import com.medexa.delivery.dto.DeliveryDtos.DeliveryAttemptResponse;
import com.medexa.delivery.dto.DeliveryDtos.DeliveryEventResponse;
import com.medexa.delivery.dto.DeliveryDtos.ReportDeliveryFailureRequest;
import com.medexa.delivery.dto.DeliveryDtos.RiderDecisionRequest;
import com.medexa.delivery.dto.DeliveryDtos.RiderDeliveryResponse;
import com.medexa.order.DeliveryAssignmentStatus;
import com.medexa.order.Order;
import com.medexa.order.OrderItem;
import com.medexa.order.OrderItemRepository;
import com.medexa.order.OrderRepository;
import com.medexa.order.OrderStatus;
import com.medexa.order.OrderStatusHistory;
import com.medexa.order.OrderStatusHistoryRepository;
import com.medexa.order.dto.OrderDtos.OrderStatusHistoryResponse;
import com.medexa.prescription.service.PrescriptionService;
import com.medexa.rider.EarningStatus;
import com.medexa.rider.Rider;
import com.medexa.rider.RiderAvailabilityStatus;
import com.medexa.rider.RiderEarning;
import com.medexa.rider.RiderEarningRepository;
import com.medexa.rider.RiderRepository;
import com.medexa.rider.dto.RiderDtos.UpdateRiderLocationRequest;
import com.medexa.rider.service.RiderService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryLifecycleService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAssignmentAttemptRepository attemptRepository;
    private final DeliveryEventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final RiderRepository riderRepository;
    private final RiderEarningRepository earningRepository;
    private final RiderService riderService;
    private final DeliveryAssignmentService assignmentService;
    private final PrescriptionService prescriptionService;
    private final FileStorageService fileStorageService;
    private final DeliveryProperties deliveryProperties;

    @Transactional(readOnly = true)
    public List<RiderDeliveryResponse> listActiveDeliveriesForRider(Long riderUserId) {
        Rider rider = riderService.requireRiderByUserId(riderUserId);
        return deliveryRepository.findByAssignedRiderIdAndStatusInWithRefs(
                        rider.getId(), RiderService.ACTIVE_WORKLOAD_STATUSES
                ).stream()
                .map(d -> toRiderResponse(d, rider))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<RiderDeliveryResponse> listRiderDeliveryHistory(
            Long riderUserId,
            DeliveryStatus status,
            Pageable pageable) {
        Rider rider = riderService.requireRiderByUserId(riderUserId);
        return deliveryRepository.findForRider(rider.getId(), status, pageable)
                .map(d -> toRiderResponse(d, rider));
    }

    @Transactional(readOnly = true)
    public RiderDeliveryResponse getRiderDelivery(Long riderUserId, Long deliveryId) {
        Rider rider = riderService.requireRiderByUserId(riderUserId);
        Delivery delivery = requireAssignedDeliveryForRider(rider, deliveryId);
        return toRiderResponse(delivery, rider);
    }

    @Transactional
    public RiderDeliveryResponse decideAssignment(
            Long riderUserId,
            Long deliveryId,
            RiderDecisionRequest request) {
        Rider rider = riderService.requireApprovedActiveRider(riderUserId);
        Delivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery record not found."));

        if (delivery.getAssignedRider() == null || !delivery.getAssignedRider().getId().equals(rider.getId())) {
            throw new ResourceNotFoundException("Delivery is not assigned to this rider.");
        }
        if (delivery.getStatus() != DeliveryStatus.ASSIGNED) {
            throw new BusinessRuleException("Delivery can only be accepted or rejected while ASSIGNED.");
        }

        Instant now = Instant.now();
        Order order = delivery.getOrder();

        if (Boolean.TRUE.equals(request.accepted())) {
            if (delivery.getAssignmentExpiresAt() != null && delivery.getAssignmentExpiresAt().isBefore(now)) {
                throw new BusinessRuleException("This assignment offer has expired.");
            }
            attemptRepository.findFirstByDeliveryIdAndRiderIdAndStatusOrderByIdDesc(
                    delivery.getId(), rider.getId(), AssignmentAttemptStatus.OFFERED
            ).ifPresent(a -> {
                a.setStatus(AssignmentAttemptStatus.ACCEPTED);
                a.setRespondedAt(now);
                attemptRepository.save(a);
            });

            delivery.setStatus(DeliveryStatus.ACCEPTED);
            delivery.setAcceptedAt(now);
            delivery.setAssignmentExpiresAt(null);
            deliveryRepository.save(delivery);

            rider.setAvailabilityStatus(RiderAvailabilityStatus.ON_DELIVERY);
            riderRepository.save(rider);

            assignmentService.recordDeliveryEvent(
                    delivery,
                    "RIDER_ACCEPTED",
                    DeliveryStatus.ASSIGNED,
                    DeliveryStatus.ACCEPTED,
                    riderUserId,
                    "RIDER",
                    rider.getUser().getFullName(),
                    "Rider accepted pickup assignment for " + order.getOrderNumber()
            );

            return toRiderResponse(delivery, rider);
        }

        // Rejected -> mark attempt REJECTED and immediately trigger reassignment
        String reason = request.reason() != null && !request.reason().isBlank()
                ? request.reason().trim()
                : "Declined by rider";

        attemptRepository.findFirstByDeliveryIdAndRiderIdAndStatusOrderByIdDesc(
                delivery.getId(), rider.getId(), AssignmentAttemptStatus.OFFERED
        ).ifPresent(a -> {
            a.setStatus(AssignmentAttemptStatus.REJECTED);
            a.setRespondedAt(now);
            a.setRejectionReason(reason);
            attemptRepository.save(a);
        });

        delivery.setAssignedRider(null);
        delivery.setStatus(DeliveryStatus.REASSIGNMENT_REQUIRED);
        delivery.setAssignmentExpiresAt(null);
        deliveryRepository.save(delivery);

        if (order.getStatus() == OrderStatus.RIDER_ASSIGNED) {
            order.setStatus(OrderStatus.READY_FOR_PICKUP);
            order.setDeliveryAssignmentStatus(DeliveryAssignmentStatus.AWAITING_PICKUP);
            orderRepository.save(order);
        }

        assignmentService.recordDeliveryEvent(
                delivery,
                "RIDER_REJECTED",
                DeliveryStatus.ASSIGNED,
                DeliveryStatus.REASSIGNMENT_REQUIRED,
                riderUserId,
                "RIDER",
                rider.getUser().getFullName(),
                "Rider rejected delivery (" + reason + "); seeking next rider."
        );

        Delivery reassigned = assignmentService.assignBestAvailableRider(delivery, order, rider.getId());
        return toRiderResponse(reassigned, rider);
    }

    @Transactional
    public RiderDeliveryResponse confirmPickup(Long riderUserId, Long deliveryId, String notes) {
        Rider rider = riderService.requireApprovedActiveRider(riderUserId);
        Delivery delivery = requireAssignedDeliveryForRiderForUpdate(rider, deliveryId);

        if (delivery.getStatus() != DeliveryStatus.ACCEPTED) {
            throw new BusinessRuleException("Accept the delivery assignment before confirming pharmacy pickup.");
        }

        Order order = delivery.getOrder();
        verifyPrescriptionsBeforeDispatch(order);

        Instant now = Instant.now();
        delivery.setStatus(DeliveryStatus.PICKED_UP);
        delivery.setPickedUpAt(now);
        deliveryRepository.save(delivery);

        order.setDeliveryAssignmentStatus(DeliveryAssignmentStatus.DISPATCHED);
        orderRepository.save(order);

        assignmentService.recordDeliveryEvent(
                delivery,
                "PHARMACY_PICKUP_CONFIRMED",
                DeliveryStatus.ACCEPTED,
                DeliveryStatus.PICKED_UP,
                riderUserId,
                "RIDER",
                rider.getUser().getFullName(),
                notes != null && !notes.isBlank()
                        ? notes.trim()
                        : "Medicine parcel collected from " + delivery.getPharmacy().getPharmacyName()
        );

        return toRiderResponse(delivery, rider);
    }

    @Transactional
    public RiderDeliveryResponse markOutForDelivery(Long riderUserId, Long deliveryId, UpdateRiderLocationRequest loc) {
        Rider rider = riderService.requireApprovedActiveRider(riderUserId);
        Delivery delivery = requireAssignedDeliveryForRiderForUpdate(rider, deliveryId);

        if (delivery.getStatus() != DeliveryStatus.PICKED_UP) {
            throw new BusinessRuleException("Confirm pharmacy pickup before marking out for delivery.");
        }

        Order order = delivery.getOrder();
        verifyPrescriptionsBeforeDispatch(order);

        Instant now = Instant.now();
        if (loc != null) {
            delivery.setRiderLat(loc.latitude());
            delivery.setRiderLng(loc.longitude());
            delivery.setRiderLocationUpdatedAt(now);
            rider.setCurrentLatitude(loc.latitude());
            rider.setCurrentLongitude(loc.longitude());
            rider.setLocationUpdatedAt(now);
            riderRepository.save(rider);
        }

        issueDeliveryOtpInternal(delivery, now);
        delivery.setStatus(DeliveryStatus.OUT_FOR_DELIVERY);
        delivery.setOutForDeliveryAt(now);
        deliveryRepository.save(delivery);

        OrderStatus prevOrderStatus = order.getStatus();
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        order.setDeliveryAssignmentStatus(DeliveryAssignmentStatus.DISPATCHED);
        orderRepository.save(order);

        recordOrderHistory(
                order,
                prevOrderStatus,
                OrderStatus.OUT_FOR_DELIVERY,
                riderUserId,
                "RIDER",
                rider.getUser().getFullName(),
                "Rider is out for delivery; customer 6-digit delivery OTP generated."
        );

        assignmentService.recordDeliveryEvent(
                delivery,
                "OUT_FOR_DELIVERY",
                DeliveryStatus.PICKED_UP,
                DeliveryStatus.OUT_FOR_DELIVERY,
                riderUserId,
                "RIDER",
                rider.getUser().getFullName(),
                "Out for doorstep delivery; OTP issued (valid until " + delivery.getOtpExpiresAt() + ")"
        );

        return toRiderResponse(delivery, rider);
    }

    @Transactional
    public RiderDeliveryResponse updateLiveLocation(Long riderUserId, Long deliveryId, UpdateRiderLocationRequest loc) {
        Rider rider = riderService.requireApprovedActiveRider(riderUserId);
        Delivery delivery = requireAssignedDeliveryForRiderForUpdate(rider, deliveryId);

        Instant now = Instant.now();
        delivery.setRiderLat(loc.latitude());
        delivery.setRiderLng(loc.longitude());
        delivery.setRiderLocationUpdatedAt(now);
        deliveryRepository.save(delivery);

        rider.setCurrentLatitude(loc.latitude());
        rider.setCurrentLongitude(loc.longitude());
        rider.setLocationUpdatedAt(now);
        riderRepository.save(rider);

        return toRiderResponse(delivery, rider);
    }

    @Transactional
    public RiderDeliveryResponse uploadDeliveryProof(Long riderUserId, Long deliveryId, MultipartFile file) {
        Rider rider = riderService.requireApprovedActiveRider(riderUserId);
        Delivery delivery = requireAssignedDeliveryForRiderForUpdate(rider, deliveryId);

        if (delivery.getStatus() != DeliveryStatus.PICKED_UP
                && delivery.getStatus() != DeliveryStatus.OUT_FOR_DELIVERY
                && delivery.getStatus() != DeliveryStatus.DELIVERED) {
            throw new BusinessRuleException("Delivery proof can only be attached after pharmacy pickup.");
        }

        String path = fileStorageService.storeImage(FileStorageService.BUCKET_DELIVERY_PROOFS, file);
        delivery.setDeliveryProofUrl(path);
        deliveryRepository.save(delivery);

        assignmentService.recordDeliveryEvent(
                delivery,
                "PROOF_ATTACHED",
                delivery.getStatus(),
                delivery.getStatus(),
                riderUserId,
                "RIDER",
                rider.getUser().getFullName(),
                "Handover proof photograph uploaded."
        );

        return toRiderResponse(delivery, rider);
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public RiderDeliveryResponse completeDeliveryWithOtp(
            Long riderUserId,
            Long deliveryId,
            CompleteDeliveryOtpRequest request) {
        Rider rider = riderService.requireApprovedActiveRider(riderUserId);
        Delivery delivery = requireAssignedDeliveryForRiderForUpdate(rider, deliveryId);

        if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
            return toRiderResponse(delivery, rider);
        }
        if (delivery.getStatus() != DeliveryStatus.OUT_FOR_DELIVERY) {
            throw new BusinessRuleException("Mark delivery as OUT_FOR_DELIVERY before completing with OTP.");
        }
        if (delivery.getOtpHash() == null || delivery.getOtpExpiresAt() == null) {
            throw new BusinessRuleException("No active delivery OTP exists. Ask customer to refresh OTP.");
        }

        Instant now = Instant.now();
        if (delivery.getOtpExpiresAt().isBefore(now)) {
            throw new BusinessRuleException("Delivery OTP has expired. Ask customer to generate a fresh OTP.");
        }
        if (delivery.getOtpAttempts() >= delivery.getMaxOtpAttempts()) {
            throw new BusinessRuleException("Maximum OTP verification attempts reached. Ask customer to generate a fresh OTP.");
        }

        String candidateHash = sha256(delivery.getDeliveryReference() + "|" + request.otp().trim());
        if (!MessageDigest.isEqual(
                candidateHash.getBytes(StandardCharsets.UTF_8),
                delivery.getOtpHash().getBytes(StandardCharsets.UTF_8))) {
            int nextAttempts = delivery.getOtpAttempts() + 1;
            delivery.setOtpAttempts(nextAttempts);
            deliveryRepository.save(delivery);
            assignmentService.recordDeliveryEvent(
                    delivery,
                    "OTP_VERIFICATION_FAILED",
                    DeliveryStatus.OUT_FOR_DELIVERY,
                    DeliveryStatus.OUT_FOR_DELIVERY,
                    riderUserId,
                    "RIDER",
                    rider.getUser().getFullName(),
                    "Incorrect delivery OTP entered (" + nextAttempts + "/" + delivery.getMaxOtpAttempts() + ")"
            );
            throw new BusinessRuleException(
                    "Invalid delivery OTP. Attempts used: " + nextAttempts + " of " + delivery.getMaxOtpAttempts() + "."
            );
        }

        // OTP matched: finalize delivery + release OTP plaintext + accrue rider earning
        delivery.setOtpVerifiedAt(now);
        delivery.setCustomerOtpCode(null);
        delivery.setOtpHash(null);
        delivery.setStatus(DeliveryStatus.DELIVERED);
        delivery.setDeliveredAt(now);
        delivery.setProofType(delivery.getDeliveryProofUrl() != null ? ProofType.PHOTO_AND_OTP : ProofType.OTP_VERIFIED);
        delivery.setRecipientConfirmedName(
                request.recipientName() != null && !request.recipientName().isBlank()
                        ? request.recipientName().trim()
                        : delivery.getOrder().getShippingRecipientName()
        );
        delivery.setDeliveryNotes(request.notes() != null ? request.notes().trim() : null);

        BigDecimal baseFee = rider.getBaseFeePerDelivery() != null
                ? rider.getBaseFeePerDelivery()
                : deliveryProperties.getDefaultBaseFee();
        BigDecimal distanceBonus = delivery.getDistanceKm()
                .multiply(deliveryProperties.getPerKmFee())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalEarning = baseFee.add(distanceBonus);
        delivery.setRiderEarningAmount(totalEarning);
        deliveryRepository.save(delivery);

        if (earningRepository.findByDeliveryId(delivery.getId()).isEmpty()) {
            earningRepository.save(RiderEarning.builder()
                    .earningReference("ERN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .rider(rider)
                    .delivery(delivery)
                    .order(delivery.getOrder())
                    .baseFee(baseFee)
                    .distanceBonus(distanceBonus)
                    .incentiveAmount(BigDecimal.ZERO)
                    .totalEarning(totalEarning)
                    .status(EarningStatus.ACCRUED)
                    .earnedAt(now)
                    .build());
        }

        Order order = delivery.getOrder();
        OrderStatus prevOrderStatus = order.getStatus();
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveryAssignmentStatus(DeliveryAssignmentStatus.DELIVERED);
        orderRepository.save(order);

        recordOrderHistory(
                order,
                prevOrderStatus,
                OrderStatus.DELIVERED,
                riderUserId,
                "RIDER",
                rider.getUser().getFullName(),
                "Delivered and verified via customer OTP (" + delivery.getDeliveryReference() + ")"
        );

        assignmentService.recordDeliveryEvent(
                delivery,
                "DELIVERED_OTP_VERIFIED",
                DeliveryStatus.OUT_FOR_DELIVERY,
                DeliveryStatus.DELIVERED,
                riderUserId,
                "RIDER",
                rider.getUser().getFullName(),
                "Handover confirmed via customer OTP to " + delivery.getRecipientConfirmedName()
        );

        // If rider has no more active ON_DELIVERY workload, return status cleanly to ONLINE
        long remaining = deliveryRepository.countByAssignedRiderIdAndStatusIn(
                rider.getId(), RiderService.ACTIVE_WORKLOAD_STATUSES
        );
        if (remaining == 0 && rider.getAvailabilityStatus() == RiderAvailabilityStatus.ON_DELIVERY) {
            rider.setAvailabilityStatus(RiderAvailabilityStatus.ONLINE);
            riderRepository.save(rider);
        }

        return toRiderResponse(delivery, rider);
    }

    @Transactional
    public RiderDeliveryResponse reportDeliveryFailure(
            Long riderUserId,
            Long deliveryId,
            ReportDeliveryFailureRequest request) {
        Rider rider = riderService.requireApprovedActiveRider(riderUserId);
        Delivery delivery = requireAssignedDeliveryForRiderForUpdate(rider, deliveryId);

        if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
            throw new BusinessRuleException("Completed delivery cannot be marked failed.");
        }

        Instant now = Instant.now();
        DeliveryStatus prev = delivery.getStatus();
        String reason = request.reason().trim();

        delivery.setStatus(DeliveryStatus.FAILED);
        delivery.setFailedAt(now);
        delivery.setFailureReason(reason);
        delivery.setCustomerOtpCode(null);
        delivery.setOtpHash(null);
        deliveryRepository.save(delivery);

        Order order = delivery.getOrder();
        order.setDeliveryAssignmentStatus(DeliveryAssignmentStatus.CANCELLED);
        orderRepository.save(order);

        recordOrderHistory(
                order,
                order.getStatus(),
                order.getStatus(),
                riderUserId,
                "RIDER",
                rider.getUser().getFullName(),
                "Delivery failed (" + delivery.getDeliveryReference() + "): " + reason
        );

        assignmentService.recordDeliveryEvent(
                delivery,
                "DELIVERY_FAILED",
                prev,
                DeliveryStatus.FAILED,
                riderUserId,
                "RIDER",
                rider.getUser().getFullName(),
                reason
        );

        long remaining = deliveryRepository.countByAssignedRiderIdAndStatusIn(
                rider.getId(), RiderService.ACTIVE_WORKLOAD_STATUSES
        );
        if (remaining == 0 && rider.getAvailabilityStatus() == RiderAvailabilityStatus.ON_DELIVERY) {
            rider.setAvailabilityStatus(RiderAvailabilityStatus.ONLINE);
            riderRepository.save(rider);
        }

        return toRiderResponse(delivery, rider);
    }

    // Customer Tracking & OTP Controls

    @Transactional(readOnly = true)
    public CustomerTrackingResponse getCustomerTracking(Long customerId, Long orderId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        return buildCustomerTrackingResponse(order);
    }

    @Transactional
    public CustomerTrackingResponse regenerateCustomerOtp(Long customerId, Long orderId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        Delivery delivery = deliveryRepository.findByOrderIdForUpdate(order.getId())
                .orElseThrow(() -> new BusinessRuleException("Delivery has not been dispatched yet."));
        if (delivery.getStatus() != DeliveryStatus.OUT_FOR_DELIVERY) {
            throw new BusinessRuleException("Delivery OTP can only be refreshed while the order is OUT_FOR_DELIVERY.");
        }

        issueDeliveryOtpInternal(delivery, Instant.now());
        deliveryRepository.save(delivery);

        assignmentService.recordDeliveryEvent(
                delivery,
                "OTP_REFRESHED",
                DeliveryStatus.OUT_FOR_DELIVERY,
                DeliveryStatus.OUT_FOR_DELIVERY,
                customerId,
                "CUSTOMER",
                order.getCustomer().getFullName(),
                "Fresh 6-digit delivery OTP generated by customer."
        );

        return buildCustomerTrackingResponse(order);
    }

    // Admin Oversight

    @Transactional(readOnly = true)
    public Page<AdminDeliveryResponse> listAdminDeliveries(
            DeliveryStatus status,
            Long riderId,
            boolean exceptionsOnly,
            String query,
            Pageable pageable) {
        return deliveryRepository.findForAdmin(status, riderId, exceptionsOnly, query, pageable)
                .map(this::toAdminResponse);
    }

    @Transactional(readOnly = true)
    public AdminDeliveryResponse getAdminDelivery(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery record not found."));
        return toAdminResponse(delivery);
    }

    @Transactional(readOnly = true)
    public Resource loadDeliveryProofResource(Long actorUserId, String role, Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery record not found."));

        boolean authorized = "ADMIN".equals(role)
                || ("CUSTOMER".equals(role) && delivery.getCustomer().getId().equals(actorUserId))
                || ("RIDER".equals(role)
                    && delivery.getAssignedRider() != null
                    && delivery.getAssignedRider().getUser().getId().equals(actorUserId));

        if (!authorized) {
            throw new ResourceNotFoundException("Delivery proof not found.");
        }
        if (delivery.getDeliveryProofUrl() == null || delivery.getDeliveryProofUrl().isBlank()) {
            throw new ResourceNotFoundException("No delivery proof image attached for this delivery.");
        }
        String path = delivery.getDeliveryProofUrl();
        int idx = path.lastIndexOf('/');
        String filename = idx >= 0 ? path.substring(idx + 1) : path;
        return fileStorageService.loadAsResource(FileStorageService.BUCKET_DELIVERY_PROOFS, filename);
    }

    // Helpers

    private CustomerTrackingResponse buildCustomerTrackingResponse(Order order) {
        Delivery delivery = deliveryRepository.findByOrderId(order.getId()).orElse(null);
        List<OrderStatusHistory> orderTimeline =
                orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId());

        if (delivery == null) {
            return new CustomerTrackingResponse(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getStatus(),
                    order.getDeliveryAssignmentStatus(),
                    false,
                    null,
                    null,
                    null,
                    order.getDeliveryEtaMinutes(),
                    null,
                    order.getPlacedAt(),
                    order.getAcceptedAt(),
                    order.getPreparedAt(),
                    order.getReadyAt(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    deliveryProperties.getMaxOtpAttempts(),
                    "Your delivery OTP will appear once a rider picks up your medicines and marks them OUT_FOR_DELIVERY.",
                    false,
                    null,
                    null,
                    null,
                    orderTimeline.stream().map(OrderStatusHistoryResponse::from).toList(),
                    List.of()
            );
        }

        List<DeliveryEvent> events = eventRepository.findByDeliveryIdOrderByCreatedAtAscIdAsc(delivery.getId());
        Rider rider = delivery.getAssignedRider();
        boolean exposeOtp = delivery.getStatus() == DeliveryStatus.OUT_FOR_DELIVERY
                && delivery.getCustomerOtpCode() != null
                && delivery.getOtpExpiresAt() != null
                && delivery.getOtpExpiresAt().isAfter(Instant.now());

        String otpInstructions = switch (delivery.getStatus()) {
            case WAITING_FOR_RIDER, REASSIGNMENT_REQUIRED ->
                "Your order is packed and awaiting rider assignment.";
            case ASSIGNED ->
                "A Medexa rider has been matched and is confirming pickup.";
            case ACCEPTED ->
                "Rider is on the way to " + delivery.getPharmacy().getPharmacyName() + " for pickup.";
            case PICKED_UP ->
                "Medicines collected from pharmacy. Delivery OTP will activate once dispatch starts.";
            case OUT_FOR_DELIVERY -> exposeOtp
                ? "Share this 6-digit OTP only with your Medexa rider when inspecting and receiving your sealed medicine package."
                : "Your OTP expired or reached maximum retries. Generate a fresh OTP below before handover.";
            case DELIVERED ->
                "Order delivered and confirmed via OTP verification.";
            case FAILED ->
                "Delivery encountered an exception. Support or automatic reassignment will follow up.";
        };

        return new CustomerTrackingResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getDeliveryAssignmentStatus(),
                true,
                delivery.getId(),
                delivery.getDeliveryReference(),
                delivery.getStatus(),
                order.getDeliveryEtaMinutes(),
                delivery.getDistanceKm(),
                order.getPlacedAt(),
                order.getAcceptedAt(),
                order.getPreparedAt(),
                order.getReadyAt(),
                rider != null ? rider.getUser().getFullName() : null,
                rider != null ? rider.getVehicleType() : null,
                rider != null ? rider.getVehicleRegistrationNumber() : null,
                rider != null ? rider.getPhoneNumber() : null,
                delivery.getRiderLat(),
                delivery.getRiderLng(),
                delivery.getRiderLocationUpdatedAt(),
                exposeOtp ? delivery.getCustomerOtpCode() : null,
                delivery.getOtpExpiresAt(),
                delivery.getOtpAttempts(),
                delivery.getMaxOtpAttempts(),
                otpInstructions,
                delivery.getDeliveryProofUrl() != null && !delivery.getDeliveryProofUrl().isBlank(),
                delivery.getProofType(),
                delivery.getDeliveredAt(),
                delivery.getFailureReason(),
                orderTimeline.stream().map(OrderStatusHistoryResponse::from).toList(),
                events.stream().map(DeliveryEventResponse::from).toList()
        );
    }

    private Delivery requireAssignedDeliveryForRider(Rider rider, Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery record not found."));
        if (delivery.getAssignedRider() == null || !delivery.getAssignedRider().getId().equals(rider.getId())) {
            throw new ResourceNotFoundException("Delivery is not assigned to this rider.");
        }
        return delivery;
    }

    private Delivery requireAssignedDeliveryForRiderForUpdate(Rider rider, Long deliveryId) {
        Delivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery record not found."));
        if (delivery.getAssignedRider() == null || !delivery.getAssignedRider().getId().equals(rider.getId())) {
            throw new ResourceNotFoundException("Delivery is not assigned to this rider.");
        }
        return delivery;
    }

    private void verifyPrescriptionsBeforeDispatch(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdWithRefs(order.getId());
        for (OrderItem item : items) {
            if (item.isPrescriptionRequired()) {
                Long rxId = item.getPrescription() != null ? item.getPrescription().getId() : null;
                prescriptionService.checkDispatchEligibility(
                        order.getCustomer().getId(),
                        item.getMedicine().getId(),
                        rxId,
                        true
                );
            }
        }
    }

    private void issueDeliveryOtpInternal(Delivery delivery, Instant now) {
        int codeNum = 100000 + SECURE_RANDOM.nextInt(900000);
        String otp = String.valueOf(codeNum);
        delivery.setCustomerOtpCode(otp);
        delivery.setOtpHash(sha256(delivery.getDeliveryReference() + "|" + otp));
        delivery.setOtpExpiresAt(now.plus(deliveryProperties.getOtpExpiryMinutes(), ChronoUnit.MINUTES));
        delivery.setOtpAttempts(0);
        delivery.setMaxOtpAttempts(deliveryProperties.getMaxOtpAttempts());
    }

    private void recordOrderHistory(
            Order order,
            OrderStatus prev,
            OrderStatus next,
            Long actorUserId,
            String actorRole,
            String actorName,
            String notes) {
        orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                .order(order)
                .previousStatus(prev)
                .newStatus(next)
                .actorUserId(actorUserId)
                .actorRole(actorRole)
                .actorName(actorName)
                .notes(notes)
                .build());
    }

    private RiderDeliveryResponse toRiderResponse(Delivery d, Rider rider) {
        List<DeliveryEvent> events = eventRepository.findByDeliveryIdOrderByCreatedAtAscIdAsc(d.getId());
        BigDecimal estEarning = d.getRiderEarningAmount() != null
                && d.getRiderEarningAmount().compareTo(BigDecimal.ZERO) > 0
                ? d.getRiderEarningAmount()
                : rider.getBaseFeePerDelivery();
        return RiderDeliveryResponse.from(d, estEarning, events);
    }

    private AdminDeliveryResponse toAdminResponse(Delivery d) {
        List<DeliveryAttemptResponse> attempts = attemptRepository
                .findByDeliveryIdWithRiderOrderByAttemptNumberAsc(d.getId())
                .stream().map(DeliveryAttemptResponse::from).toList();
        List<DeliveryEventResponse> events = eventRepository
                .findByDeliveryIdOrderByCreatedAtAscIdAsc(d.getId())
                .stream().map(DeliveryEventResponse::from).toList();
        Rider rider = d.getAssignedRider();

        return new AdminDeliveryResponse(
                d.getId(),
                d.getDeliveryReference(),
                d.getOrder().getId(),
                d.getOrder().getOrderNumber(),
                d.getOrder().getCheckoutSession().getCheckoutReference(),
                d.getOrder().getStatus(),
                d.getPharmacy().getId(),
                d.getPharmacy().getPharmacyName(),
                d.getPickupPostalCode(),
                d.getCustomer().getId(),
                d.getCustomer().getFullName(),
                d.getDropPostalCode(),
                rider != null ? rider.getId() : null,
                rider != null ? rider.getUser().getFullName() : null,
                rider != null ? rider.getPhoneNumber() : null,
                d.getStatus(),
                d.getDistanceKm(),
                d.getAssignmentAttempts(),
                d.getAssignedAt(),
                d.getAssignmentExpiresAt(),
                d.getAcceptedAt(),
                d.getPickedUpAt(),
                d.getOutForDeliveryAt(),
                d.getDeliveredAt(),
                d.getFailedAt(),
                d.getOtpAttempts(),
                d.getMaxOtpAttempts(),
                d.getProofType(),
                d.getDeliveryProofUrl() != null && !d.getDeliveryProofUrl().isBlank(),
                d.getRecipientConfirmedName(),
                d.getFailureReason(),
                d.getRiderEarningAmount(),
                attempts,
                events
        );
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash delivery OTP.", ex);
        }
    }
}
