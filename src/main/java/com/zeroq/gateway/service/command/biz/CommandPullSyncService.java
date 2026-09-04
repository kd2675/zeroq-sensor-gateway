package com.zeroq.gateway.service.command.biz;

import com.zeroq.gateway.database.pub.entity.GatewayCommandBuffer;
import com.zeroq.gateway.database.pub.entity.GatewayCommandBufferStatus;
import com.zeroq.gateway.database.pub.entity.GatewayManagedSensor;
import com.zeroq.gateway.database.pub.repository.GatewayCommandBufferRepository;
import com.zeroq.gateway.database.pub.repository.GatewayManagedSensorRepository;
import com.zeroq.gateway.infrastructure.cloud.CloudSensorApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommandPullSyncService {
    private final GatewayManagedSensorRepository gatewayManagedSensorRepository;
    private final GatewayCommandBufferRepository gatewayCommandBufferRepository;
    private final CloudSensorApiClient cloudSensorApiClient;

    /**
     * 활성 관리 센서별 클라우드 미완료 명령을 polling하고 cloudCommandId 기준으로 멱등 저장한다.
     * 한 센서의 통신 실패가 다른 센서 polling을 중단하지 않는다.
     */
    @Scheduled(fixedDelayString = "${gateway.sync.command-poll-fixed-delay-ms:10000}")
    @Transactional
    public void pollPendingCommands() {
        List<GatewayManagedSensor> sensors = gatewayManagedSensorRepository.findAllByActiveTrueOrderBySensorIdAsc();

        for (GatewayManagedSensor sensor : sensors) {
            try {
                List<CloudSensorApiClient.CloudPendingCommand> commands = cloudSensorApiClient.getPendingCommands(sensor.getSensorId());
                for (CloudSensorApiClient.CloudPendingCommand command : commands) {
                    gatewayCommandBufferRepository.findByCloudCommandId(command.getId())
                            .orElseGet(() -> gatewayCommandBufferRepository.save(
                                    GatewayCommandBuffer.builder()
                                            .cloudCommandId(command.getId())
                                            .sensorId(command.getSensorId())
                                            .commandType(command.getCommandType())
                                            .commandPayload(command.getCommandPayload())
                                            .requestedAt(command.getRequestedAt() == null
                                                    ? LocalDateTime.now(ZoneOffset.UTC)
                                                    : command.getRequestedAt())
                                            .commandStatus(GatewayCommandBufferStatus.PENDING_DISPATCH)
                                            .build()
                            ));
                }
                sensor.setLastCommandPollAt(LocalDateTime.now(ZoneOffset.UTC));
                gatewayManagedSensorRepository.save(sensor);
            } catch (Exception ex) {
                log.warn("Pending command poll failed. sensorId={}, reason={}", sensor.getSensorId(), ex.getMessage());
            }
        }
    }
}
