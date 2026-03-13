package com.zeroq.gateway.service.monitoring.biz;

import com.zeroq.gateway.common.config.GatewayNodeProperties;
import com.zeroq.gateway.database.pub.repository.GatewayManagedSensorRepository;
import com.zeroq.gateway.infrastructure.cloud.CloudSensorApiClient;
import com.zeroq.gateway.service.monitoring.vo.GatewayCloudRuntimeMetricsSnapshot;
import com.zeroq.gateway.service.monitoring.vo.GatewayQueueStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GatewayStatusSyncService {
    private final GatewayMonitoringService gatewayMonitoringService;
    private final GatewayManagedSensorRepository gatewayManagedSensorRepository;
    private final GatewayNodeProperties gatewayNodeProperties;
    private final GatewayCloudRuntimeMetricsService gatewayCloudRuntimeMetricsService;
    private final CloudSensorApiClient cloudSensorApiClient;

    @Scheduled(fixedDelayString = "${gateway.sync.status-fixed-delay-ms:15000}")
    public void pushGatewayStatus() {
        GatewayQueueStatusResponse queueStatus = gatewayMonitoringService.getQueueStatus();
        GatewayCloudRuntimeMetricsSnapshot metrics = gatewayCloudRuntimeMetricsService.snapshot();

        CloudSensorApiClient.CloudGatewayStatus request = new CloudSensorApiClient.CloudGatewayStatus();
        request.setGatewayId(gatewayNodeProperties.getGatewayId());
        request.setStatus(deriveStatus(queueStatus, metrics));
        request.setHeartbeatAt(LocalDateTime.now());
        request.setFirmwareVersion(blankToNull(gatewayNodeProperties.getFirmwareVersion()));
        request.setIpAddress(blankToNull(gatewayNodeProperties.getIpAddress()));
        request.setCurrentSensorLoad(Math.toIntExact(gatewayManagedSensorRepository.countByActiveTrue()));
        request.setLatencyMs(metrics.getAverageLatencyMs());
        request.setPacketLossPercent(metrics.getPacketLossPercent());
        request.setTelemetryPending(queueStatus.getTelemetryPending());
        request.setTelemetryFailed(queueStatus.getTelemetryFailed());
        request.setHeartbeatPending(queueStatus.getHeartbeatPending());
        request.setHeartbeatFailed(queueStatus.getHeartbeatFailed());
        request.setCommandDispatchPending(queueStatus.getCommandDispatchPending());
        request.setCommandAckPending(queueStatus.getCommandAckPending());

        try {
            cloudSensorApiClient.postGatewayStatus(request);
        } catch (Exception ex) {
            log.warn("Gateway status sync failed. gatewayId={}, reason={}", request.getGatewayId(), ex.getMessage());
        }
    }

    private String deriveStatus(
            GatewayQueueStatusResponse queueStatus,
            GatewayCloudRuntimeMetricsSnapshot metrics
    ) {
        if (metrics.getConsecutiveFailureCount() >= 3) {
            return "OFFLINE";
        }
        if (queueStatus.getTelemetryExhausted() > 0
                || queueStatus.getHeartbeatExhausted() > 0
                || queueStatus.getCommandAckExhausted() > 0) {
            return "WARNING";
        }
        if ((metrics.getPacketLossPercent() != null && metrics.getPacketLossPercent() >= 25.0)
                || queueStatus.getTelemetryFailed() > 0
                || queueStatus.getHeartbeatFailed() > 0
                || queueStatus.getCommandAckFailed() > 0
                || queueStatus.getCommandDispatchPending() > 0) {
            return "WARNING";
        }
        return "ONLINE";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
