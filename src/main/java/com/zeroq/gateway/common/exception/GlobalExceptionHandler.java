package com.zeroq.gateway.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import web.common.core.response.base.dto.ResponseErrorDTO;
import web.common.core.response.base.exception.GeneralException;
import web.common.core.response.base.vo.Code;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ResponseErrorDTO> handleGatewayException(GatewayException ex) {
        Code errorCode = ex.getErrorCode();
        log.warn("GatewayException: code={}, status={}, message={}",
                errorCode,
                errorCode.getHttpStatus().value(),
                ex.getMessage());

        ResponseErrorDTO body = ResponseErrorDTO.of(errorCode, ex.getMessage());
        return new ResponseEntity<>(body, errorCode.getHttpStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseErrorDTO> handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
        BindingResult bindingResult = ex.getBindingResult();
        var fieldErrors = bindingResult.getFieldErrors().stream()
                .map(error -> String.format("%s=%s (%s)",
                        error.getField(),
                        error.getRejectedValue(),
                        error.getDefaultMessage()))
                .collect(Collectors.toList());

        log.warn("Validation failed: path={}, errors={}",
                request.getDescription(false).replace("uri=", ""),
                fieldErrors);

        ResponseErrorDTO body = ResponseErrorDTO.of(Code.VALIDATION_ERROR, "Validation failed");
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseErrorDTO> handleUnknownException(Exception ex) {
        log.error("Unexpected gateway error", ex);

        ResponseErrorDTO body = ResponseErrorDTO.of(Code.INTERNAL_SERVER_ERROR, "Unexpected error occurred");
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
