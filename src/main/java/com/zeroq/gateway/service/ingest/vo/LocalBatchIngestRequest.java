package com.zeroq.gateway.service.ingest.vo;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class LocalBatchIngestRequest {
    @Valid
    private List<LocalTelemetryRequest> telemetries = new ArrayList<>();

    @Valid
    private List<LocalHeartbeatRequest> heartbeats = new ArrayList<>();
}
