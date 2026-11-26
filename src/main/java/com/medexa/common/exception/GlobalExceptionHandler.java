package com.medexa.common.exception;

import com.medexa.common.api.ApiError;
import com.medexa.common.api.ApiResponse;
import com.medexa.common.api.FieldValidationError;
import com.medexa.common.web.CorrelationIdContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MedexaException.class)
    public ResponseEntity<ApiResponse<Void>> handleMedexaException(
            MedexaException ex,
            HttpServletRequest request) {
        log.warn("Domain exception [code={}, corrId={}]: {}",
                ex.getErrorCode(), CorrelationIdContext.get(), ex.getMessage());
        ApiError error = ApiError.of(ex.getErrorCode().name(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(error, request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldValidationError)
                .toList();
        ApiError error = ApiError.of(
                ErrorCode.VALIDATION_ERROR.name(),
                "Validation failed for one or more fields.",
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(error, request.getRequestURI()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldValidationError(
                        violation.getPropertyPath().toString(),
                        violation.getMessage(),
                        violation.getInvalidValue()))
                .toList();
        ApiError error = ApiError.of(
                ErrorCode.VALIDATION_ERROR.name(),
                "Request parameter validation failed.",
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(error, request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCode.MALFORMED_REQUEST.name(),
                "Malformed or unreadable request payload."
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(error, request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCode.BAD_REQUEST.name(),
                "Invalid value for parameter '" + ex.getName() + "'."
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(error, request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCode.METHOD_NOT_ALLOWED.name(),
                "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint."
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(error, request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCode.ENDPOINT_NOT_FOUND.name(),
                "Requested endpoint was not found."
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(error, request.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCode.UNAUTHORIZED.name(),
                "Authentication is required to access this resource."
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(error, request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCode.FORBIDDEN.name(),
                "You do not have permission to perform this action."
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(error, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandledException(
            Exception ex,
            HttpServletRequest request) {
        log.error("Unhandled exception [corrId={}, path={}]",
                CorrelationIdContext.get(), request.getRequestURI(), ex);
        ApiError error = ApiError.of(
                ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected error occurred. Please contact support with correlation ID "
                        + CorrelationIdContext.get() + "."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(error, request.getRequestURI()));
    }

    private FieldValidationError toFieldValidationError(FieldError fieldError) {
        return new FieldValidationError(
                fieldError.getField(),
                fieldError.getDefaultMessage(),
                fieldError.getRejectedValue()
        );
    }
}
