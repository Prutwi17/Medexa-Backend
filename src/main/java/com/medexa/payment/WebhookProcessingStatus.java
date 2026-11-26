package com.medexa.payment;

public enum WebhookProcessingStatus {
    RECEIVED,
    PROCESSED,
    IGNORED_DUPLICATE,
    IGNORED_OUT_OF_ORDER,
    FAILED
}
