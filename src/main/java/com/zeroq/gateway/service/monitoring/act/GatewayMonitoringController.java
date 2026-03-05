package com.zeroq.gateway.service.monitoring.act;

import com.zeroq.gateway.common.security.GatewayApiKeyGuard;
import com.zeroq.gateway.service.command.biz.CommandAckSyncService;
import com.zeroq.gateway.service.command.biz.CommandPullSyncService;
import com.zeroq.gateway.service.ingest.biz.CloudIngestSyncService;
import com.zeroq.gateway.service.monitoring.biz.GatewayMonitoringService;
import com.zeroq.gateway.service.monitoring.vo.GatewayQueueStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import web.common.core.response.base.dto.ResponseDataDTO;

@RestController
@RequestMapping("/api/zeroq/gateway/v1/monitoring")
@RequiredArgsConstructor
public class GatewayMonitoringController {
    private final GatewayApiKeyGuard gatewayApiKeyGuard;
    private final GatewayMonitoringService gatewayMonitoringService;
    private final CloudIngestSyncService cloudIngestSyncService;
    private final CommandPullSyncService commandPullSyncService;
    private final CommandAckSyncService commandAckSyncService;

    @GetMapping("/queue-status")
    public ResponseDataDTO<GatewayQueueStatusResponse> getQueueStatus(HttpServletRequest httpServletRequest) {
        gatewayApiKeyGuard.requireGatewayApiKey(httpServletRequest);
        return ResponseDataDTO.of(gatewayMonitoringService.getQueueStatus(), "게이트웨이 큐 상태 조회 완료");
    }

    @PostMapping("/sync-now")
    public ResponseDataDTO<SyncNowResponse> syncNow(HttpServletRequest httpServletRequest) {
        gatewayApiKeyGuard.requireGatewayApiKey(httpServletRequest);
        cloudIngestSyncService.flushPendingBuffers();
        commandPullSyncService.pollPendingCommands();
        commandAckSyncService.flushPendingAcks();
        return ResponseDataDTO.of(SyncNowResponse.builder().triggered(true).build(), "수동 동기화 실행 완료");
    }

    @Getter
    @Builder
    private static class SyncNowResponse {
        private boolean triggered;
    }
}
