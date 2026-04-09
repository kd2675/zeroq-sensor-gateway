package com.zeroq.gateway.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import web.common.core.response.base.dto.ResponseErrorDTO;
import web.common.core.response.base.vo.Code;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Test
    void handleGatewayException_preservesBadGatewayCode() {
        ResponseEntity<ResponseErrorDTO> response = globalExceptionHandler.handleGatewayException(
                new GatewayException.RemoteCallException("원격 호출 실패")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(Code.BAD_GATEWAY.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo("원격 호출 실패");
    }

    @Test
    void handleUnknownException_returnsInternalServerErrorWrapper() {
        ResponseEntity<ResponseErrorDTO> response = globalExceptionHandler.handleUnknownException(
                new IllegalStateException("boom")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(Code.INTERNAL_SERVER_ERROR.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo("Unexpected error occurred");
    }
}
