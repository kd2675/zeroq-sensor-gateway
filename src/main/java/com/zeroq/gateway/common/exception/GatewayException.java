package com.zeroq.gateway.common.exception;

import lombok.Getter;

@Getter
public class GatewayException extends RuntimeException {
    private final String code;
    private final int status;

    public GatewayException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static class ResourceNotFoundException extends GatewayException {
        public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
            super(
                    "RESOURCE_NOT_FOUND",
                    String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue),
                    404
            );
        }
    }

    public static class ValidationException extends GatewayException {
        public ValidationException(String message) {
            super("VALIDATION_ERROR", message, 400);
        }
    }

    public static class ForbiddenException extends GatewayException {
        public ForbiddenException(String message) {
            super("FORBIDDEN", message, 403);
        }
    }

    public static class RemoteCallException extends GatewayException {
        public RemoteCallException(String message) {
            super("REMOTE_CALL_FAILED", message, 502);
        }
    }
}
