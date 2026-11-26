package com.medexa.refund.service;

import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.BusinessRuleException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.order.Order;
import com.medexa.order.OrderPaymentStatus;
import com.medexa.order.OrderRepository;
import com.medexa.order.RefundStatus;
import com.medexa.order.service.OrderService;
import com.medexa.payment.Payment;
import com.medexa.payment.PaymentRepository;
import com.medexa.payment.PaymentStatus;
import com.medexa.payment.service.PaymentLedgerService;
import com.medexa.payment.service.RazorpayGatewayClient;
import com.medexa.payment.service.RazorpayGatewayClient.GatewayRefundResult;
import com.medexa.refund.RefundRecord;
import com.medexa.refund.RefundRecordRepository;
import com.medexa.refund.RefundRecordStatus;
import com.medexa.refund.dto.RefundDtos.AdminRefundDecisionRequest;
import com.medexa.refund.dto.RefundDtos.CreateRefundRequest;
import com.medexa.refund.dto.RefundDtos.RefundResponse;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final RefundRecordRepository refundRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PaymentLedgerService paymentLedgerService;
    private final OrderService orderService;
    private final RazorpayGatewayClient gatewayClient;

    @Transactional
    public RefundResponse createRefundRequest(Long customerId, Long orderId, CreateRefundRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<RefundRecord> existing = refundRepository
                    .findByIdempotencyKeyAndCustomerId(request.idempotencyKey().trim(), customerId);
            if (existing.isPresent()) {
                return RefundResponse.from(existing.get());
            }
        }

        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));

        if (!order.isRefundEligible()) {
            throw new BusinessRuleException("This order is not currently eligible for refund.");
        }
        if (refundRepository.existsByOrderIdAndStatusIn(
                order.getId(),
                List.of(RefundRecordStatus.REQUESTED, RefundRecordStatus.PROCESSING, RefundRecordStatus.PROCESSED))) {
            throw new BusinessRuleException("An active or completed refund already exists for this order.");
        }

        Payment payment = paymentRepository.findByCheckoutSessionId(order.getCheckoutSession().getId())
                .orElseThrow(() -> new BusinessRuleException("No captured payment record found for this order."));
        if (payment.getStatus() != PaymentStatus.CAPTURED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BusinessRuleException("Refund requires a captured payment record.");
        }

        String refCode = "RFD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        RefundRecord record = RefundRecord.builder()
                .refundReference(refCode)
                .order(order)
                .payment(payment)
                .customer(customer)
                .amount(order.getGrandTotal())
                .currency(payment.getCurrency())
                .reason(request.reason().trim())
                .status(RefundRecordStatus.REQUESTED)
                .idempotencyKey(request.idempotencyKey() != null && !request.idempotencyKey().isBlank()
                        ? request.idempotencyKey().trim()
                        : null)
                .initiatedByUserId(customerId)
                .build();
        refundRepository.save(record);

        order.setRefundStatus(RefundStatus.REQUESTED);
        order.setPaymentStatus(OrderPaymentStatus.REFUND_REQUESTED);
        order.setRefundReason(request.reason().trim());
        orderRepository.save(order);

        orderService.recordStatusChange(
                order,
                order.getStatus(),
                order.getStatus(),
                customerId,
                "CUSTOMER",
                customer.getFullName(),
                "Refund request " + refCode + " submitted: " + request.reason().trim()
        );

        return RefundResponse.from(record);
    }

    @Transactional
    public RefundResponse processRefund(
            Long adminUserId,
            Long refundId,
            AdminRefundDecisionRequest request) {
        RefundRecord refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund record not found."));

        // Idempotent guard: already processed returns cleanly without calling gateway twice
        if (refund.getStatus() == RefundRecordStatus.PROCESSED) {
            return RefundResponse.from(refund);
        }
        if (refund.getStatus() != RefundRecordStatus.REQUESTED
                && refund.getStatus() != RefundRecordStatus.PROCESSING) {
            throw new BusinessRuleException("Refund cannot be processed from status " + refund.getStatus() + ".");
        }

        Payment payment = paymentRepository.findByCheckoutSessionIdForUpdate(
                refund.getOrder().getCheckoutSession().getId()
        ).orElseThrow(() -> new ResourceNotFoundException("Associated payment record not found."));

        if (payment.getGatewayPaymentId() == null || payment.getGatewayPaymentId().isBlank()) {
            throw new BusinessRuleException("Cannot refund payment missing gateway payment ID.");
        }

        long amountPaise = refund.getAmount().multiply(ONE_HUNDRED).longValue();
        GatewayRefundResult gatewayResult = gatewayClient.createRefund(
                payment.getGatewayPaymentId(),
                amountPaise,
                refund.getRefundReference(),
                refund.getReason()
        );

        Instant now = Instant.now();
        refund.setGatewayRefundId(gatewayResult.refundId());
        refund.setStatus(RefundRecordStatus.PROCESSED);
        refund.setProcessedByUserId(adminUserId);
        refund.setAdminNotes(request != null && request.notes() != null && !request.notes().isBlank()
                ? request.notes().trim()
                : "Refund settled via Razorpay");
        refund.setProcessedAt(now);
        refundRepository.save(refund);

        syncPaymentAndOrderOnRefundProcessed(payment, refund, adminUserId);
        return RefundResponse.from(refund);
    }

    @Transactional
    public RefundResponse rejectRefund(
            Long adminUserId,
            Long refundId,
            AdminRefundDecisionRequest request) {
        if (request == null || request.notes() == null || request.notes().isBlank()) {
            throw new BadRequestException("A reason is required when rejecting a refund request.");
        }
        RefundRecord refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund record not found."));
        if (refund.getStatus() == RefundRecordStatus.PROCESSED) {
            throw new BusinessRuleException("Processed refunds cannot be rejected.");
        }

        refund.setStatus(RefundRecordStatus.REJECTED);
        refund.setProcessedByUserId(adminUserId);
        refund.setAdminNotes(request.notes().trim());
        refund.setProcessedAt(Instant.now());
        refundRepository.save(refund);

        Order order = refund.getOrder();
        order.setRefundStatus(RefundStatus.NONE);
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        orderRepository.save(order);

        orderService.recordStatusChange(
                order,
                order.getStatus(),
                order.getStatus(),
                adminUserId,
                "ADMIN",
                "Operations Admin",
                "Refund " + refund.getRefundReference() + " rejected: " + request.notes().trim()
        );
        return RefundResponse.from(refund);
    }

    @Transactional
    public String handleRefundWebhook(String gatewayRefundId, String gatewayPaymentId, String statusEvent) {
        if (gatewayRefundId == null || gatewayRefundId.isBlank()) {
            return "Ignored refund webhook missing refund ID";
        }
        Optional<RefundRecord> opt = refundRepository.findByGatewayRefundId(gatewayRefundId);
        if (opt.isEmpty()) {
            return "No matching refund record found for " + gatewayRefundId;
        }
        RefundRecord refund = refundRepository.findByIdForUpdate(opt.get().getId()).orElseThrow();
        if ("refund.processed".equals(statusEvent)) {
            if (refund.getStatus() == RefundRecordStatus.PROCESSED) {
                return "Refund already marked processed";
            }
            refund.setStatus(RefundRecordStatus.PROCESSED);
            refund.setProcessedAt(Instant.now());
            refundRepository.save(refund);
            Payment payment = paymentRepository.findByCheckoutSessionIdForUpdate(
                    refund.getOrder().getCheckoutSession().getId()
            ).orElseThrow();
            syncPaymentAndOrderOnRefundProcessed(payment, refund, null);
            return "Refund marked processed via webhook";
        }
        if ("refund.failed".equals(statusEvent) && refund.getStatus() != RefundRecordStatus.PROCESSED) {
            refund.setStatus(RefundRecordStatus.FAILED);
            refund.setFailureReason("Razorpay webhook reported refund.failed for " + gatewayPaymentId);
            refundRepository.save(refund);
            return "Refund marked failed via webhook";
        }
        return "Refund webhook processed";
    }

    @Transactional(readOnly = true)
    public Page<RefundResponse> listCustomerRefunds(
            Long customerId,
            RefundRecordStatus status,
            Pageable pageable) {
        return refundRepository.findForCustomer(customerId, status, pageable).map(RefundResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<RefundResponse> listAdminRefunds(
            RefundRecordStatus status,
            String query,
            Pageable pageable) {
        return refundRepository.findForAdmin(status, query, pageable).map(RefundResponse::from);
    }

    private void syncPaymentAndOrderOnRefundProcessed(Payment payment, RefundRecord refund, Long adminUserId) {
        PaymentStatus prevPaymentStatus = payment.getStatus();
        BigDecimal newRefundedTotal = payment.getRefundedAmount().add(refund.getAmount());
        if (newRefundedTotal.compareTo(payment.getAmount()) > 0) {
            newRefundedTotal = payment.getAmount();
        }
        payment.setRefundedAmount(newRefundedTotal);
        PaymentStatus nextPaymentStatus = newRefundedTotal.compareTo(payment.getAmount()) >= 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
        payment.setStatus(nextPaymentStatus);
        paymentRepository.save(payment);

        paymentLedgerService.recordEvent(
                payment,
                adminUserId != null ? "ADMIN_REFUND" : "RAZORPAY_WEBHOOK",
                "REFUND_PROCESSED",
                prevPaymentStatus,
                nextPaymentStatus,
                refund.getGatewayRefundId(),
                "Refund " + refund.getRefundReference() + " of ₹" + refund.getAmount().toPlainString() + " processed"
        );

        Order order = refund.getOrder();
        order.setRefundStatus(RefundStatus.PROCESSED);
        order.setPaymentStatus(OrderPaymentStatus.REFUNDED);
        orderRepository.save(order);

        orderService.recordStatusChange(
                order,
                order.getStatus(),
                order.getStatus(),
                adminUserId,
                adminUserId != null ? "ADMIN" : "SYSTEM",
                adminUserId != null ? "Operations Admin" : "Razorpay Refund",
                "Refund " + refund.getRefundReference() + " processed (" + refund.getGatewayRefundId() + ")"
        );
    }
}
