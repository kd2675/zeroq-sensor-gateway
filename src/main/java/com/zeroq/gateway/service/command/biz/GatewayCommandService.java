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
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GatewayCommandService {
    private final GatewayCommandBufferRepository gatewayCommandBufferRepository;
    private final GatewayCommandAckOutboxRepository gatewayCommandAckOutboxRepository;

    /** 장치 전달 전·후이지만 최종 ACK 전인 로컬 명령을 최대 100개 조회한다. */
    public List<GatewayCommandResponse> getPendingCommands(int limit) {
        int capped = Math.max(1, Math.min(limit, 100));
        return gatewayCommandBufferRepository.findTop100ByCommandStatusInOrderByRequestedAtAsc(
                        List.of(
                                GatewayCommandBufferStatus.PENDING_DISPATCH,
                                GatewayCommandBufferStatus.DISPATCHED
                        )
                ).stream()
                .limit(capped)
                .map(GatewayCommandResponse::from)
                .toList();
    }

    /** 로컬 장치 전달 완료 시각과 DISPATCHED 상태를 기록한다. */
    @Transactional
    public GatewayCommandResponse markDispatched(Long cloudCommandId) {
        GatewayCommandBuffer command = gatewayCommandBufferRepository.findByCloudCommandId(cloudCommandId)
                .orElseThrow(() -> new GatewayException.ResourceNotFoundException(
                        "GatewayCommandBuffer",
                        "cloudCommandId",
                        cloudCommandId
                ));

        command.setCommandStatus(GatewayCommandBufferStatus.DISPATCHED);
        command.setLastDispatchAt(LocalDateTime.now(ZoneOffset.UTC));
        gatewayCommandBufferRepository.save(command);

        return GatewayCommandResponse.from(command);
    }

    /**
     * 허용된 최종 상태를 검증한 뒤 ACK outbox 생성과 command 상태 변경을 한 트랜잭션에 묶는다.
     */
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
                ? LocalDateTime.now(ZoneOffset.UTC)
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
