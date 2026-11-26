package com.medexa.payment.service;

import com.medexa.checkout.config.RazorpayProperties;
import com.medexa.common.exception.BusinessRuleException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayGatewayClient {

    private static final String RAZORPAY_API_BASE = "https://api.razorpay.com/v1";

    private final RazorpayProperties razorpayProperties;
    private final ObjectMapper objectMapper;

    public record GatewayOrderResult(String orderId, long amountPaise, String currency, boolean remoteCreated) {
    }

    public record GatewayPaymentSnapshot(
            String paymentId,
            String orderId,
            String status,
            long amountPaise,
            String errorCode,
            String errorDescription
    ) {
    }

    public record GatewayRefundResult(String refundId, String status, long amountPaise) {
    }

    public boolean isLiveGatewayConfigured() {
        String keyId = razorpayProperties.getKeyId();
        String keySecret = razorpayProperties.getKeySecret();
        return keyId != null && !keyId.isBlank()
                && keySecret != null && !keySecret.isBlank()
                && !"rzp_test_medexa_local".equals(keyId)
                && !"your_razorpay_key_id".equals(keyId);
    }

    public GatewayOrderResult createOrder(String receipt, long amountPaise, String currency) {
        if (!isLiveGatewayConfigured()) {
            return new GatewayOrderResult("rzp_ord_" + receipt.toLowerCase(), amountPaise, currency, false);
        }

        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "amount", amountPaise,
                    "currency", currency,
                    "receipt", receipt,
                    "payment_capture", 1
            ));
            HttpResponse<String> response = sendRequest("POST", "/orders", body);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Razorpay order creation failed ({}): {}", response.statusCode(), response.body());
                throw new BusinessRuleException("Unable to initialize payment order with Razorpay.");
            }
            JsonNode json = objectMapper.readTree(response.body());
            return new GatewayOrderResult(
                    json.path("id").asText(),
                    json.path("amount").asLong(amountPaise),
                    json.path("currency").asText(currency),
                    true
            );
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Razorpay order creation communication error: {}", ex.getMessage());
            throw new BusinessRuleException("Payment gateway is temporarily unreachable. Retry checkout preparation.");
        }
    }

    public GatewayPaymentSnapshot fetchPayment(String razorpayPaymentId) {
        if (!isLiveGatewayConfigured()) {
            return new GatewayPaymentSnapshot(razorpayPaymentId, null, "captured", 0L, null, null);
        }

        try {
            HttpResponse<String> response = sendRequest("GET", "/payments/" + razorpayPaymentId, null);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Razorpay fetch payment {} returned HTTP {}: {}",
                        razorpayPaymentId, response.statusCode(), response.body());
                throw new BusinessRuleException("Could not verify payment status from Razorpay gateway.");
            }
            JsonNode json = objectMapper.readTree(response.body());
            return toPaymentSnapshot(json);
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessRuleException("Unable to query Razorpay payment verification API.");
        }
    }

    public GatewayPaymentSnapshot fetchLatestPaymentForOrder(String razorpayOrderId) {
        if (!isLiveGatewayConfigured()) {
            return null;
        }

        try {
            HttpResponse<String> response = sendRequest("GET", "/orders/" + razorpayOrderId + "/payments", null);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonNode items = objectMapper.readTree(response.body()).path("items");
            if (!items.isArray() || items.isEmpty()) {
                return null;
            }
            JsonNode chosen = items.get(items.size() - 1);
            for (JsonNode candidate : items) {
                if ("captured".equalsIgnoreCase(candidate.path("status").asText())) {
                    chosen = candidate;
                    break;
                }
            }
            return toPaymentSnapshot(chosen);
        } catch (Exception ex) {
            log.warn("Unable to inspect Razorpay order payments for {}: {}", razorpayOrderId, ex.getMessage());
            return null;
        }
    }

    public GatewayRefundResult createRefund(
            String razorpayPaymentId,
            long amountPaise,
            String refundReference,
            String reason) {
        if (!isLiveGatewayConfigured()) {
            return new GatewayRefundResult("rfnd_" + refundReference.toLowerCase(), "processed", amountPaise);
        }

        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "amount", amountPaise,
                    "speed", "normal",
                    "receipt", refundReference,
                    "notes", java.util.Map.of("reason", reason != null ? reason : "Order refund")
            ));
            HttpResponse<String> response = sendRequest(
                    "POST",
                    "/payments/" + razorpayPaymentId + "/refund",
                    body
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Razorpay refund creation failed ({}): {}", response.statusCode(), response.body());
                throw new BusinessRuleException("Razorpay refund processing failed: HTTP " + response.statusCode());
            }
            JsonNode json = objectMapper.readTree(response.body());
            return new GatewayRefundResult(
                    json.path("id").asText(),
                    json.path("status").asText("processed"),
                    json.path("amount").asLong(amountPaise)
            );
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessRuleException("Unable to reach Razorpay refund gateway API.");
        }
    }

    private GatewayPaymentSnapshot toPaymentSnapshot(JsonNode json) {
        return new GatewayPaymentSnapshot(
                json.path("id").asText(null),
                json.path("order_id").asText(null),
                json.path("status").asText(null),
                json.path("amount").asLong(0L),
                json.path("error_code").asText(null),
                json.path("error_description").asText(null)
        );
    }

    private HttpResponse<String> sendRequest(String method, String path, String jsonBody) throws Exception {
        String auth = Base64.getEncoder().encodeToString(
                (razorpayProperties.getKeyId() + ":" + razorpayProperties.getKeySecret())
                        .getBytes(StandardCharsets.UTF_8)
        );
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(RAZORPAY_API_BASE + path))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/json");
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "{}"));
        } else {
            builder.GET();
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
