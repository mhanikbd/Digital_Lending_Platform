package com.naztech.lending.common.exception;

/** Thrown when an addressed resource does not exist or is out of the caller's scope. */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "%s '%s' was not found".formatted(resource, identifier));
    }
}
