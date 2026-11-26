package com.medexa.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends MedexaException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, message);
    }
}
