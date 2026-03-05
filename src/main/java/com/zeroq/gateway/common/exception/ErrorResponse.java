package com.zeroq.gateway.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private boolean success;
    private String code;
    private String message;
    private int status;
    private LocalDateTime timestamp;
    private String path;
    private List<FieldError> fieldErrors;

    public static ErrorResponse of(String code, String message, int status, String path) {
        return ErrorResponse.builder()
                .success(false)
                .code(code)
                .message(message)
                .status(status)
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
    }

    @Getter
    @Builder
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
}
