package com.medexa.order.service;

import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.BusinessRuleException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.delivery.service.DeliveryAssignmentService;
import com.medexa.inventory.service.InventoryService;
import com.medexa.order.DeliveryAssignmentStatus;
import com.medexa.order.Order;
import com.medexa.order.OrderItem;
import com.medexa.order.OrderItemRepository;
import com.medexa.order.OrderPaymentStatus;
import com.medexa.order.OrderRepository;
import com.medexa.order.OrderStatus;
import com.medexa.order.OrderStatusHistory;
import com.medexa.order.OrderStatusHistoryRepository;
import com.medexa.order.RefundStatus;
import com.medexa.order.dto.OrderDtos.CancelOrderRequest;
import com.medexa.order.dto.OrderDtos.OrderResponse;
import com.medexa.order.dto.OrderDtos.PharmacyOrderDecisionRequest;
import com.medexa.order.dto.OrderDtos.PharmacyOrderStatusUpdateRequest;
import com.medexa.order.dto.OrderDtos.RefundRequestPayload;
import com.medexa.pharmacy.Pharmacy;
import com.medexa.pharmacy.service.PharmacyService;
import com.medexa.prescription.service.PrescriptionService;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;
    private final PharmacyService pharmacyService;
    private final InventoryService inventoryService;
    private final PrescriptionService prescriptionService;
    private final DeliveryAssignmentService deliveryAssignmentService;

    @Transactional(readOnly = true)
    public Page<OrderResponse> listCustomerOrders(
            Long customerId,
            OrderStatus status,
            String query,
            Pageable pageable) {
        Page<Order> page = orderRepository.findForCustomer(customerId, status, query, pageable);
        Map<Long, List<OrderItem>> itemsMap = loadItemsMap(page.getContent());
        return page.map(o -> OrderResponse.from(o, itemsMap.getOrDefault(o.getId(), List.of()), List.of(), false));
    }

    @Transactional(readOnly = true)
    public OrderResponse getCustomerOrder(Long customerId, Long orderId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        return loadFullOrder(order, false);
    }

    @Transactional
    public OrderResponse cancelCustomerOrder(Long customerId, Long orderId, CancelOrderRequest request) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return loadFullOrder(order, false);
        }

        boolean allowed = order.getStatus() == OrderStatus.PAYMENT_PENDING
                || order.getStatus() == OrderStatus.PLACED
                || order.getStatus() == OrderStatus.PHARMACY_ACCEPTED
                || order.getStatus() == OrderStatus.PREPARING;
        if (!allowed) {
            throw new BusinessRuleException(
                    "Order cannot be cancelled once it reaches " + order.getStatus() + " status."
            );
        }

        OrderStatus previous = order.getStatus();
        String reason = request.reason().trim();

        order.setStatus(OrderStatus.CANCELLED);
        order.setDeliveryAssignmentStatus(DeliveryAssignmentStatus.CANCELLED);
        order.setCancellationReason(reason);
        order.setCancelledByRole("CUSTOMER");
        order.setCancelledAt(Instant.now());

        if (order.getPaymentStatus() == OrderPaymentStatus.PAID) {
            order.setRefundEligible(true);
            order.setRefundStatus(RefundStatus.ELIGIBLE);
        } else {
            order.setPaymentStatus(OrderPaymentStatus.FAILED);
        }
        orderRepository.save(order);

        restoreOrderStock(order, customerId, "Order " + order.getOrderNumber() + " cancelled by customer");
        recordStatusChange(order, previous, OrderStatus.CANCELLED, customerId, "CUSTOMER", customer.getFullName(), reason);
        return loadFullOrder(order, false);
    }

    @Transactional
    public OrderResponse requestRefund(Long customerId, Long orderId, RefundRequestPayload request) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));

        if (!order.isRefundEligible()) {
            throw new BusinessRuleException("This order is not currently eligible for a refund request.");
        }
        if (order.getRefundStatus() == RefundStatus.REQUESTED
                || order.getRefundStatus() == RefundStatus.APPROVED
                || order.getRefundStatus() == RefundStatus.PROCESSED) {
            throw new BusinessRuleException("A refund request has already been recorded for this order.");
        }

        order.setRefundStatus(RefundStatus.REQUESTED);
        order.setPaymentStatus(OrderPaymentStatus.REFUND_REQUESTED);
        order.setRefundReason(request.reason().trim());
        orderRepository.save(order);

        recordStatusChange(
                order,
                order.getStatus(),
                order.getStatus(),
                customerId,
                "CUSTOMER",
                customer.getFullName(),
                "Refund requested: " + request.reason().trim()
        );
        return loadFullOrder(order, false);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listPharmacyOrders(
            Long pharmacyUserId,
            OrderStatus status,
            String query,
            Pageable pageable) {
        Pharmacy pharmacy = pharmacyService.requirePharmacyByUserId(pharmacyUserId);
        Page<Order> page = orderRepository.findForPharmacy(pharmacy.getId(), status, query, pageable);
        Map<Long, List<OrderItem>> itemsMap = loadItemsMap(page.getContent());
        return page.map(o -> OrderResponse.from(o, itemsMap.getOrDefault(o.getId(), List.of()), List.of(), true));
    }

    @Transactional(readOnly = true)
    public OrderResponse getPharmacyOrder(Long pharmacyUserId, Long orderId) {
        Pharmacy pharmacy = pharmacyService.requirePharmacyByUserId(pharmacyUserId);
        Order order = orderRepository.findByIdAndPharmacyId(orderId, pharmacy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy order not found."));
        return loadFullOrder(order, true);
    }

    @Transactional
    public OrderResponse decideOrder(
            Long pharmacyUserId,
            Long orderId,
            PharmacyOrderDecisionRequest request) {
        Pharmacy pharmacy = pharmacyService.requireVerifiedActivePharmacy(pharmacyUserId);
        Order order = orderRepository.findByIdAndPharmacyId(orderId, pharmacy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy order not found."));

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new BusinessRuleException("Only newly PLACED paid orders can be accepted or rejected.");
        }
        if (order.getPaymentStatus() != OrderPaymentStatus.PAID) {
            throw new BusinessRuleException("Unpaid orders cannot be accepted by pharmacy.");
        }

        OrderStatus previous = order.getStatus();
        Instant now = Instant.now();

        if (Boolean.TRUE.equals(request.accepted())) {
            verifyOrderPrescriptions(order);
            order.setStatus(OrderStatus.PHARMACY_ACCEPTED);
            order.setAcceptedAt(now);
            orderRepository.save(order);
            recordStatusChange(
                    order,
                    previous,
                    OrderStatus.PHARMACY_ACCEPTED,
                    pharmacyUserId,
                    "PHARMACY",
                    pharmacy.getPharmacyName(),
                    request.reason() != null && !request.reason().isBlank()
                            ? request.reason().trim()
                            : "Order accepted by dispensing pharmacy"
            );
        } else {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new BadRequestException("A rejection reason is required when rejecting an order.");
            }
            String reason = request.reason().trim();
            order.setStatus(OrderStatus.PHARMACY_REJECTED);
            order.setDeliveryAssignmentStatus(DeliveryAssignmentStatus.CANCELLED);
            order.setCancellationReason(reason);
            order.setCancelledByRole("PHARMACY");
            order.setCancelledAt(now);
            order.setRefundEligible(true);
            order.setRefundStatus(RefundStatus.ELIGIBLE);
            orderRepository.save(order);

            restoreOrderStock(order, pharmacyUserId, "Order rejected by pharmacy: " + reason);
            recordStatusChange(
                    order,
                    previous,
                    OrderStatus.PHARMACY_REJECTED,
                    pharmacyUserId,
                    "PHARMACY",
                    pharmacy.getPharmacyName(),
                    reason
            );
        }

        return loadFullOrder(order, true);
    }

    @Transactional
    public OrderResponse updatePharmacyProgress(
            Long pharmacyUserId,
            Long orderId,
            PharmacyOrderStatusUpdateRequest request) {
        Pharmacy pharmacy = pharmacyService.requireVerifiedActivePharmacy(pharmacyUserId);
        Order order = orderRepository.findByIdAndPharmacyId(orderId, pharmacy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy order not found."));

        OrderStatus target = request.status();
        OrderStatus current = order.getStatus();
        Instant now = Instant.now();

        if (target == OrderStatus.PREPARING) {
            if (current != OrderStatus.PHARMACY_ACCEPTED) {
                throw new BusinessRuleException("Order must be PHARMACY_ACCEPTED before entering PREPARING.");
            }
            verifyOrderPrescriptions(order);
            order.setStatus(OrderStatus.PREPARING);
            order.setPreparedAt(now);
        } else if (target == OrderStatus.READY_FOR_PICKUP) {
            if (current != OrderStatus.PHARMACY_ACCEPTED && current != OrderStatus.PREPARING) {
                throw new BusinessRuleException("Order must be accepted or preparing before marking READY_FOR_PICKUP.");
            }
            verifyOrderPrescriptions(order);
            order.setStatus(OrderStatus.READY_FOR_PICKUP);
            if (order.getPreparedAt() == null) {
                order.setPreparedAt(now);
            }
            order.setReadyAt(now);
            order.setDeliveryAssignmentStatus(DeliveryAssignmentStatus.AWAITING_PICKUP);
        } else {
            throw new BadRequestException("Pharmacy fulfillment stage can only transition to PREPARING or READY_FOR_PICKUP.");
        }

        orderRepository.save(order);
        recordStatusChange(
                order,
                current,
                target,
                pharmacyUserId,
                "PHARMACY",
                pharmacy.getPharmacyName(),
                request.notes() != null && !request.notes().isBlank()
                        ? request.notes().trim()
                        : "Order moved to " + target.name()
        );
        if (target == OrderStatus.READY_FOR_PICKUP) {
            deliveryAssignmentService.initiateOrRetryAssignment(order.getId());
            order = orderRepository.findById(order.getId()).orElse(order);
        }
        return loadFullOrder(order, true);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listAdminOrders(
            OrderStatus status,
            OrderPaymentStatus paymentStatus,
            Long pharmacyId,
            String query,
            Pageable pageable) {
        Page<Order> page = orderRepository.findForAdmin(status, paymentStatus, pharmacyId, query, pageable);
        Map<Long, List<OrderItem>> itemsMap = loadItemsMap(page.getContent());
        return page.map(o -> OrderResponse.from(o, itemsMap.getOrDefault(o.getId(), List.of()), List.of(), true));
    }

    @Transactional(readOnly = true)
    public OrderResponse getAdminOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        return loadFullOrder(order, true);
    }

    public OrderResponse loadFullOrder(Order order, boolean includePayoutDetails) {
        List<OrderItem> items = orderItemRepository.findByOrderIdWithRefs(order.getId());
        List<OrderStatusHistory> history = statusHistoryRepository.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId());
        return OrderResponse.from(order, items, history, includePayoutDetails);
    }

    public void recordStatusChange(
            Order order,
            OrderStatus previous,
            OrderStatus next,
            Long actorUserId,
            String actorRole,
            String actorName,
            String notes) {
        OrderStatusHistory entry = OrderStatusHistory.builder()
                .order(order)
                .previousStatus(previous)
                .newStatus(next)
                .actorUserId(actorUserId)
                .actorRole(actorRole)
                .actorName(actorName)
                .notes(notes)
                .build();
        statusHistoryRepository.save(entry);
    }

    private void verifyOrderPrescriptions(Order order) {
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

    private void restoreOrderStock(Order order, Long actorUserId, String reason) {
        String checkoutRef = order.getCheckoutSession().getCheckoutReference();
        List<OrderItem> items = orderItemRepository.findByOrderIdWithRefs(order.getId());
        for (OrderItem item : items) {
            inventoryService.releaseReservationsForMedicine(
                    checkoutRef,
                    item.getMedicine().getId(),
                    actorUserId,
                    reason
            );
        }
    }

    private Map<Long, List<OrderItem>> loadItemsMap(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        return orderItemRepository.findByOrderIdInWithRefs(orderIds)
                .stream()
                .collect(Collectors.groupingBy(oi -> oi.getOrder().getId()));
    }
}
