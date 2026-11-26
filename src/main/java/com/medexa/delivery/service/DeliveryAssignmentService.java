package com.medexa.delivery.service;

import com.medexa.common.exception.BusinessRuleException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.delivery.AssignmentAttemptStatus;
import com.medexa.delivery.Delivery;
import com.medexa.delivery.DeliveryAssignmentAttempt;
import com.medexa.delivery.DeliveryAssignmentAttemptRepository;
import com.medexa.delivery.DeliveryEvent;
import com.medexa.delivery.DeliveryEventRepository;
import com.medexa.delivery.DeliveryRepository;
import com.medexa.delivery.DeliveryStatus;
import com.medexa.delivery.config.DeliveryProperties;
import com.medexa.order.DeliveryAssignmentStatus;
import com.medexa.order.Order;
import com.medexa.order.OrderRepository;
import com.medexa.order.OrderStatus;
import com.medexa.order.OrderStatusHistory;
import com.medexa.order.OrderStatusHistoryRepository;
import com.medexa.pharmacy.VerificationStatus;
import com.medexa.rider.Rider;
import com.medexa.rider.RiderAvailabilityStatus;
import com.medexa.rider.RiderDeliveryZone;
import com.medexa.rider.RiderDeliveryZoneRepository;
import com.medexa.rider.RiderRepository;
import com.medexa.rider.service.RiderService;
import com.medexa.user.AccountStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryAssignmentService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAssignmentAttemptRepository attemptRepository;
    private final DeliveryEventRepository eventRepository;
    private final RiderRepository riderRepository;
    private final RiderDeliveryZoneRepository zoneRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final DistanceCalculator distanceCalculator;
    private final DeliveryProperties deliveryProperties;

    @Transactional
    public Delivery initiateOrRetryAssignment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));

        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.PHARMACY_REJECTED
                || order.getStatus() == OrderStatus.FAILED
                || order.getStatus() == OrderStatus.DELIVERED) {
            return deliveryRepository.findByOrderId(orderId).orElse(null);
        }

        Delivery delivery = deliveryRepository.findByOrderIdForUpdate(orderId)
                .orElseGet(() -> createDeliveryRecord(order));

        if (delivery.getStatus() == DeliveryStatus.ACCEPTED
                || delivery.getStatus() == DeliveryStatus.PICKED_UP
                || delivery.getStatus() == DeliveryStatus.OUT_FOR_DELIVERY
                || delivery.getStatus() == DeliveryStatus.DELIVERED) {
            return delivery;
        }

        return assignBestAvailableRider(delivery, order, null);
    }

    @Transactional
    public Delivery assignSpecificRiderByAdmin(Long adminUserId, Long deliveryId, Long riderId, String notes) {
        Delivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery record not found."));
        if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
            throw new BusinessRuleException("Completed deliveries cannot be reassigned.");
        }
        Rider rider = riderRepository.findById(riderId)
                .orElseThrow(() -> new ResourceNotFoundException("Rider not found."));
        if (rider.getVerificationStatus() != VerificationStatus.APPROVED
                || rider.getUser().getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Target rider must be verified and active.");
        }

        Order order = delivery.getOrder();
        if (delivery.getAssignedRider() != null && !delivery.getAssignedRider().getId().equals(rider.getId())) {
            attemptRepository.findFirstByDeliveryIdAndRiderIdAndStatusOrderByIdDesc(
                    delivery.getId(), delivery.getAssignedRider().getId(), AssignmentAttemptStatus.OFFERED
            ).ifPresent(prevAttempt -> {
                prevAttempt.setStatus(AssignmentAttemptStatus.REASSIGNED);
                prevAttempt.setRespondedAt(Instant.now());
                prevAttempt.setRejectionReason("Reassigned by administrator");
                attemptRepository.save(prevAttempt);
            });
        }

        BigDecimal distanceKm = distanceCalculator.estimateRiderToPharmacyKm(rider, delivery.getPharmacy());
        return applyRiderAssignment(
                delivery,
                order,
                rider,
                distanceKm,
                BigDecimal.ZERO,
                adminUserId,
                "ADMIN",
                "Operations Admin",
                notes != null && !notes.isBlank() ? notes.trim() : "Manually assigned by administrator"
        );
    }

    @Scheduled(fixedDelayString = "${app.delivery.timeout-sweep-interval-ms:30000}")
    @Transactional
    public int expireTimedOutAssignments() {
        Instant now = Instant.now();
        List<Delivery> expired = deliveryRepository.findExpiredAssignedDeliveries(now);
        int processed = 0;

        for (Delivery item : expired) {
            Delivery delivery = deliveryRepository.findByIdForUpdate(item.getId()).orElse(null);
            if (delivery == null || delivery.getStatus() != DeliveryStatus.ASSIGNED) {
                continue;
            }
            if (delivery.getAssignmentExpiresAt() == null || !delivery.getAssignmentExpiresAt().isBefore(now)) {
                continue;
            }

            Rider previousRider = delivery.getAssignedRider();
            if (previousRider != null) {
                attemptRepository.findFirstByDeliveryIdAndRiderIdAndStatusOrderByIdDesc(
                        delivery.getId(), previousRider.getId(), AssignmentAttemptStatus.OFFERED
                ).ifPresent(attempt -> {
                    attempt.setStatus(AssignmentAttemptStatus.TIMED_OUT);
                    attempt.setRespondedAt(now);
                    attempt.setRejectionReason("Acceptance timeout exceeded");
                    attemptRepository.save(attempt);
                });
            }

            DeliveryStatus prevStatus = delivery.getStatus();
            delivery.setAssignedRider(null);
            delivery.setStatus(DeliveryStatus.REASSIGNMENT_REQUIRED);
            delivery.setAssignmentExpiresAt(null);
            deliveryRepository.save(delivery);

            Order order = delivery.getOrder();
            if (order.getStatus() == OrderStatus.RIDER_ASSIGNED) {
                order.setStatus(OrderStatus.READY_FOR_PICKUP);
                order.setDeliveryAssignmentStatus(DeliveryAssignmentStatus.AWAITING_PICKUP);
                orderRepository.save(order);
            }

            recordDeliveryEvent(
                    delivery,
                    "ASSIGNMENT_TIMED_OUT",
                    prevStatus,
                    DeliveryStatus.REASSIGNMENT_REQUIRED,
                    null,
                    "SYSTEM",
                    "Auto Dispatcher",
                    previousRider != null
                            ? "Rider " + previousRider.getUser().getFullName() + " did not accept before timeout."
                            : "Rider assignment timed out."
            );

            assignBestAvailableRider(delivery, order, null);
            processed++;
        }

        return processed;
    }

    public Delivery assignBestAvailableRider(Delivery delivery, Order order, Long excludeRiderId) {
        Set<Long> excludedIds = new HashSet<>(attemptRepository.findDeclinedOrTimedOutRiderIds(delivery.getId()));
        if (excludeRiderId != null) {
            excludedIds.add(excludeRiderId);
        }

        List<Rider> onlineRiders = riderRepository.findAllEligibleOnlineRiders();
        if (onlineRiders.isEmpty()) {
            return markPendingNoRider(delivery, order);
        }

        List<Long> riderIds = onlineRiders.stream().map(Rider::getId).toList();
        Map<Long, List<RiderDeliveryZone>> zonesByRider = new HashMap<>();
        for (RiderDeliveryZone zone : zoneRepository.findByRiderIdInAndActiveTrue(riderIds)) {
            zonesByRider.computeIfAbsent(zone.getRider().getId(), k -> new java.util.ArrayList<>()).add(zone);
        }

        String pickupPostal = delivery.getPickupPostalCode();
        String dropPostal = delivery.getDropPostalCode();
        String pharmacyCity = delivery.getPharmacy().getCity();

        List<RankedCandidate> candidates = onlineRiders.stream()
                .filter(r -> !excludedIds.contains(r.getId()))
                .filter(r -> isZoneEligible(r, zonesByRider.getOrDefault(r.getId(), List.of()), pickupPostal, dropPostal, pharmacyCity))
                .map(r -> scoreCandidate(r, zonesByRider.getOrDefault(r.getId(), List.of()), delivery, pickupPostal))
                .filter(c -> c != null)
                .sorted(Comparator.comparing(RankedCandidate::score)
                        .thenComparing(c -> c.distanceKm)
                        .thenComparing(c -> c.activeWorkload)
                        .thenComparing(c -> c.rider.getId()))
                .toList();

        if (candidates.isEmpty()) {
            return markPendingNoRider(delivery, order);
        }

        RankedCandidate chosen = candidates.get(0);
        return applyRiderAssignment(
                delivery,
                order,
                chosen.rider(),
                chosen.distanceKm(),
                chosen.score(),
                null,
                "SYSTEM",
                "Auto Dispatcher",
                "Auto-assigned best ranked rider (distance=" + chosen.distanceKm() + " km, active workload=" + chosen.activeWorkload() + ")"
        );
    }

    private Delivery createDeliveryRecord(Order order) {
        String ref = "DLV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Delivery delivery = Delivery.builder()
                .deliveryReference(ref)
                .order(order)
                .pharmacy(order.getPharmacy())
                .customer(order.getCustomer())
                .status(DeliveryStatus.WAITING_FOR_RIDER)
                .pickupPostalCode(order.getPharmacy().getPostalCode())
                .dropPostalCode(order.getShippingPostalCode())
                .maxOtpAttempts(deliveryProperties.getMaxOtpAttempts())
                .build();
        delivery = deliveryRepository.save(delivery);
        recordDeliveryEvent(
                delivery,
                "DELIVERY_CREATED",
                null,
                DeliveryStatus.WAITING_FOR_RIDER,
                null,
                "SYSTEM",
                "Auto Dispatcher",
                "Delivery queue initialized when order marked READY_FOR_PICKUP"
        );
        return delivery;
    }

    private boolean isZoneEligible(
            Rider rider,
            List<RiderDeliveryZone> zones,
            String pickupPostal,
            String dropPostal,
            String pharmacyCity) {
        if (!zones.isEmpty()) {
            return zones.stream().anyMatch(z ->
                    z.getPostalCode().equalsIgnoreCase(pickupPostal)
                            || z.getPostalCode().equalsIgnoreCase(dropPostal)
            );
        }
        return rider.getServiceCity() != null && pharmacyCity != null
                && rider.getServiceCity().equalsIgnoreCase(pharmacyCity.trim());
    }

    private RankedCandidate scoreCandidate(
            Rider rider,
            List<RiderDeliveryZone> zones,
            Delivery delivery,
            String pickupPostal) {
        long activeWorkload = deliveryRepository.countByAssignedRiderIdAndStatusIn(
                rider.getId(), RiderService.ACTIVE_WORKLOAD_STATUSES
        );
        if (activeWorkload >= rider.getMaxConcurrentDeliveries()) {
            return null;
        }

        BigDecimal distanceKm = distanceCalculator.estimateRiderToPharmacyKm(rider, delivery.getPharmacy());
        if (distanceKm.doubleValue() > deliveryProperties.getMaxDistanceKm()) {
            return null;
        }

        int zonePriority = zones.stream()
                .filter(z -> z.getPostalCode().equalsIgnoreCase(pickupPostal))
                .mapToInt(RiderDeliveryZone::getPriorityOrder)
                .min()
                .orElse(3);

        double scoreValue = (distanceKm.doubleValue() * deliveryProperties.getDistanceWeight())
                + (activeWorkload * deliveryProperties.getWorkloadWeight())
                + (zonePriority * 0.5);

        return new RankedCandidate(
                rider,
                distanceKm,
                activeWorkload,
                BigDecimal.valueOf(scoreValue).setScale(4, RoundingMode.HALF_UP)
        );
    }

    private Delivery applyRiderAssignment(
            Delivery delivery,
            Order order,
            Rider rider,
            BigDecimal distanceKm,
            BigDecimal rankingScore,
            Long actorUserId,
            String actorRole,
            String actorName,
            String notes) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(deliveryProperties.getAcceptanceTimeoutSeconds(), ChronoUnit.SECONDS);

        DeliveryStatus prev = delivery.getStatus();
        int nextAttemptNum = delivery.getAssignmentAttempts() + 1;

        delivery.setAssignedRider(rider);
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        delivery.setDistanceKm(distanceKm);
        delivery.setAssignmentAttempts(nextAttemptNum);
        delivery.setAssignedAt(now);
        delivery.setAssignmentExpiresAt(expiresAt);
        delivery.setAcceptedAt(null);
        delivery.setFailureReason(null);

        BigDecimal baseFee = rider.getBaseFeePerDelivery() != null
                ? rider.getBaseFeePerDelivery()
                : deliveryProperties.getDefaultBaseFee();
        BigDecimal distanceBonus = distanceKm.multiply(deliveryProperties.getPerKmFee()).setScale(2, RoundingMode.HALF_UP);
        delivery.setRiderEarningAmount(baseFee.add(distanceBonus));

        delivery = deliveryRepository.save(delivery);

        attemptRepository.save(DeliveryAssignmentAttempt.builder()
                .delivery(delivery)
                .rider(rider)
                .attemptNumber(nextAttemptNum)
                .status(AssignmentAttemptStatus.OFFERED)
                .distanceKm(distanceKm)
                .rankingScore(rankingScore)
                .offeredAt(now)
                .expiresAt(expiresAt)
                .build());

        recordDeliveryEvent(
                delivery,
                "RIDER_ASSIGNED",
                prev,
                DeliveryStatus.ASSIGNED,
                actorUserId,
                actorRole,
                actorName,
                notes + " (Rider: " + rider.getUser().getFullName() + ", expires " + expiresAt + ")"
        );

        OrderStatus prevOrderStatus = order.getStatus();
        if (prevOrderStatus == OrderStatus.READY_FOR_PICKUP) {
            order.setStatus(OrderStatus.RIDER_ASSIGNED);
            order.setDeliveryAssignmentStatus(DeliveryAssignmentStatus.AWAITING_PICKUP);
            orderRepository.save(order);
            orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                    .order(order)
                    .previousStatus(prevOrderStatus)
                    .newStatus(OrderStatus.RIDER_ASSIGNED)
                    .actorUserId(actorUserId)
                    .actorRole(actorRole)
                    .actorName(actorName)
                    .notes("Rider " + rider.getUser().getFullName() + " assigned for pickup")
                    .build());
        }

        return delivery;
    }

    private Delivery markPendingNoRider(Delivery delivery, Order order) {
        DeliveryStatus prev = delivery.getStatus();
        DeliveryStatus target = delivery.getAssignmentAttempts() > 0
                ? DeliveryStatus.REASSIGNMENT_REQUIRED
                : DeliveryStatus.WAITING_FOR_RIDER;
        delivery.setAssignedRider(null);
        delivery.setStatus(target);
        delivery.setAssignmentExpiresAt(null);
        deliveryRepository.save(delivery);

        if (order.getStatus() == OrderStatus.RIDER_ASSIGNED) {
            order.setStatus(OrderStatus.READY_FOR_PICKUP);
            order.setDeliveryAssignmentStatus(DeliveryAssignmentStatus.AWAITING_PICKUP);
            orderRepository.save(order);
        }

        if (prev != target) {
            recordDeliveryEvent(
                    delivery,
                    "AWAITING_AVAILABLE_RIDER",
                    prev,
                    target,
                    null,
                    "SYSTEM",
                    "Auto Dispatcher",
                    "No eligible verified online rider available in zone; keeping order ready in pending dispatch queue."
            );
        }
        return delivery;
    }

    public void recordDeliveryEvent(
            Delivery delivery,
            String eventType,
            DeliveryStatus prev,
            DeliveryStatus next,
            Long actorUserId,
            String actorRole,
            String actorName,
            String notes) {
        eventRepository.save(DeliveryEvent.builder()
                .delivery(delivery)
                .eventType(eventType)
                .previousStatus(prev)
                .newStatus(next)
                .actorUserId(actorUserId)
                .actorRole(actorRole)
                .actorName(actorName)
                .notes(notes)
                .latitude(delivery.getRiderLat())
                .longitude(delivery.getRiderLng())
                .createdAt(Instant.now())
                .build());
    }

    private record RankedCandidate(
            Rider rider,
            BigDecimal distanceKm,
            long activeWorkload,
            BigDecimal score
    ) {
    }
}
