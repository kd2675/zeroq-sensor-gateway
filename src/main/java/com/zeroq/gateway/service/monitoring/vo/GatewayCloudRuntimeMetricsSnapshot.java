package com.zeroq.gateway.service.monitoring.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class GatewayCloudRuntimeMetricsSnapshot {
    private Integer averageLatencyMs;
    private Double packetLossPercent;
    private LocalDateTime lastSuccessfulCloudSyncAt;
    private int consecutiveFailureCount;
}
