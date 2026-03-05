package com.zeroq.gateway.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.node")
public class GatewayNodeProperties {
    private String gatewayId = "GW-UNKNOWN";
    private String localApiKey = "change-me";
}
