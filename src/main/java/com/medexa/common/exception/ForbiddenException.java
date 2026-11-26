package com.medexa.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends MedexaException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, message);
    }
}
