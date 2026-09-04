package com.zeroq.gateway.common.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class GatewayServiceRequestSigner {
    /**
     * gatewayId, HTTP 메서드, query 포함 경로, timestamp, nonce, 본문 SHA-256을
     * 줄바꿈으로 결합해 클라우드와 동일한 HMAC-SHA256 서명을 만든다.
     */
    public String sign(
            String gatewayId,
            String httpMethod,
            String requestPath,
            String timestamp,
            String nonce,
            String contentSha256,
            String secret
    ) {
        try {
            String payload = String.join(
                    "\n",
                    gatewayId,
                    httpMethod,
                    requestPath,
                    timestamp,
                    nonce,
                    contentSha256
            );
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign gateway request", ex);
        }
    }

    /** 직렬화가 끝난 실제 요청 body 바이트의 SHA-256 hex를 계산한다. */
    public String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash gateway request body", ex);
        }
    }
}
