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

    /**
     * 로컬 장비 API를 설정된 X-Gateway-Key와 정확히 일치하는 호출로 제한한다.
     * 이 키는 클라우드 HMAC 서명과 별개의 로컬 접근 제어다.
     */
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
