package com.medexa.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends MedexaException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
