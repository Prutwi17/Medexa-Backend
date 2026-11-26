package com.medexa.auth.exception;

import com.medexa.common.exception.ErrorCode;
import com.medexa.common.exception.MedexaException;
import org.springframework.http.HttpStatus;

public class AccountStatusAuthException extends MedexaException {

    public AccountStatusAuthException(HttpStatus status, ErrorCode errorCode, String message) {
        super(status, errorCode, message);
    }
}
