package com.zeroq.gateway.service.ingest.biz;

import com.zeroq.gateway.common.config.GatewaySyncProperties;
import com.zeroq.gateway.database.pub.entity.BufferSyncStatus;
import com.zeroq.gateway.database.pub.entity.GatewayHeartbeatBuffer;
import com.zeroq.gateway.database.pub.entity.GatewayTelemetryBuffer;
import com.zeroq.gateway.database.pub.repository.GatewayHeartbeatBufferRepository;
import com.zeroq.gateway.database.pub.repository.GatewayTelemetryBufferRepository;
import com.zeroq.gateway.infrastructure.cloud.CloudSensorApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CloudIngestSyncService {
    private final GatewaySyncProperties gatewaySyncProperties;
    private final GatewayTelemetryBufferRepository gatewayTelemetryBufferRepository;
    private final GatewayHeartbeatBufferRepository gatewayHeartbeatBufferRepository;
    private final CloudSensorApiClient cloudSensorApiClient;

    @Scheduled(fixedDelayString = "${gateway.sync.ingest-fixed-delay-ms:5000}")
    @Transactional
    public void flushPendingBuffers() {
        List<GatewayTelemetryBuffer> telemetryBuffers = gatewayTelemetryBufferRepository
                .findTop200BySyncStatusInAndRetryCountLessThanOrderByIdAsc(
                        EnumSet.of(BufferSyncStatus.PENDING, BufferSyncStatus.FAILED),
                        gatewaySyncProperties.getMaxRetry()
                )
                .stream()
                .limit(gatewaySyncProperties.getBatchSize())
                .toList();

        List<GatewayHeartbeatBuffer> heartbeatBuffers = gatewayHeartbeatBufferRepository
                .findTop200BySyncStatusInAndRetryCountLessThanOrderByIdAsc(
                        EnumSet.of(BufferSyncStatus.PENDING, BufferSyncStatus.FAILED),
                        gatewaySyncProperties.getMaxRetry()
                )
                .stream()
                .limit(gatewaySyncProperties.getBatchSize())
                .toList();

        if (telemetryBuffers.isEmpty() && heartbeatBuffers.isEmpty()) {
            return;
        }

        try {
            CloudSensorApiClient.CloudBatchResponse response = cloudSensorApiClient.postBatchIngest(telemetryBuffers, heartbeatBuffers);
            boolean allTelemetrySynced = response.getTelemetryFailureCount() == 0
                    && response.getTelemetrySuccessCount() == telemetryBuffers.size();
            boolean allHeartbeatSynced = response.getHeartbeatFailureCount() == 0
                    && response.getHeartbeatSuccessCount() == heartbeatBuffers.size();

            if (allTelemetrySynced && allHeartbeatSynced) {
                markTelemetryAsSent(telemetryBuffers);
                markHeartbeatsAsSent(heartbeatBuffers);
                return;
            }

            telemetryBuffers.forEach(this::flushTelemetryOneByOne);
            heartbeatBuffers.forEach(this::flushHeartbeatOneByOne);
        } catch (Exception ex) {
            log.warn("Batch sync failed. fallback to one-by-one. reason={}", ex.getMessage());
            telemetryBuffers.forEach(this::flushTelemetryOneByOne);
            heartbeatBuffers.forEach(this::flushHeartbeatOneByOne);
        }
    }

    private void flushTelemetryOneByOne(GatewayTelemetryBuffer telemetryBuffer) {
        try {
            cloudSensorApiClient.postTelemetry(telemetryBuffer);
            telemetryBuffer.setSyncStatus(BufferSyncStatus.SENT);
            telemetryBuffer.setErrorMessage(null);
        } catch (Exception ex) {
            telemetryBuffer.setSyncStatus(BufferSyncStatus.FAILED);
            telemetryBuffer.setRetryCount(telemetryBuffer.getRetryCount() + 1);
            telemetryBuffer.setErrorMessage(truncate(ex.getMessage(), 500));
        }
        telemetryBuffer.setLastAttemptAt(LocalDateTime.now());
        gatewayTelemetryBufferRepository.save(telemetryBuffer);
    }

    private void flushHeartbeatOneByOne(GatewayHeartbeatBuffer heartbeatBuffer) {
        try {
            cloudSensorApiClient.postHeartbeat(heartbeatBuffer);
            heartbeatBuffer.setSyncStatus(BufferSyncStatus.SENT);
            heartbeatBuffer.setErrorMessage(null);
        } catch (Exception ex) {
            heartbeatBuffer.setSyncStatus(BufferSyncStatus.FAILED);
            heartbeatBuffer.setRetryCount(heartbeatBuffer.getRetryCount() + 1);
            heartbeatBuffer.setErrorMessage(truncate(ex.getMessage(), 500));
        }
        heartbeatBuffer.setLastAttemptAt(LocalDateTime.now());
        gatewayHeartbeatBufferRepository.save(heartbeatBuffer);
    }

    private void markTelemetryAsSent(List<GatewayTelemetryBuffer> telemetryBuffers) {
        LocalDateTime now = LocalDateTime.now();
        telemetryBuffers.forEach(buffer -> {
            buffer.setSyncStatus(BufferSyncStatus.SENT);
            buffer.setErrorMessage(null);
            buffer.setLastAttemptAt(now);
        });
        gatewayTelemetryBufferRepository.saveAll(telemetryBuffers);
    }

    private void markHeartbeatsAsSent(List<GatewayHeartbeatBuffer> heartbeatBuffers) {
        LocalDateTime now = LocalDateTime.now();
        heartbeatBuffers.forEach(buffer -> {
            buffer.setSyncStatus(BufferSyncStatus.SENT);
            buffer.setErrorMessage(null);
            buffer.setLastAttemptAt(now);
        });
        gatewayHeartbeatBufferRepository.saveAll(heartbeatBuffers);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
