package com.zeroq.gateway.common.exception;

import web.common.core.response.base.exception.GeneralException;
import web.common.core.response.base.vo.Code;

public class GatewayException extends GeneralException {

    public GatewayException(Code errorCode, String message) {
        super(errorCode, message);
    }

    public Integer getCode() {
        return getErrorCode().getCode();
    }

    public int getStatus() {
        return getErrorCode().getHttpStatus().value();
    }

    public static class ResourceNotFoundException extends GatewayException {
        public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
            super(
                    Code.NOT_FOUND,
                    String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue)
            );
        }
    }

    public static class ValidationException extends GatewayException {
        public ValidationException(String message) {
            super(Code.VALIDATION_ERROR, message);
        }
    }

    public static class ForbiddenException extends GatewayException {
        public ForbiddenException(String message) {
            super(Code.FORBIDDEN, message);
        }
    }

    public static class RemoteCallException extends GatewayException {
        public RemoteCallException(String message) {
            super(Code.BAD_GATEWAY, message);
        }
    }
}
