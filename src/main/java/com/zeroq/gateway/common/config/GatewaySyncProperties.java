package com.zeroq.gateway.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.sync")
public class GatewaySyncProperties {
    private int batchSize = 100;
    private int maxRetry = 10;
    private long ingestFixedDelayMs = 5000L;
    private long commandPollFixedDelayMs = 10000L;
    private long ackFixedDelayMs = 5000L;
}
