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

    /**
     * 로컬 센서 측정을 H2 전송 버퍼에 먼저 저장한다. 클라우드 전송 성공 여부와 무관하게 수집을 수락한다.
     */
    @PostMapping("/telemetry")
    public ResponseDataDTO<LocalIngestResponse> ingestTelemetry(
            @Valid @RequestBody LocalTelemetryRequest request,
            HttpServletRequest httpServletRequest
    ) {
        gatewayApiKeyGuard.requireGatewayApiKey(httpServletRequest);
        return ResponseDataDTO.of(localSensorIngestService.ingestTelemetry(request), "로컬 텔레메트리 저장 완료");
    }

    /**
     * 로컬 센서 heartbeat를 중복 제거 가능한 H2 버퍼에 저장한다.
     */
    @PostMapping("/heartbeat")
    public ResponseDataDTO<LocalIngestResponse> ingestHeartbeat(
            @Valid @RequestBody LocalHeartbeatRequest request,
            HttpServletRequest httpServletRequest
    ) {
        gatewayApiKeyGuard.requireGatewayApiKey(httpServletRequest);
        return ResponseDataDTO.of(localSensorIngestService.ingestHeartbeat(request), "로컬 하트비트 저장 완료");
    }

    /**
     * 여러 telemetry와 heartbeat를 같은 로컬 수집 규칙으로 일괄 버퍼링한다.
     */
    @PostMapping("/batch")
    public ResponseDataDTO<LocalIngestResponse> ingestBatch(
            @Valid @RequestBody LocalBatchIngestRequest request,
            HttpServletRequest httpServletRequest
    ) {
        gatewayApiKeyGuard.requireGatewayApiKey(httpServletRequest);
        return ResponseDataDTO.of(localSensorIngestService.ingestBatch(request), "로컬 배치 저장 완료");
    }
}
