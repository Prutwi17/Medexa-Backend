package com.medexa.auth.entity;

public enum LoginOutcome {
    SUCCESS,
    INVALID_CREDENTIALS,
    RATE_LIMITED,
    EMAIL_NOT_VERIFIED,
    PENDING_APPROVAL,
    ACCOUNT_BLOCKED,
    TOKEN_INVALID,
    TOKEN_REUSE_DETECTED
}
