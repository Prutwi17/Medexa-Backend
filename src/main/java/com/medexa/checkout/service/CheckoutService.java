package com.medexa.checkout.service;

import com.medexa.address.CustomerAddress;
import com.medexa.address.CustomerAddressRepository;
import com.medexa.cart.CartItem;
import com.medexa.cart.CartItemRepository;
import com.medexa.checkout.CheckoutSession;
import com.medexa.checkout.CheckoutSessionRepository;
import com.medexa.checkout.CheckoutStatus;
import com.medexa.checkout.config.CheckoutProperties;
import com.medexa.checkout.config.RazorpayProperties;
import com.medexa.checkout.dto.CheckoutDtos.CheckoutPreviewRequest;
import com.medexa.checkout.dto.CheckoutDtos.CheckoutPreviewResponse;
import com.medexa.checkout.dto.CheckoutDtos.CheckoutSessionResponse;
import com.medexa.checkout.dto.CheckoutDtos.ConfirmPaymentRequest;
import com.medexa.checkout.dto.CheckoutDtos.CreateCheckoutSessionRequest;
import com.medexa.checkout.dto.CheckoutDtos.PharmacyCheckoutBreakdown;
import com.medexa.checkout.service.CouponService.CouponEvaluation;
import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.BusinessRuleException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.inventory.dto.InventoryDtos.BatchAllocation;
import com.medexa.inventory.service.InventoryService;
import com.medexa.medicine.Medicine;
import com.medexa.medicine.MedicineApprovalStatus;
import com.medexa.medicine.MedicineClassificationRule;
import com.medexa.medicine.service.MedicineClassificationService;
import com.medexa.order.DeliveryAssignmentStatus;
import com.medexa.order.Order;
import com.medexa.order.OrderItem;
import com.medexa.order.OrderItemRepository;
import com.medexa.order.OrderPaymentStatus;
import com.medexa.order.OrderRepository;
import com.medexa.order.OrderStatus;
import com.medexa.order.dto.OrderDtos.OrderResponse;
import com.medexa.order.service.OrderService;
import com.medexa.pharmacy.Pharmacy;
import com.medexa.pharmacy.PharmacyDeliveryZone;
import com.medexa.pharmacy.PharmacyDeliveryZoneRepository;
import com.medexa.pharmacy.VerificationStatus;
import com.medexa.prescription.service.PrescriptionService;
import com.medexa.invoice.service.InvoiceService;
import com.medexa.payment.Payment;
import com.medexa.payment.PaymentEventRepository;
import com.medexa.payment.PaymentRepository;
import com.medexa.payment.PaymentStatus;
import com.medexa.payment.dto.PaymentDtos.PaymentFailureRequest;
import com.medexa.payment.dto.PaymentDtos.PaymentResponse;
import com.medexa.payment.dto.PaymentDtos.PaymentVerifyRequest;
import com.medexa.payment.service.PaymentLedgerService;
import com.medexa.payment.service.RazorpayGatewayClient;
import com.medexa.payment.service.RazorpayGatewayClient.GatewayOrderResult;
import com.medexa.payment.service.RazorpayGatewayClient.GatewayPaymentSnapshot;
import com.medexa.user.AccountStatus;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final CheckoutSessionRepository checkoutRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final CustomerAddressRepository addressRepository;
    private final PharmacyDeliveryZoneRepository deliveryZoneRepository;
    private final UserRepository userRepository;
    private final MedicineClassificationService classificationService;
    private final PrescriptionService prescriptionService;
    private final InventoryService inventoryService;
    private final CouponService couponService;
    private final OrderService orderService;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PaymentLedgerService paymentLedgerService;
    private final RazorpayGatewayClient razorpayGatewayClient;
    private final InvoiceService invoiceService;
    private final CheckoutProperties checkoutProperties;
    private final RazorpayProperties razorpayProperties;

    private record ComputedCartPlan(
            User customer,
            CustomerAddress address,
            String appliedCouponCode,
            boolean couponApplied,
            String couponMessage,
            BigDecimal itemsSubtotal,
            BigDecimal discountTotal,
            BigDecimal deliveryFeeTotal,
            BigDecimal taxTotal,
            BigDecimal grandTotal,
            boolean checkoutAllowed,
            List<String> blockers,
            List<PharmacyPlan> pharmacyPlans
    ) {
    }

    private record PharmacyPlan(
            Pharmacy pharmacy,
            PharmacyDeliveryZone zone,
            List<CartItem> items,
            Map<Long, MedicineClassificationRule> itemRules,
            BigDecimal itemsSubtotal,
            BigDecimal discountAllocated,
            BigDecimal deliveryFee,
            BigDecimal taxAmount,
            BigDecimal grandTotal,
            BigDecimal platformCommissionAmount,
            BigDecimal pharmacyPayoutAmount,
            boolean ready,
            List<String> blockers
    ) {
    }

    @Transactional(readOnly = true)
    public CheckoutPreviewResponse previewCheckout(Long customerId, CheckoutPreviewRequest request) {
        ComputedCartPlan plan = buildCheckoutPlan(
                customerId,
                request != null ? request.addressId() : null,
                request != null ? request.couponCode() : null,
                false
        );

        List<PharmacyCheckoutBreakdown> breakdowns = plan.pharmacyPlans().stream()
                .map(p -> new PharmacyCheckoutBreakdown(
                        p.pharmacy().getId(),
                        p.pharmacy().getPharmacyName(),
                        p.pharmacy().getCity(),
                        p.items().stream().mapToInt(CartItem::getQuantity).sum(),
                        p.itemsSubtotal(),
                        p.discountAllocated(),
                        p.deliveryFee(),
                        p.taxAmount(),
                        p.grandTotal(),
                        p.platformCommissionAmount(),
                        p.pharmacyPayoutAmount(),
                        p.zone() != null ? p.zone().getEstimatedDeliveryMinutes() : null,
                        p.zone() != null,
                        p.ready(),
                        p.blockers()
                ))
                .toList();

        return new CheckoutPreviewResponse(
                plan.address() != null ? plan.address().getId() : null,
                plan.address() != null ? plan.address().getPostalCode() : null,
                plan.appliedCouponCode(),
                plan.couponApplied(),
                plan.couponMessage(),
                plan.itemsSubtotal(),
                plan.discountTotal(),
                plan.deliveryFeeTotal(),
                checkoutProperties.getTaxRatePercent(),
                plan.taxTotal(),
                plan.grandTotal(),
                plan.checkoutAllowed(),
                plan.blockers(),
                breakdowns
        );
    }

    @Transactional
    public CheckoutSessionResponse createCheckoutSession(
            Long customerId,
            CreateCheckoutSessionRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<CheckoutSession> existing = checkoutRepository
                    .findByIdempotencyKeyAndCustomerId(request.idempotencyKey().trim(), customerId);
            if (existing.isPresent()) {
                return buildCheckoutResponse(existing.get());
            }
        }

        ComputedCartPlan plan = buildCheckoutPlan(
                customerId,
                request.addressId(),
                request.couponCode(),
                true
        );

        if (!plan.checkoutAllowed()) {
            throw new BusinessRuleException(
                    plan.blockers().isEmpty()
                            ? "Checkout validation failed."
                            : String.join("; ", plan.blockers())
            );
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(checkoutProperties.getReservationTtlMinutes(), ChronoUnit.MINUTES);
        String checkoutRef = "CHK-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

        CheckoutSession session = CheckoutSession.builder()
                .checkoutReference(checkoutRef)
                .customer(plan.customer())
                .address(plan.address())
                .deliveryRecipientName(plan.address().getRecipientName())
                .deliveryPhone(plan.address().getRecipientPhone())
                .deliveryAddressLine1(plan.address().getAddressLine1())
                .deliveryAddressLine2(plan.address().getAddressLine2())
                .deliveryCity(plan.address().getCity())
                .deliveryState(plan.address().getState())
                .deliveryPostalCode(plan.address().getPostalCode())
                .couponCode(plan.appliedCouponCode())
                .status(CheckoutStatus.AWAITING_PAYMENT)
                .itemsSubtotal(plan.itemsSubtotal())
                .discountAmount(plan.discountTotal())
                .deliveryFeeTotal(plan.deliveryFeeTotal())
                .taxAmount(plan.taxTotal())
                .grandTotal(plan.grandTotal())
                .currency("INR")
                .idempotencyKey(request.idempotencyKey() != null && !request.idempotencyKey().isBlank()
                        ? request.idempotencyKey().trim()
                        : null)
                .expiresAt(expiresAt)
                .build();

        long amountPaise = plan.grandTotal().multiply(ONE_HUNDRED).longValue();
        GatewayOrderResult gatewayOrder = razorpayGatewayClient.createOrder(checkoutRef, amountPaise, "INR");
        session.setRazorpayOrderId(gatewayOrder.orderId());
        checkoutRepository.save(session);
        paymentLedgerService.createPaymentRecord(session, gatewayOrder.orderId(), session.getIdempotencyKey());

        String formattedAddress = formatFullAddress(plan.address());

        int pharmacySeq = 1;
        for (PharmacyPlan group : plan.pharmacyPlans()) {
            String orderNumber = "ORD-" + checkoutRef.substring(4) + "-" + pharmacySeq++;
            boolean requiresRx = group.items().stream().anyMatch(i -> {
                MedicineClassificationRule rule = group.itemRules().get(i.getMedicine().getId());
                return i.getMedicine().isPrescriptionRequired() || (rule != null && rule.isPrescriptionRequired());
            });

            Order order = Order.builder()
                    .orderNumber(orderNumber)
                    .checkoutSession(session)
                    .customer(plan.customer())
                    .pharmacy(group.pharmacy())
                    .address(plan.address())
                    .shippingRecipientName(plan.address().getRecipientName())
                    .shippingPhone(plan.address().getRecipientPhone())
                    .shippingAddress(formattedAddress)
                    .shippingCity(plan.address().getCity())
                    .shippingState(plan.address().getState())
                    .shippingPostalCode(plan.address().getPostalCode())
                    .status(OrderStatus.PAYMENT_PENDING)
                    .paymentStatus(OrderPaymentStatus.PENDING)
                    .itemsSubtotal(group.itemsSubtotal())
                    .discountAmount(group.discountAllocated())
                    .deliveryFee(group.deliveryFee())
                    .taxAmount(group.taxAmount())
                    .grandTotal(group.grandTotal())
                    .platformCommissionAmount(group.platformCommissionAmount())
                    .pharmacyPayoutAmount(group.pharmacyPayoutAmount())
                    .deliveryAssignmentStatus(DeliveryAssignmentStatus.UNASSIGNED)
                    .deliveryEtaMinutes(group.zone().getEstimatedDeliveryMinutes())
                    .requiresPrescription(requiresRx)
                    .allPrescriptionsVerified(true)
                    .build();
            orderRepository.save(order);

            orderService.recordStatusChange(
                    order,
                    null,
                    OrderStatus.PAYMENT_PENDING,
                    plan.customer().getId(),
                    "CUSTOMER",
                    plan.customer().getFullName(),
                    "Child order created in checkout session " + checkoutRef + " with stock reserved"
            );

            for (CartItem ci : group.items()) {
                Medicine med = ci.getMedicine();
                MedicineClassificationRule rule = group.itemRules().get(med.getId());
                boolean itemRx = med.isPrescriptionRequired() || (rule != null && rule.isPrescriptionRequired());

                // Transactional FEFO stock reservation using pessimistic DB locking
                List<BatchAllocation> allocations = inventoryService.reserveStockFefo(
                        checkoutRef,
                        med.getId(),
                        ci.getQuantity(),
                        expiresAt,
                        customerId
                );

                String batchSummary = allocations.stream()
                        .map(a -> a.batchNumber() + " (exp " + a.expiryDate() + "): " + a.quantityAllocated())
                        .collect(Collectors.joining(", "));

                BigDecimal lineSubtotal = med.getSellingPrice().multiply(BigDecimal.valueOf(ci.getQuantity()))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal lineDiscount = prorate(lineSubtotal, group.itemsSubtotal(), group.discountAllocated());
                BigDecimal lineTaxable = lineSubtotal.subtract(lineDiscount).max(BigDecimal.ZERO);
                BigDecimal lineTax = lineTaxable
                        .multiply(checkoutProperties.getTaxRatePercent())
                        .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

                OrderItem item = OrderItem.builder()
                        .order(order)
                        .medicine(med)
                        .prescription(ci.getPrescription())
                        .medicineName(med.getName())
                        .medicineSlug(med.getSlug())
                        .brand(med.getBrand())
                        .packSize(med.getPackSize())
                        .classificationCode(rule != null ? rule.getClassificationCode() : "OTC")
                        .prescriptionRequired(itemRx)
                        .quantity(ci.getQuantity())
                        .mrpPrice(med.getMrpPrice())
                        .unitPrice(med.getSellingPrice())
                        .lineSubtotal(lineSubtotal)
                        .discountAllocated(lineDiscount)
                        .taxAmount(lineTax)
                        .batchAllocationsSummary(batchSummary)
                        .build();
                orderItemRepository.save(item);
            }
        }

        return buildCheckoutResponse(session);
    }

    @Transactional
    public CheckoutSessionResponse confirmPayment(
            Long customerId,
            String checkoutReference,
            ConfirmPaymentRequest request) {
        CheckoutSession session = checkoutRepository.findByCheckoutReferenceForUpdate(checkoutReference)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout session not found."));

        if (!session.getCustomer().getId().equals(customerId)) {
            throw new ResourceNotFoundException("Checkout session not found.");
        }

        // Idempotent retry check: already PAID -> return cleanly without deducting stock twice
        if (session.getStatus() == CheckoutStatus.PAID) {
            return buildCheckoutResponse(session);
        }

        Instant now = Instant.now();
        if (session.getStatus() == CheckoutStatus.AWAITING_PAYMENT && session.getExpiresAt().isBefore(now)) {
            markSessionExpiredInternal(session, "Checkout reservation window expired before payment confirmation.");
            throw new BusinessRuleException("Checkout session expired. Reserved stock has been released.");
        }

        if (session.getStatus() != CheckoutStatus.AWAITING_PAYMENT) {
            throw new BusinessRuleException("Checkout session cannot be confirmed in status " + session.getStatus() + ".");
        }

        if (!request.razorpayOrderId().equals(session.getRazorpayOrderId())) {
            throw new BadRequestException("Gateway order reference does not match this checkout session.");
        }

        Payment payment = paymentRepository.findByCheckoutSessionIdForUpdate(session.getId())
                .orElseGet(() -> paymentLedgerService.createPaymentRecord(
                        session, session.getRazorpayOrderId(), session.getIdempotencyKey()
                ));

        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            finalizeCapturedPayment(
                    session,
                    payment,
                    payment.getGatewayPaymentId(),
                    request.razorpaySignature(),
                    "SIGNATURE"
            );
            return buildCheckoutResponse(session);
        }

        boolean validSignature = RazorpaySignatureUtils.verifyPaymentSignature(
                request.razorpayOrderId(),
                request.razorpayPaymentId(),
                request.razorpaySignature(),
                razorpayProperties.getKeySecret()
        );
        if (!validSignature) {
            paymentLedgerService.recordEvent(
                    payment,
                    "CLIENT_CALLBACK",
                    "SIGNATURE_INVALID",
                    payment.getStatus(),
                    payment.getStatus(),
                    request.razorpayPaymentId(),
                    "Rejected tampered or invalid Razorpay payment signature"
            );
            throw new BadRequestException("Payment signature verification failed.");
        }

        GatewayPaymentSnapshot snapshot = razorpayGatewayClient.fetchPayment(request.razorpayPaymentId().trim());
        long expectedPaise = session.getGrandTotal().multiply(ONE_HUNDRED).longValue();
        if (snapshot.orderId() != null && !snapshot.orderId().isBlank()
                && !snapshot.orderId().equals(session.getRazorpayOrderId())) {
            throw new BusinessRuleException("Gateway payment order ID mismatch.");
        }
        if (snapshot.amountPaise() > 0 && snapshot.amountPaise() != expectedPaise) {
            throw new BusinessRuleException("Gateway paid amount does not match server checkout total.");
        }

        String gatewayStatus = snapshot.status() != null ? snapshot.status().toLowerCase() : "captured";
        payment.setLastGatewayStatus(gatewayStatus);
        payment.setGatewayPaymentId(request.razorpayPaymentId().trim());
        payment.setGatewaySignature(request.razorpaySignature().trim());

        if ("failed".equals(gatewayStatus)) {
            markPaymentFailedInternal(
                    session,
                    payment,
                    snapshot.errorCode(),
                    snapshot.errorDescription() != null ? snapshot.errorDescription() : "Payment failed at gateway",
                    "CLIENT_CALLBACK",
                    true
            );
            throw new BusinessRuleException("Payment failed at Razorpay gateway. Reserved stock released.");
        }

        if ("authorized".equals(gatewayStatus) || "created".equals(gatewayStatus)) {
            PaymentStatus prev = payment.getStatus();
            PaymentStatus next = "authorized".equals(gatewayStatus) ? PaymentStatus.AUTHORIZED : PaymentStatus.PENDING;
            payment.setStatus(next);
            if (next == PaymentStatus.AUTHORIZED) {
                payment.setAuthorizedAt(now);
            }
            paymentRepository.save(payment);
            paymentLedgerService.recordEvent(
                    payment,
                    "CLIENT_CALLBACK",
                    "PAYMENT_" + next.name(),
                    prev,
                    next,
                    request.razorpayPaymentId(),
                    "Awaiting final Razorpay capture before confirming orders"
            );
            return buildCheckoutResponse(session);
        }

        if (!"captured".equals(gatewayStatus)) {
            throw new BusinessRuleException("Unsupported gateway payment status: " + gatewayStatus);
        }

        finalizeCapturedPayment(
                session,
                payment,
                request.razorpayPaymentId().trim(),
                request.razorpaySignature().trim(),
                "SIGNATURE"
        );
        return buildCheckoutResponse(session);
    }

    @Transactional
    public PaymentResponse recordClientPaymentFailure(
            Long customerId,
            String checkoutReference,
            PaymentFailureRequest request) {
        CheckoutSession session = checkoutRepository.findByCheckoutReferenceForUpdate(checkoutReference)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout session not found."));
        if (!session.getCustomer().getId().equals(customerId)) {
            throw new ResourceNotFoundException("Checkout session not found.");
        }

        Payment payment = paymentRepository.findByCheckoutSessionIdForUpdate(session.getId())
                .orElseGet(() -> paymentLedgerService.createPaymentRecord(
                        session, session.getRazorpayOrderId(), session.getIdempotencyKey()
                ));

        if (payment.getStatus() == PaymentStatus.CAPTURED || session.getStatus() == CheckoutStatus.PAID) {
            return PaymentResponse.from(
                    payment,
                    paymentEventRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(payment.getId())
            );
        }

        if (request.razorpayPaymentId() != null && !request.razorpayPaymentId().isBlank()) {
            payment.setGatewayPaymentId(request.razorpayPaymentId().trim());
        }
        markPaymentFailedInternal(
                session,
                payment,
                request.errorCode(),
                request.errorDescription(),
                "CLIENT_CALLBACK",
                Boolean.TRUE.equals(request.releaseStock())
        );
        return PaymentResponse.from(
                payment,
                paymentEventRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(payment.getId())
        );
    }

    @Transactional
    public PaymentResponse verifyPaymentFromServer(
            Long customerId,
            String paymentReference,
            PaymentVerifyRequest request) {
        Payment lockedPayment = paymentRepository.findByPaymentReferenceForUpdate(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found."));
        if (!lockedPayment.getCustomer().getId().equals(customerId)) {
            throw new ResourceNotFoundException("Payment record not found.");
        }

        if (lockedPayment.getStatus() == PaymentStatus.CAPTURED
                || lockedPayment.getStatus() == PaymentStatus.REFUNDED
                || lockedPayment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            return PaymentResponse.from(
                    lockedPayment,
                    paymentEventRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(lockedPayment.getId())
            );
        }

        CheckoutSession session = checkoutRepository
                .findByCheckoutReferenceForUpdate(lockedPayment.getCheckoutSession().getCheckoutReference())
                .orElseThrow(() -> new ResourceNotFoundException("Checkout session not found."));

        String requestedPaymentId = request != null && request.razorpayPaymentId() != null
                && !request.razorpayPaymentId().isBlank()
                ? request.razorpayPaymentId().trim()
                : lockedPayment.getGatewayPaymentId();

        GatewayPaymentSnapshot snapshot = (requestedPaymentId != null && !requestedPaymentId.isBlank())
                ? razorpayGatewayClient.fetchPayment(requestedPaymentId)
                : razorpayGatewayClient.fetchLatestPaymentForOrder(lockedPayment.getGatewayOrderId());

        if (snapshot == null || snapshot.paymentId() == null) {
            PaymentStatus prev = lockedPayment.getStatus();
            lockedPayment.setStatus(PaymentStatus.PENDING);
            lockedPayment.setVerificationMethod("SERVER_FETCH");
            paymentRepository.save(lockedPayment);
            paymentLedgerService.recordEvent(
                    lockedPayment,
                    "SERVER_VERIFY",
                    "PAYMENT_PENDING",
                    prev,
                    PaymentStatus.PENDING,
                    lockedPayment.getGatewayOrderId(),
                    "No captured payment settled at gateway yet"
            );
            return PaymentResponse.from(
                    lockedPayment,
                    paymentEventRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(lockedPayment.getId())
            );
        }

        String gatewayStatus = snapshot.status() != null ? snapshot.status().toLowerCase() : "created";
        lockedPayment.setGatewayPaymentId(snapshot.paymentId());
        lockedPayment.setLastGatewayStatus(gatewayStatus);
        lockedPayment.setVerificationMethod("SERVER_FETCH");

        if ("captured".equals(gatewayStatus)) {
            finalizeCapturedPayment(session, lockedPayment, snapshot.paymentId(), null, "SERVER_FETCH");
        } else if ("authorized".equals(gatewayStatus)) {
            PaymentStatus prev = lockedPayment.getStatus();
            lockedPayment.setStatus(PaymentStatus.AUTHORIZED);
            lockedPayment.setAuthorizedAt(Instant.now());
            paymentRepository.save(lockedPayment);
            paymentLedgerService.recordEvent(
                    lockedPayment,
                    "SERVER_VERIFY",
                    "PAYMENT_AUTHORIZED",
                    prev,
                    PaymentStatus.AUTHORIZED,
                    snapshot.paymentId(),
                    "Payment authorized at gateway; awaiting capture"
            );
        } else if ("failed".equals(gatewayStatus)) {
            markPaymentFailedInternal(
                    session,
                    lockedPayment,
                    snapshot.errorCode(),
                    snapshot.errorDescription() != null ? snapshot.errorDescription() : "Payment failed at gateway",
                    "SERVER_VERIFY",
                    session.getStatus() == CheckoutStatus.AWAITING_PAYMENT
            );
        }

        return PaymentResponse.from(
                lockedPayment,
                paymentEventRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(lockedPayment.getId())
        );
    }

    @Transactional
    public String finalizeCapturedFromWebhook(
            String gatewayOrderId,
            String gatewayPaymentId,
            long paidAmountPaise,
            String eventId) {
        Optional<Payment> payOpt = paymentRepository.findByGatewayOrderIdForUpdate(gatewayOrderId);
        if (payOpt.isEmpty()) {
            return "No internal payment found for gateway order " + gatewayOrderId;
        }
        Payment payment = payOpt.get();
        if (payment.getStatus() == PaymentStatus.CAPTURED
                || payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            return "IGNORED_OUT_OF_ORDER: Payment already captured";
        }
        CheckoutSession session = checkoutRepository
                .findByCheckoutReferenceForUpdate(payment.getCheckoutSession().getCheckoutReference())
                .orElseThrow();
        long expectedPaise = session.getGrandTotal().multiply(ONE_HUNDRED).longValue();
        if (paidAmountPaise > 0 && paidAmountPaise != expectedPaise) {
            paymentLedgerService.recordEvent(
                    payment,
                    "RAZORPAY_WEBHOOK",
                    "AMOUNT_MISMATCH",
                    payment.getStatus(),
                    payment.getStatus(),
                    eventId,
                    "Webhook captured amount " + paidAmountPaise + " != expected " + expectedPaise
            );
            return "FAILED: Captured amount mismatch";
        }

        finalizeCapturedPayment(session, payment, gatewayPaymentId, null, "WEBHOOK");
        return "PROCESSED";
    }

    @Transactional
    public String markAuthorizedFromWebhook(
            String gatewayOrderId,
            String gatewayPaymentId,
            String eventId) {
        Optional<Payment> payOpt = paymentRepository.findByGatewayOrderIdForUpdate(gatewayOrderId);
        if (payOpt.isEmpty()) {
            return "No internal payment found for gateway order " + gatewayOrderId;
        }
        Payment payment = payOpt.get();
        if (payment.getStatus() == PaymentStatus.CAPTURED
                || payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            paymentLedgerService.recordEvent(
                    payment,
                    "RAZORPAY_WEBHOOK",
                    "IGNORED_OUT_OF_ORDER",
                    payment.getStatus(),
                    payment.getStatus(),
                    eventId,
                    "Out-of-order payment.authorized ignored because payment is already " + payment.getStatus()
            );
            return "IGNORED_OUT_OF_ORDER";
        }
        PaymentStatus prev = payment.getStatus();
        payment.setGatewayPaymentId(gatewayPaymentId);
        payment.setLastGatewayStatus("authorized");
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizedAt(Instant.now());
        paymentRepository.save(payment);
        paymentLedgerService.recordEvent(
                payment,
                "RAZORPAY_WEBHOOK",
                "PAYMENT_AUTHORIZED",
                prev,
                PaymentStatus.AUTHORIZED,
                eventId,
                "Payment authorized via Razorpay webhook"
        );
        return "PROCESSED";
    }

    @Transactional
    public String failPaymentFromWebhook(
            String gatewayOrderId,
            String gatewayPaymentId,
            String errorCode,
            String errorDescription,
            String eventId) {
        Optional<Payment> payOpt = paymentRepository.findByGatewayOrderIdForUpdate(gatewayOrderId);
        if (payOpt.isEmpty()) {
            return "No internal payment found for gateway order " + gatewayOrderId;
        }
        Payment payment = payOpt.get();
        if (payment.getStatus() == PaymentStatus.CAPTURED
                || payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            paymentLedgerService.recordEvent(
                    payment,
                    "RAZORPAY_WEBHOOK",
                    "IGNORED_OUT_OF_ORDER",
                    payment.getStatus(),
                    payment.getStatus(),
                    eventId,
                    "Out-of-order payment.failed ignored because payment is already " + payment.getStatus()
            );
            return "IGNORED_OUT_OF_ORDER";
        }
        CheckoutSession session = checkoutRepository
                .findByCheckoutReferenceForUpdate(payment.getCheckoutSession().getCheckoutReference())
                .orElseThrow();
        payment.setGatewayPaymentId(gatewayPaymentId);
        markPaymentFailedInternal(
                session,
                payment,
                errorCode,
                errorDescription != null ? errorDescription : "Payment failed (webhook)",
                "RAZORPAY_WEBHOOK",
                session.getStatus() == CheckoutStatus.AWAITING_PAYMENT
        );
        return "PROCESSED";
    }

    private void finalizeCapturedPayment(
            CheckoutSession session,
            Payment payment,
            String gatewayPaymentId,
            String signature,
            String method) {
        Instant now = Instant.now();
        Long customerId = session.getCustomer().getId();

        inventoryService.commitReservations(session.getCheckoutReference(), customerId);

        PaymentStatus prevPayStatus = payment.getStatus();
        payment.setGatewayPaymentId(gatewayPaymentId);
        if (signature != null) {
            payment.setGatewaySignature(signature);
        }
        payment.setVerificationMethod(method);
        payment.setLastGatewayStatus("captured");
        payment.setStatus(PaymentStatus.CAPTURED);
        if (payment.getAuthorizedAt() == null) {
            payment.setAuthorizedAt(now);
        }
        payment.setCapturedAt(now);
        payment.setFailureCode(null);
        payment.setFailureReason(null);
        paymentRepository.save(payment);

        if (prevPayStatus != PaymentStatus.CAPTURED) {
            paymentLedgerService.recordEvent(
                    payment,
                    "WEBHOOK".equals(method) ? "RAZORPAY_WEBHOOK" : "CLIENT_CALLBACK",
                    "PAYMENT_CAPTURED",
                    prevPayStatus,
                    PaymentStatus.CAPTURED,
                    gatewayPaymentId,
                    "Payment captured and verified via " + method
            );
        }

        if (session.getStatus() != CheckoutStatus.PAID) {
            session.setStatus(CheckoutStatus.PAID);
            session.setPaymentReference(gatewayPaymentId);
            session.setPaidAt(now);
            checkoutRepository.save(session);

            couponService.incrementUsage(session.getCouponCode());

            List<Order> childOrders = orderRepository.findByCheckoutSessionIdOrderByIdAsc(session.getId());
            for (Order child : childOrders) {
                OrderStatus prev = child.getStatus();
                child.setStatus(OrderStatus.PLACED);
                child.setPaymentStatus(OrderPaymentStatus.PAID);
                child.setPlacedAt(now);
                orderRepository.save(child);

                orderService.recordStatusChange(
                        child,
                        prev,
                        OrderStatus.PLACED,
                        customerId,
                        "WEBHOOK".equals(method) ? "SYSTEM" : "CUSTOMER",
                        session.getCustomer().getFullName(),
                        "Verified payment " + gatewayPaymentId + " confirmed via " + method
                );

                List<OrderItem> orderItems = orderItemRepository.findByOrderIdWithRefs(child.getId());
                for (OrderItem oi : orderItems) {
                    if (oi.isPrescriptionRequired() && oi.getPrescription() != null) {
                        prescriptionService.linkPrescriptionToOrder(
                                customerId,
                                oi.getMedicine().getId(),
                                oi.getPrescription().getId(),
                                child.getId(),
                                child.getOrderNumber()
                        );
                    }
                }

                invoiceService.ensureInvoiceIssued(child);
            }

            cartItemRepository.deleteByCustomerId(customerId);
        }
    }

    private void markPaymentFailedInternal(
            CheckoutSession session,
            Payment payment,
            String errorCode,
            String errorDescription,
            String source,
            boolean releaseStockAndFailSession) {
        Instant now = Instant.now();
        PaymentStatus prev = payment.getStatus();
        payment.setStatus(PaymentStatus.FAILED);
        payment.setLastGatewayStatus("failed");
        payment.setFailureCode(errorCode);
        payment.setFailureReason(errorDescription);
        payment.setFailedAt(now);
        paymentRepository.save(payment);

        paymentLedgerService.recordEvent(
                payment,
                source,
                "PAYMENT_FAILED",
                prev,
                PaymentStatus.FAILED,
                payment.getGatewayPaymentId(),
                (errorCode != null ? errorCode + ": " : "") + errorDescription
        );

        if (releaseStockAndFailSession && session.getStatus() == CheckoutStatus.AWAITING_PAYMENT) {
            inventoryService.releaseReservations(
                    session.getCheckoutReference(),
                    session.getCustomer().getId(),
                    "Payment failed: " + errorDescription
            );
            session.setStatus(CheckoutStatus.FAILED);
            session.setFailureReason(errorDescription);
            checkoutRepository.save(session);

            List<Order> childOrders = orderRepository.findByCheckoutSessionIdOrderByIdAsc(session.getId());
            for (Order child : childOrders) {
                OrderStatus prevOrder = child.getStatus();
                child.setStatus(OrderStatus.FAILED);
                child.setPaymentStatus(OrderPaymentStatus.FAILED);
                child.setDeliveryAssignmentStatus(DeliveryAssignmentStatus.CANCELLED);
                child.setCancellationReason("Payment failed: " + errorDescription);
                child.setCancelledByRole("SYSTEM");
                child.setCancelledAt(now);
                orderRepository.save(child);

                orderService.recordStatusChange(
                        child, prevOrder, OrderStatus.FAILED, null, "SYSTEM", "Payment Failure", errorDescription
                );
            }
        }
    }

    @Transactional
    public CheckoutSessionResponse cancelCheckout(
            Long customerId,
            String checkoutReference,
            String reason) {
        CheckoutSession session = checkoutRepository.findByCheckoutReferenceForUpdate(checkoutReference)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout session not found."));
        if (!session.getCustomer().getId().equals(customerId)) {
            throw new ResourceNotFoundException("Checkout session not found.");
        }
        if (session.getStatus() == CheckoutStatus.CANCELLED) {
            return buildCheckoutResponse(session);
        }
        if (session.getStatus() != CheckoutStatus.AWAITING_PAYMENT) {
            throw new BusinessRuleException("Only AWAITING_PAYMENT checkout sessions can be cancelled.");
        }

        String note = reason != null && !reason.isBlank() ? reason.trim() : "Checkout cancelled by customer";
        inventoryService.releaseReservations(session.getCheckoutReference(), customerId, note);

        Instant now = Instant.now();
        session.setStatus(CheckoutStatus.CANCELLED);
        session.setCancelledAt(now);
        session.setFailureReason(note);
        checkoutRepository.save(session);

        List<Order> childOrders = orderRepository.findByCheckoutSessionIdOrderByIdAsc(session.getId());
        for (Order child : childOrders) {
            OrderStatus prev = child.getStatus();
            child.setStatus(OrderStatus.CANCELLED);
            child.setPaymentStatus(OrderPaymentStatus.FAILED);
            child.setDeliveryAssignmentStatus(DeliveryAssignmentStatus.CANCELLED);
            child.setCancellationReason(note);
            child.setCancelledByRole("CUSTOMER");
            child.setCancelledAt(now);
            orderRepository.save(child);

            orderService.recordStatusChange(
                    child, prev, OrderStatus.CANCELLED, customerId, "CUSTOMER",
                    session.getCustomer().getFullName(), note
            );
        }

        return buildCheckoutResponse(session);
    }

    @Transactional(readOnly = true)
    public CheckoutSessionResponse getCheckoutSession(Long customerId, String checkoutReference) {
        CheckoutSession session = checkoutRepository.findByCheckoutReferenceAndCustomerId(checkoutReference, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout session not found."));
        return buildCheckoutResponse(session);
    }

    @Transactional(readOnly = true)
    public CheckoutSessionResponse getAdminCheckoutSession(String checkoutReference) {
        CheckoutSession session = checkoutRepository.findByCheckoutReference(checkoutReference)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout session not found."));
        return buildCheckoutResponse(session);
    }

    @Transactional
    public int expireTimedOutCheckouts() {
        Set<String> expiredReservationKeys = inventoryService.releaseExpiredReservations();
        List<CheckoutSession> staleSessions = checkoutRepository
                .findByStatusAndExpiresAtBefore(CheckoutStatus.AWAITING_PAYMENT, Instant.now());

        int count = 0;
        for (CheckoutSession session : staleSessions) {
            if (!expiredReservationKeys.contains(session.getCheckoutReference())) {
                inventoryService.releaseReservations(
                        session.getCheckoutReference(),
                        null,
                        "Checkout reservation expired automatically"
                );
            }
            markSessionExpiredInternal(session, "Checkout reservation window expired without payment confirmation.");
            count++;
        }
        return count;
    }

    private void markSessionExpiredInternal(CheckoutSession session, String reason) {
        inventoryService.releaseReservations(session.getCheckoutReference(), null, reason);
        session.setStatus(CheckoutStatus.EXPIRED);
        session.setFailureReason(reason);
        checkoutRepository.save(session);

        List<Order> childOrders = orderRepository.findByCheckoutSessionIdOrderByIdAsc(session.getId());
        Instant now = Instant.now();
        for (Order child : childOrders) {
            if (child.getStatus() == OrderStatus.PAYMENT_PENDING) {
                child.setStatus(OrderStatus.FAILED);
                child.setPaymentStatus(OrderPaymentStatus.FAILED);
                child.setDeliveryAssignmentStatus(DeliveryAssignmentStatus.CANCELLED);
                child.setCancellationReason(reason);
                child.setCancelledByRole("SYSTEM");
                child.setCancelledAt(now);
                orderRepository.save(child);

                orderService.recordStatusChange(
                        child, OrderStatus.PAYMENT_PENDING, OrderStatus.FAILED,
                        null, "SYSTEM", "Reservation Timeout", reason
                );
            }
        }
    }

    private ComputedCartPlan buildCheckoutPlan(
            Long customerId,
            Long requestedAddressId,
            String couponCode,
            boolean strictMode) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));
        if (customer.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Customer account is not active for checkout.");
        }

        List<CartItem> items = cartItemRepository.findAllForCustomer(customerId);
        List<String> blockers = new ArrayList<>();
        if (items.isEmpty()) {
            blockers.add("Your cart is empty.");
        }

        CustomerAddress address = resolveAddress(customerId, requestedAddressId, strictMode);
        if (address == null) {
            blockers.add("Select a valid delivery address.");
        }

        Map<String, MedicineClassificationRule> ruleMap = classificationService.loadRuleMap();
        Map<Long, List<CartItem>> itemsByPharmacy = new LinkedHashMap<>();
        for (CartItem item : items) {
            itemsByPharmacy.computeIfAbsent(item.getPharmacy().getId(), k -> new ArrayList<>()).add(item);
        }

        BigDecimal totalItemsSubtotal = BigDecimal.ZERO;
        for (CartItem item : items) {
            totalItemsSubtotal = totalItemsSubtotal.add(
                    item.getMedicine().getSellingPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
            );
        }
        totalItemsSubtotal = totalItemsSubtotal.setScale(2, RoundingMode.HALF_UP);

        CouponEvaluation couponEval = couponService.evaluate(
                couponCode,
                totalItemsSubtotal,
                strictMode && couponCode != null && !couponCode.isBlank()
        );
        BigDecimal totalDiscount = couponEval.discountAmount();

        BigDecimal taxRate = checkoutProperties.getTaxRatePercent();
        BigDecimal commissionRate = checkoutProperties.getPlatformCommissionPercent();

        List<PharmacyPlan> pharmacyPlans = new ArrayList<>();
        BigDecimal remainingDiscount = totalDiscount;
        int groupIdx = 0;
        int groupTotalCount = itemsByPharmacy.size();

        BigDecimal totalDeliveryFees = BigDecimal.ZERO;
        BigDecimal totalTaxes = BigDecimal.ZERO;
        boolean allReady = !items.isEmpty() && address != null;

        for (List<CartItem> groupItems : itemsByPharmacy.values()) {
            groupIdx++;
            Pharmacy pharmacy = groupItems.get(0).getPharmacy();
            List<String> groupBlockers = new ArrayList<>();
            Map<Long, MedicineClassificationRule> itemRuleMap = new LinkedHashMap<>();

            if (pharmacy.getVerificationStatus() != VerificationStatus.APPROVED
                    || pharmacy.getUser().getAccountStatus() != AccountStatus.ACTIVE
                    || !pharmacy.isAcceptingOrders()) {
                groupBlockers.add(pharmacy.getPharmacyName() + " is currently not accepting orders.");
            }

            BigDecimal groupSubtotal = BigDecimal.ZERO;
            for (CartItem ci : groupItems) {
                Medicine med = ci.getMedicine();
                String code = med.getClassificationCode() != null ? med.getClassificationCode().toUpperCase() : "OTC";
                MedicineClassificationRule rule = ruleMap.get(code);
                itemRuleMap.put(med.getId(), rule);

                if (med.getApprovalStatus() != MedicineApprovalStatus.APPROVED
                        || !med.isActive()
                        || !med.getCategory().isActive()) {
                    groupBlockers.add(med.getName() + " is not approved or active in the catalog.");
                }

                int available = med.getTotalAvailableQuantity() != null ? med.getTotalAvailableQuantity() : 0;
                if (available < ci.getQuantity()) {
                    groupBlockers.add(med.getName() + " has only " + available + " unit(s) available.");
                }

                int maxUnits = rule != null ? rule.getMaxUnitsPerOrder() : 50;
                if (ci.getQuantity() > maxUnits) {
                    groupBlockers.add(med.getName() + " exceeds max limit of " + maxUnits + " units per order.");
                }

                boolean rxRequired = med.isPrescriptionRequired() || (rule != null && rule.isPrescriptionRequired());
                if (rxRequired) {
                    Long rxId = ci.getPrescription() != null ? ci.getPrescription().getId() : null;
                    var rxEligibility = prescriptionService.checkDispatchEligibility(customerId, med.getId(), rxId, false);
                    if (!rxEligibility.eligible()) {
                        groupBlockers.add(rxEligibility.reason());
                    }
                }

                groupSubtotal = groupSubtotal.add(
                        med.getSellingPrice().multiply(BigDecimal.valueOf(ci.getQuantity()))
                );
            }
            groupSubtotal = groupSubtotal.setScale(2, RoundingMode.HALF_UP);

            PharmacyDeliveryZone zone = null;
            BigDecimal deliveryFee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (address != null) {
                zone = deliveryZoneRepository
                        .findActiveZoneForPharmacyAndPostalCode(pharmacy.getId(), address.getPostalCode())
                        .orElse(null);
                if (zone == null) {
                    groupBlockers.add(
                            pharmacy.getPharmacyName() + " does not service postal code " + address.getPostalCode() + "."
                    );
                } else {
                    deliveryFee = zone.getDeliveryFee().setScale(2, RoundingMode.HALF_UP);
                    if (groupSubtotal.compareTo(zone.getMinOrderAmount()) < 0) {
                        groupBlockers.add(
                                pharmacy.getPharmacyName() + " requires a minimum order of ₹"
                                        + zone.getMinOrderAmount().toPlainString() + "."
                        );
                    }
                }
            }

            BigDecimal allocatedDiscount;
            if (groupIdx == groupTotalCount) {
                allocatedDiscount = remainingDiscount;
            } else {
                allocatedDiscount = prorate(groupSubtotal, totalItemsSubtotal, totalDiscount);
                remainingDiscount = remainingDiscount.subtract(allocatedDiscount);
            }

            BigDecimal taxableSubtotal = groupSubtotal.subtract(allocatedDiscount).max(BigDecimal.ZERO);
            BigDecimal taxAmount = taxableSubtotal.multiply(taxRate).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal grandTotal = taxableSubtotal.add(deliveryFee).add(taxAmount).setScale(2, RoundingMode.HALF_UP);
            BigDecimal commissionAmount = taxableSubtotal.multiply(commissionRate).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal payoutAmount = taxableSubtotal.subtract(commissionAmount).add(deliveryFee).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            if (!groupBlockers.isEmpty()) {
                allReady = false;
                blockers.addAll(groupBlockers);
            }

            totalDeliveryFees = totalDeliveryFees.add(deliveryFee);
            totalTaxes = totalTaxes.add(taxAmount);

            pharmacyPlans.add(new PharmacyPlan(
                    pharmacy,
                    zone,
                    groupItems,
                    itemRuleMap,
                    groupSubtotal,
                    allocatedDiscount,
                    deliveryFee,
                    taxAmount,
                    grandTotal,
                    commissionAmount,
                    payoutAmount,
                    groupBlockers.isEmpty() && zone != null,
                    groupBlockers
            ));
        }

        BigDecimal grandTotal = totalItemsSubtotal
                .subtract(totalDiscount)
                .max(BigDecimal.ZERO)
                .add(totalDeliveryFees)
                .add(totalTaxes)
                .setScale(2, RoundingMode.HALF_UP);

        return new ComputedCartPlan(
                customer,
                address,
                couponEval.appliedCode(),
                couponEval.appliedCode() != null,
                couponEval.message(),
                totalItemsSubtotal,
                totalDiscount,
                totalDeliveryFees.setScale(2, RoundingMode.HALF_UP),
                totalTaxes.setScale(2, RoundingMode.HALF_UP),
                grandTotal,
                allReady,
                blockers,
                pharmacyPlans
        );
    }

    private CustomerAddress resolveAddress(Long customerId, Long addressId, boolean required) {
        if (addressId != null) {
            return addressRepository.findByIdAndCustomerId(addressId, customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Delivery address not found."));
        }
        Optional<CustomerAddress> defaultAddr = addressRepository.findByCustomerIdAndDefaultAddressTrue(customerId);
        if (required && defaultAddr.isEmpty()) {
            throw new BadRequestException("A valid delivery address must be selected before creating a checkout session.");
        }
        return defaultAddr.orElse(null);
    }

    private BigDecimal prorate(BigDecimal part, BigDecimal whole, BigDecimal amountToDistribute) {
        if (whole.compareTo(BigDecimal.ZERO) <= 0 || amountToDistribute.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amountToDistribute
                .multiply(part)
                .divide(whole, 2, RoundingMode.HALF_UP);
    }

    private String formatFullAddress(CustomerAddress addr) {
        StringBuilder sb = new StringBuilder(addr.getAddressLine1());
        if (addr.getAddressLine2() != null && !addr.getAddressLine2().isBlank()) {
            sb.append(", ").append(addr.getAddressLine2());
        }
        if (addr.getLandmark() != null && !addr.getLandmark().isBlank()) {
            sb.append(" (").append(addr.getLandmark()).append(")");
        }
        return sb.toString();
    }

    private CheckoutSessionResponse buildCheckoutResponse(CheckoutSession session) {
        List<Order> childOrders = orderRepository.findByCheckoutSessionIdOrderByIdAsc(session.getId());
        List<OrderResponse> orderDtos = childOrders.stream()
                .map(o -> orderService.loadFullOrder(o, false))
                .toList();
        return CheckoutSessionResponse.from(session, razorpayProperties.getKeyId(), orderDtos);
    }
}
