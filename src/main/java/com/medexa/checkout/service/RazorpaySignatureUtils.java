package com.medexa.checkout.service;

import com.medexa.common.exception.BusinessRuleException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class RazorpaySignatureUtils {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private RazorpaySignatureUtils() {
    }

    public static String signPayload(String payload, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new BusinessRuleException("Payment gateway secret is not configured on the server.");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature.", ex);
        }
    }

    public static boolean verifyPaymentSignature(
            String razorpayOrderId,
            String razorpayPaymentId,
            String providedSignature,
            String keySecret) {
        if (razorpayOrderId == null || razorpayPaymentId == null || providedSignature == null) {
            return false;
        }
        String expected = signPayload(razorpayOrderId + "|" + razorpayPaymentId, keySecret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignature.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    public static boolean verifyWebhookSignature(
            String rawPayload,
            String providedSignature,
            String webhookSecret) {
        if (rawPayload == null || providedSignature == null || webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }
        String expected = signPayload(rawPayload, webhookSecret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignature.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] raw = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute SHA-256 hash.", ex);
        }
    }
}
