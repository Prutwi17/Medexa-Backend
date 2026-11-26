package com.medexa.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class MedexaException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;

    public MedexaException(HttpStatus status, ErrorCode errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public MedexaException(HttpStatus status, ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}
