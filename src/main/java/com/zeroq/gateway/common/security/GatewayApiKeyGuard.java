package com.zeroq.gateway.common.security;

import com.zeroq.gateway.common.config.GatewayNodeProperties;
import com.zeroq.gateway.common.exception.GatewayException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GatewayApiKeyGuard {
    private static final String GATEWAY_KEY_HEADER = "X-Gateway-Key";

    private final GatewayNodeProperties gatewayNodeProperties;

    public void requireGatewayApiKey(HttpServletRequest request) {
        String key = request.getHeader(GATEWAY_KEY_HEADER);
        if (key == null || key.isBlank()) {
            throw new GatewayException.ForbiddenException("Missing X-Gateway-Key header");
        }
        if (!key.equals(gatewayNodeProperties.getLocalApiKey())) {
            throw new GatewayException.ForbiddenException("Invalid gateway api key");
        }
    }
}
