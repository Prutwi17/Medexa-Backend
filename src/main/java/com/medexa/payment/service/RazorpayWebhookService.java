package com.medexa.payment.service;

import com.medexa.checkout.config.RazorpayProperties;
import com.medexa.checkout.service.CheckoutService;
import com.medexa.checkout.service.RazorpaySignatureUtils;
import com.medexa.common.exception.BusinessRuleException;
import com.medexa.payment.RazorpayWebhookEvent;
import com.medexa.payment.RazorpayWebhookEventRepository;
import com.medexa.payment.WebhookProcessingStatus;
import com.medexa.refund.service.RefundService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayWebhookService {

    private final RazorpayProperties razorpayProperties;
    private final RazorpayWebhookEventRepository webhookEventRepository;
    private final CheckoutService checkoutService;
    private final RefundService refundService;
    private final ObjectMapper objectMapper;

    @Transactional
    public WebhookDispatchResult processWebhook(String rawBody, String signature, String headerEventId) {
        String secret = razorpayProperties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new BusinessRuleException("Razorpay webhook secret is not configured.");
        }
        String payload = rawBody != null ? rawBody : "";
        if (payload.isBlank() || !RazorpaySignatureUtils.verifyWebhookSignature(payload, signature, secret)) {
            throw new BusinessRuleException("Invalid Razorpay webhook signature.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new BusinessRuleException("Malformed JSON webhook payload.");
        }

        String eventType = textOrNull(root.path("event"));
        if (eventType == null) {
            throw new BusinessRuleException("Webhook event type is missing.");
        }

        String bodyEventId = textOrNull(root.path("id"));
        String eventId = firstNonBlank(headerEventId, bodyEventId);
        if (eventId == null) {
            eventId = "evt_sha256_" + RazorpaySignatureUtils.sha256Hex(payload).substring(0, 32);
        }

        if (webhookEventRepository.existsByEventId(eventId)) {
            return new WebhookDispatchResult(eventId, eventType, "IGNORED_DUPLICATE");
        }

        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        JsonNode orderEntity = root.path("payload").path("order").path("entity");
        JsonNode refundEntity = root.path("payload").path("refund").path("entity");

        String razorpayOrderId = firstNonBlank(
                textOrNull(paymentEntity.path("order_id")),
                textOrNull(orderEntity.path("id"))
        );
        String razorpayPaymentId = firstNonBlank(
                textOrNull(paymentEntity.path("id")),
                textOrNull(refundEntity.path("payment_id"))
        );
        String razorpayRefundId = textOrNull(refundEntity.path("id"));

        RazorpayWebhookEvent eventRecord = RazorpayWebhookEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .razorpayOrderId(razorpayOrderId)
                .razorpayPaymentId(razorpayPaymentId)
                .razorpayRefundId(razorpayRefundId)
                .signatureVerified(true)
                .processingStatus(WebhookProcessingStatus.RECEIVED)
                .rawPayload(payload)
                .receivedAt(Instant.now())
                .build();

        try {
            eventRecord = webhookEventRepository.saveAndFlush(eventRecord);
        } catch (DataIntegrityViolationException dup) {
            return new WebhookDispatchResult(eventId, eventType, "IGNORED_DUPLICATE");
        }

        String result;
        try {
            result = switch (eventType) {
                case "payment.authorized" -> {
                    if (razorpayOrderId == null || razorpayPaymentId == null) {
                        yield "Ignored payment.authorized missing order_id or payment id";
                    }
                    yield checkoutService.markAuthorizedFromWebhook(razorpayOrderId, razorpayPaymentId, eventId);
                }
                case "payment.captured", "order.paid" -> {
                    if (razorpayOrderId == null || razorpayPaymentId == null) {
                        yield "Ignored " + eventType + " missing order_id or payment id";
                    }
                    long amountPaise = paymentEntity.path("amount").asLong(0L);
                    if (amountPaise <= 0L) {
                        amountPaise = orderEntity.path("amount_paid").asLong(0L);
                    }
                    yield checkoutService.finalizeCapturedFromWebhook(
                            razorpayOrderId, razorpayPaymentId, amountPaise, eventId
                    );
                }
                case "payment.failed" -> {
                    if (razorpayOrderId == null) {
                        yield "Ignored payment.failed missing order_id";
                    }
                    String errorCode = textOrNull(paymentEntity.path("error_code"));
                    String errorDescription = firstNonBlank(
                            textOrNull(paymentEntity.path("error_description")),
                            "Payment failed via Razorpay webhook"
                    );
                    yield checkoutService.failPaymentFromWebhook(
                            razorpayOrderId, razorpayPaymentId, errorCode, errorDescription, eventId
                    );
                }
                case "refund.processed", "refund.failed" ->
                        refundService.handleRefundWebhook(razorpayRefundId, razorpayPaymentId, eventType);
                default -> "Informative event recorded without state change: " + eventType;
            };

            eventRecord.setProcessingStatus(
                    result != null && result.startsWith("IGNORED_OUT_OF_ORDER")
                            ? WebhookProcessingStatus.IGNORED_OUT_OF_ORDER
                            : WebhookProcessingStatus.PROCESSED
            );
            eventRecord.setProcessingNotes(truncate(result, 500));
            eventRecord.setProcessedAt(Instant.now());
            webhookEventRepository.save(eventRecord);
            return new WebhookDispatchResult(eventId, eventType, result);
        } catch (RuntimeException ex) {
            eventRecord.setProcessingStatus(WebhookProcessingStatus.FAILED);
            eventRecord.setProcessingNotes(truncate(ex.getMessage(), 500));
            eventRecord.setProcessedAt(Instant.now());
            webhookEventRepository.save(eventRecord);
            throw ex;
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    public record WebhookDispatchResult(String eventId, String eventType, String status) {
    }
}
