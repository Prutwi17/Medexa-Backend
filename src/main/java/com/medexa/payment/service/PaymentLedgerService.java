package com.medexa.payment.service;

import com.medexa.checkout.CheckoutSession;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.payment.Payment;
import com.medexa.payment.PaymentEvent;
import com.medexa.payment.PaymentEventRepository;
import com.medexa.payment.PaymentRepository;
import com.medexa.payment.PaymentStatus;
import com.medexa.payment.RazorpayWebhookEventRepository;
import com.medexa.payment.dto.PaymentDtos.PaymentResponse;
import com.medexa.payment.dto.PaymentDtos.WebhookEventLogResponse;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentLedgerService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final RazorpayWebhookEventRepository webhookEventRepository;

    @Transactional
    public Payment createPaymentRecord(CheckoutSession session, String razorpayOrderId, String idempotencyKey) {
        return paymentRepository.findByCheckoutSessionId(session.getId())
                .orElseGet(() -> {
                    String ref = "PAY-" + session.getCheckoutReference().substring(4);
                    Payment payment = Payment.builder()
                            .paymentReference(ref)
                            .checkoutSession(session)
                            .customer(session.getCustomer())
                            .gatewayProvider("RAZORPAY")
                            .gatewayOrderId(razorpayOrderId)
                            .amount(session.getGrandTotal())
                            .refundedAmount(BigDecimal.ZERO)
                            .currency(session.getCurrency())
                            .status(PaymentStatus.CREATED)
                            .lastGatewayStatus("created")
                            .idempotencyKey(idempotencyKey)
                            .build();
                    paymentRepository.save(payment);
                    recordEvent(
                            payment,
                            "CHECKOUT_SESSION",
                            "PAYMENT_CREATED",
                            null,
                            PaymentStatus.CREATED,
                            razorpayOrderId,
                            "Internal payment record created for checkout " + session.getCheckoutReference()
                    );
                    return payment;
                });
    }

    public void recordEvent(
            Payment payment,
            String source,
            String type,
            PaymentStatus prev,
            PaymentStatus next,
            String gatewayRef,
            String notes) {
        PaymentEvent event = PaymentEvent.builder()
                .payment(payment)
                .eventSource(source)
                .eventType(type)
                .previousStatus(prev)
                .newStatus(next)
                .gatewayReference(gatewayRef)
                .notes(notes)
                .build();
        paymentEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> listCustomerPayments(
            Long customerId,
            PaymentStatus status,
            Pageable pageable) {
        return paymentRepository.findForCustomer(customerId, status, pageable)
                .map(p -> PaymentResponse.from(p, List.of()));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getCustomerPayment(Long customerId, String paymentReference) {
        Payment payment = paymentRepository.findByPaymentReferenceAndCustomerId(paymentReference, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found."));
        return PaymentResponse.from(payment, paymentEventRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(payment.getId()));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> listAdminPayments(
            PaymentStatus status,
            String query,
            Pageable pageable) {
        return paymentRepository.findForAdmin(status, query, pageable)
                .map(p -> PaymentResponse.from(p, List.of()));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getAdminPayment(String paymentReference) {
        Payment payment = paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found."));
        return PaymentResponse.from(payment, paymentEventRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(payment.getId()));
    }

    @Transactional(readOnly = true)
    public Page<WebhookEventLogResponse> listWebhookLogs(Pageable pageable) {
        return webhookEventRepository.findAllByOrderByReceivedAtDesc(pageable)
                .map(WebhookEventLogResponse::from);
    }
}
