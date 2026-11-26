package com.medexa.payment.controller;

import com.medexa.common.api.ApiResponse;
import com.medexa.payment.service.RazorpayWebhookService;
import com.medexa.payment.service.RazorpayWebhookService.WebhookDispatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final RazorpayWebhookService webhookService;

    @PostMapping("/razorpay")
    public ApiResponse<WebhookDispatchResult> handleRazorpayWebhook(
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        WebhookDispatchResult result = webhookService.processWebhook(rawBody, signature, eventId);
        return ApiResponse.ok("Webhook handled", result);
    }
}
