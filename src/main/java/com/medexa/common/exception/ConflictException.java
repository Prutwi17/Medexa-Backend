package com.medexa.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends MedexaException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }
}
