package com.zeroq.gateway.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.cloud")
public class GatewayCloudProperties {
    private String baseUrl = "http://localhost:8080";
    private String authToken;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 10000;
}
