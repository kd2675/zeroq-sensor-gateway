package com.zeroq.gateway.service.ingest.act;

import com.zeroq.gateway.common.security.GatewayApiKeyGuard;
import com.zeroq.gateway.service.ingest.biz.LocalSensorIngestService;
import com.zeroq.gateway.service.ingest.vo.LocalBatchIngestRequest;
import com.zeroq.gateway.service.ingest.vo.LocalHeartbeatRequest;
import com.zeroq.gateway.service.ingest.vo.LocalIngestResponse;
import com.zeroq.gateway.service.ingest.vo.LocalTelemetryRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import web.common.core.response.base.dto.ResponseDataDTO;

@RestController
@RequestMapping("/api/zeroq/gateway/v1/local/ingest")
@RequiredArgsConstructor
public class LocalSensorIngestController {
    private final GatewayApiKeyGuard gatewayApiKeyGuard;
    private final LocalSensorIngestService localSensorIngestService;

    @PostMapping("/telemetry")
    public ResponseDataDTO<LocalIngestResponse> ingestTelemetry(
            @Valid @RequestBody LocalTelemetryRequest request,
            HttpServletRequest httpServletRequest
    ) {
        gatewayApiKeyGuard.requireGatewayApiKey(httpServletRequest);
        return ResponseDataDTO.of(localSensorIngestService.ingestTelemetry(request), "로컬 텔레메트리 저장 완료");
    }

    @PostMapping("/heartbeat")
    public ResponseDataDTO<LocalIngestResponse> ingestHeartbeat(
            @Valid @RequestBody LocalHeartbeatRequest request,
            HttpServletRequest httpServletRequest
    ) {
        gatewayApiKeyGuard.requireGatewayApiKey(httpServletRequest);
        return ResponseDataDTO.of(localSensorIngestService.ingestHeartbeat(request), "로컬 하트비트 저장 완료");
    }

    @PostMapping("/batch")
    public ResponseDataDTO<LocalIngestResponse> ingestBatch(
            @Valid @RequestBody LocalBatchIngestRequest request,
            HttpServletRequest httpServletRequest
    ) {
        gatewayApiKeyGuard.requireGatewayApiKey(httpServletRequest);
        return ResponseDataDTO.of(localSensorIngestService.ingestBatch(request), "로컬 배치 저장 완료");
    }
}
