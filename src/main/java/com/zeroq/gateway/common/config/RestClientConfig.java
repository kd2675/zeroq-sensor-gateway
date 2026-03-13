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
                        String signature = gatewayServiceRequestSigner.sign(
                                gatewayNodeProperties.getGatewayId(),
                                request.getMethod().name(),
                                path,
                                timestamp,
                                nonce,
                                cloudProperties.getServiceAuthSecret()
                        );
                        request.getHeaders().set("X-Gateway-Id", gatewayNodeProperties.getGatewayId());
                        request.getHeaders().set("X-Gateway-Timestamp", timestamp);
                        request.getHeaders().set("X-Gateway-Nonce", nonce);
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
