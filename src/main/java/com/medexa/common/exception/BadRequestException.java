package com.medexa.common.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends MedexaException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, message);
    }
}
