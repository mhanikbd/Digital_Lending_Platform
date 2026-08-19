package com.naztech.lending.common.exception;

/**
 * Thrown when a request is well-formed but breaks a business or domain rule.
 * Carries an {@link ErrorCode} so the HTTP status is decided in one place.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
