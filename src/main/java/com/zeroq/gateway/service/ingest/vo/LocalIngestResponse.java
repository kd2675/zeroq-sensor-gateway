package com.zeroq.gateway.service.ingest.vo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LocalIngestResponse {
    private int telemetryAccepted;
    private int heartbeatAccepted;
}
