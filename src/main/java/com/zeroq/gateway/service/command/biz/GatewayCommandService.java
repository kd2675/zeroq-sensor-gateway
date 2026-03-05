package com.zeroq.gateway.service.command.biz;

import com.zeroq.gateway.common.exception.GatewayException;
import com.zeroq.gateway.database.pub.entity.*;
import com.zeroq.gateway.database.pub.repository.GatewayCommandAckOutboxRepository;
import com.zeroq.gateway.database.pub.repository.GatewayCommandBufferRepository;
import com.zeroq.gateway.service.command.vo.GatewayCommandResponse;
import com.zeroq.gateway.service.command.vo.LocalCommandAckRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GatewayCommandService {
    private final GatewayCommandBufferRepository gatewayCommandBufferRepository;
    private final GatewayCommandAckOutboxRepository gatewayCommandAckOutboxRepository;

    public List<GatewayCommandResponse> getPendingCommands(int limit) {
        int capped = Math.max(1, Math.min(limit, 100));
        return gatewayCommandBufferRepository.findTop100ByCommandStatusOrderByRequestedAtAsc(
                        GatewayCommandBufferStatus.PENDING_DISPATCH
                ).stream()
                .limit(capped)
                .map(GatewayCommandResponse::from)
                .toList();
    }

    @Transactional
    public GatewayCommandResponse markDispatched(Long cloudCommandId) {
        GatewayCommandBuffer command = gatewayCommandBufferRepository.findByCloudCommandId(cloudCommandId)
                .orElseThrow(() -> new GatewayException.ResourceNotFoundException(
                        "GatewayCommandBuffer",
                        "cloudCommandId",
                        cloudCommandId
                ));

        command.setCommandStatus(GatewayCommandBufferStatus.DISPATCHED);
        command.setLastDispatchAt(LocalDateTime.now());
        gatewayCommandBufferRepository.save(command);

        return GatewayCommandResponse.from(command);
    }

    @Transactional
    public GatewayCommandResponse enqueueLocalAck(Long cloudCommandId, LocalCommandAckRequest request) {
        GatewayCommandBuffer command = gatewayCommandBufferRepository.findByCloudCommandId(cloudCommandId)
                .orElseThrow(() -> new GatewayException.ResourceNotFoundException(
                        "GatewayCommandBuffer",
                        "cloudCommandId",
                        cloudCommandId
                ));

        if (request.getStatus() != SensorCommandStatus.ACKNOWLEDGED
                && request.getStatus() != SensorCommandStatus.FAILED
                && request.getStatus() != SensorCommandStatus.CANCELED) {
            throw new GatewayException.ValidationException(
                    "status must be ACKNOWLEDGED, FAILED, or CANCELED"
            );
        }

        if (request.getStatus() == SensorCommandStatus.FAILED
                && (request.getFailureReason() == null || request.getFailureReason().isBlank())) {
            throw new GatewayException.ValidationException("failureReason is required when status is FAILED");
        }

        LocalDateTime acknowledgedAt = request.getAcknowledgedAt() == null
                ? LocalDateTime.now()
                : request.getAcknowledgedAt();

        GatewayCommandAckOutbox ackOutbox = GatewayCommandAckOutbox.builder()
                .cloudCommandId(cloudCommandId)
                .sensorId(command.getSensorId())
                .ackStatus(request.getStatus())
                .ackPayload(request.getAckPayload())
                .failureReason(request.getFailureReason())
                .acknowledgedAt(acknowledgedAt)
                .syncStatus(BufferSyncStatus.PENDING)
                .build();
        gatewayCommandAckOutboxRepository.save(ackOutbox);

        command.setCommandStatus(GatewayCommandBufferStatus.ACK_PENDING_CLOUD);
        command.setFailureReason(request.getFailureReason());
        gatewayCommandBufferRepository.save(command);

        return GatewayCommandResponse.from(command);
    }
}
