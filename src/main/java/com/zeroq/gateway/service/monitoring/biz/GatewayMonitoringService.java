package com.zeroq.gateway.service.monitoring.biz;

import com.zeroq.gateway.common.config.GatewaySyncProperties;
import com.zeroq.gateway.database.pub.entity.BufferSyncStatus;
import com.zeroq.gateway.database.pub.entity.GatewayCommandBufferStatus;
import com.zeroq.gateway.database.pub.repository.GatewayCommandAckOutboxRepository;
import com.zeroq.gateway.database.pub.repository.GatewayCommandBufferRepository;
import com.zeroq.gateway.database.pub.repository.GatewayHeartbeatBufferRepository;
import com.zeroq.gateway.database.pub.repository.GatewayTelemetryBufferRepository;
import com.zeroq.gateway.service.monitoring.vo.GatewayQueueStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GatewayMonitoringService {
    private final GatewaySyncProperties gatewaySyncProperties;
    private final GatewayTelemetryBufferRepository gatewayTelemetryBufferRepository;
    private final GatewayHeartbeatBufferRepository gatewayHeartbeatBufferRepository;
    private final GatewayCommandBufferRepository gatewayCommandBufferRepository;
    private final GatewayCommandAckOutboxRepository gatewayCommandAckOutboxRepository;

    public GatewayQueueStatusResponse getQueueStatus() {
        int maxRetry = gatewaySyncProperties.getMaxRetry();
        return GatewayQueueStatusResponse.builder()
                .telemetryPending(gatewayTelemetryBufferRepository.countBySyncStatus(BufferSyncStatus.PENDING))
                .telemetryFailed(gatewayTelemetryBufferRepository.countBySyncStatus(BufferSyncStatus.FAILED))
                .telemetryExhausted(gatewayTelemetryBufferRepository.countBySyncStatusAndRetryCountGreaterThanEqual(
                        BufferSyncStatus.FAILED,
                        maxRetry
                ))
                .heartbeatPending(gatewayHeartbeatBufferRepository.countBySyncStatus(BufferSyncStatus.PENDING))
                .heartbeatFailed(gatewayHeartbeatBufferRepository.countBySyncStatus(BufferSyncStatus.FAILED))
                .heartbeatExhausted(gatewayHeartbeatBufferRepository.countBySyncStatusAndRetryCountGreaterThanEqual(
                        BufferSyncStatus.FAILED,
                        maxRetry
                ))
                .commandDispatchPending(gatewayCommandBufferRepository.countByCommandStatus(GatewayCommandBufferStatus.PENDING_DISPATCH))
                .commandAckPending(gatewayCommandAckOutboxRepository.countBySyncStatus(BufferSyncStatus.PENDING))
                .commandAckFailed(gatewayCommandAckOutboxRepository.countBySyncStatus(BufferSyncStatus.FAILED))
                .commandAckExhausted(gatewayCommandAckOutboxRepository.countBySyncStatusAndRetryCountGreaterThanEqual(
                        BufferSyncStatus.FAILED,
                        maxRetry
                ))
                .build();
    }
}
