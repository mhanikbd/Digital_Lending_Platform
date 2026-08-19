package com.naztech.lending.common.exception;

import com.naztech.lending.common.api.ApiError;
import com.naztech.lending.common.api.ApiResponse;
import com.naztech.lending.common.correlation.CorrelationId;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every exception into the standard {@link ApiResponse} envelope.
 *
 * <p>Two rules apply throughout: the correlation id is always present so a caller
 * can quote it to support, and internal failure detail is logged but never
 * returned to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiError.FieldViolation::field))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, ApiError.of(
                ErrorCode.VALIDATION_FAILED.name(),
                ErrorCode.VALIDATION_FAILED.defaultMessage(),
                violations));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParameterValidation(ConstraintViolationException ex) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldViolation(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .sorted(Comparator.comparing(ApiError.FieldViolation::field))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, ApiError.of(
                ErrorCode.VALIDATION_FAILED.name(),
                ErrorCode.VALIDATION_FAILED.defaultMessage(),
                violations));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return respond(ErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ApiError.FieldViolation violation =
                new ApiError.FieldViolation(ex.getName(), "has an invalid value");
        return respond(ErrorCode.VALIDATION_FAILED, ApiError.of(
                ErrorCode.VALIDATION_FAILED.name(),
                ErrorCode.VALIDATION_FAILED.defaultMessage(),
                List.of(violation)));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return respond(ErrorCode.ACCESS_DENIED);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.info("Business rule rejected request [{}]: {}", ex.errorCode(), ex.getMessage());
        return respond(ex.errorCode(), ApiError.of(ex.errorCode().name(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception [correlationId={}]", CorrelationId.current(), ex);
        return respond(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> respond(ErrorCode code) {
        return respond(code, ApiError.of(code.name(), code.defaultMessage()));
    }

    private ResponseEntity<ApiResponse<Void>> respond(ErrorCode code, ApiError error) {
        return ResponseEntity.status(code.status()).body(ApiResponse.failure(error));
    }
}
