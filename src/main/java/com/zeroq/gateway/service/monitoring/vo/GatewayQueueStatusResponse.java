package com.zeroq.gateway.service.monitoring.vo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GatewayQueueStatusResponse {
    private long telemetryPending;
    private long telemetryFailed;
    private long heartbeatPending;
    private long heartbeatFailed;
    private long commandDispatchPending;
    private long commandAckPending;
    private long commandAckFailed;
}
