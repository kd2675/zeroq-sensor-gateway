package com.zeroq.gateway.infrastructure.cloud;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GatewayResponseData<T> {
    private boolean success;
    private Integer code;
    private String message;
    private T data;
}
