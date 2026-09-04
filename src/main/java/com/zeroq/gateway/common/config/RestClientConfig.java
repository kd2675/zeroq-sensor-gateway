package com.zeroq.gateway.common.config;

import com.zeroq.gateway.common.security.GatewayServiceRequestSigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;

@Configuration
public class RestClientConfig {

    /**
     * 클라우드 센서 API 전용 클라이언트를 만든다. 공유 비밀이 설정된 경우
     * 직렬화된 요청 본문과 query 포함 경로를 기준으로 매 요청을 HMAC 서명한다.
     */
    @Bean
    public RestClient cloudRestClient(
            GatewayCloudProperties cloudProperties,
            GatewayNodeProperties gatewayNodeProperties,
            GatewayServiceRequestSigner gatewayServiceRequestSigner
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(cloudProperties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(cloudProperties.getReadTimeoutMs());

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(cloudProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((request, body, execution) -> {
                    if (cloudProperties.getServiceAuthSecret() != null && !cloudProperties.getServiceAuthSecret().isBlank()) {
                        String timestamp = String.valueOf(Instant.now().toEpochMilli());
                        String nonce = UUID.randomUUID().toString();
                        String path = request.getURI().getRawQuery() == null
                                ? request.getURI().getRawPath()
                                : request.getURI().getRawPath() + "?" + request.getURI().getRawQuery();
                        String contentSha256 = gatewayServiceRequestSigner.sha256Hex(body);
                        String signature = gatewayServiceRequestSigner.sign(
                                gatewayNodeProperties.getGatewayId(),
                                request.getMethod().name(),
                                path,
                                timestamp,
                                nonce,
                                contentSha256,
                                cloudProperties.getServiceAuthSecret()
                        );
                        request.getHeaders().set("X-Gateway-Id", gatewayNodeProperties.getGatewayId());
                        request.getHeaders().set("X-Gateway-Timestamp", timestamp);
                        request.getHeaders().set("X-Gateway-Nonce", nonce);
                        request.getHeaders().set("X-Gateway-Content-SHA256", contentSha256);
                        request.getHeaders().set("X-Gateway-Signature", signature);
                    }
                    return execution.execute(request, body);
                });

        if (cloudProperties.getAuthToken() != null && !cloudProperties.getAuthToken().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cloudProperties.getAuthToken());
        }

        return builder.build();
    }
}
