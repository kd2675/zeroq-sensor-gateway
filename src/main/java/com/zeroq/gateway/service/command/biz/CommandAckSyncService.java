package com.zeroq.gateway.service.command.biz;

import com.zeroq.gateway.common.config.GatewaySyncProperties;
import com.zeroq.gateway.database.pub.entity.*;
import com.zeroq.gateway.database.pub.repository.GatewayCommandAckOutboxRepository;
import com.zeroq.gateway.database.pub.repository.GatewayCommandBufferRepository;
import com.zeroq.gateway.infrastructure.cloud.CloudSensorApiClient;
import com.zeroq.gateway.service.command.vo.LocalCommandAckRequest;
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
public class CommandAckSyncService {
    private final GatewaySyncProperties gatewaySyncProperties;
    private final GatewayCommandAckOutboxRepository gatewayCommandAckOutboxRepository;
    private final GatewayCommandBufferRepository gatewayCommandBufferRepository;
    private final CloudSensorApiClient cloudSensorApiClient;

    @Scheduled(fixedDelayString = "${gateway.sync.ack-fixed-delay-ms:5000}")
    @Transactional
    public void flushPendingAcks() {
        List<GatewayCommandAckOutbox> outboxes = gatewayCommandAckOutboxRepository
                .findTop200BySyncStatusInAndRetryCountLessThanOrderByIdAsc(
                        EnumSet.of(BufferSyncStatus.PENDING, BufferSyncStatus.FAILED),
                        gatewaySyncProperties.getMaxRetry()
                )
                .stream()
                .limit(gatewaySyncProperties.getBatchSize())
                .toList();

        for (GatewayCommandAckOutbox outbox : outboxes) {
            try {
                LocalCommandAckRequest request = new LocalCommandAckRequest();
                request.setStatus(outbox.getAckStatus());
                request.setAckPayload(outbox.getAckPayload());
                request.setFailureReason(outbox.getFailureReason());
                request.setAcknowledgedAt(outbox.getAcknowledgedAt());

                cloudSensorApiClient.ackCommand(outbox.getCloudCommandId(), request);

                outbox.setSyncStatus(BufferSyncStatus.SENT);
                outbox.setErrorMessage(null);
                outbox.setLastAttemptAt(LocalDateTime.now());
                gatewayCommandAckOutboxRepository.save(outbox);

                gatewayCommandBufferRepository.findByCloudCommandId(outbox.getCloudCommandId())
                        .ifPresent(command -> {
                            command.setLastCloudAckAt(LocalDateTime.now());
                            command.setFailureReason(outbox.getFailureReason());
                            if (outbox.getAckStatus() == SensorCommandStatus.ACKNOWLEDGED) {
                                command.setCommandStatus(GatewayCommandBufferStatus.ACKED);
                            } else if (outbox.getAckStatus() == SensorCommandStatus.CANCELED) {
                                command.setCommandStatus(GatewayCommandBufferStatus.CANCELED);
                            } else {
                                command.setCommandStatus(GatewayCommandBufferStatus.FAILED);
                            }
                            gatewayCommandBufferRepository.save(command);
                        });
            } catch (Exception ex) {
                outbox.setSyncStatus(BufferSyncStatus.FAILED);
                outbox.setRetryCount(outbox.getRetryCount() + 1);
                outbox.setErrorMessage(truncate(ex.getMessage(), 500));
                outbox.setLastAttemptAt(LocalDateTime.now());
                gatewayCommandAckOutboxRepository.save(outbox);
                log.warn("Command ACK sync failed. cloudCommandId={}, reason={}", outbox.getCloudCommandId(), ex.getMessage());
            }
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
