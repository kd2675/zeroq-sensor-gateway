package com.zeroq.gateway.service.ingest.biz;

import com.zeroq.gateway.common.config.GatewayNodeProperties;
import com.zeroq.gateway.common.exception.GatewayException;
import com.zeroq.gateway.database.pub.entity.BufferSyncStatus;
import com.zeroq.gateway.database.pub.entity.GatewayHeartbeatBuffer;
import com.zeroq.gateway.database.pub.entity.GatewayTelemetryBuffer;
import com.zeroq.gateway.database.pub.repository.GatewayHeartbeatBufferRepository;
import com.zeroq.gateway.database.pub.repository.GatewayTelemetryBufferRepository;
import com.zeroq.gateway.service.ingest.vo.LocalBatchIngestRequest;
import com.zeroq.gateway.service.ingest.vo.LocalHeartbeatRequest;
import com.zeroq.gateway.service.ingest.vo.LocalIngestResponse;
import com.zeroq.gateway.service.ingest.vo.LocalTelemetryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocalSensorIngestService {
    private final GatewayNodeProperties gatewayNodeProperties;
    private final GatewayTelemetryBufferRepository gatewayTelemetryBufferRepository;
    private final GatewayHeartbeatBufferRepository gatewayHeartbeatBufferRepository;
    private final GatewayBufferPersistenceService gatewayBufferPersistenceService;
    private final GatewayManagedSensorPersistenceService gatewayManagedSensorPersistenceService;

    /**
     * 로컬 입력을 검증하고 관리 센서 원장을 갱신한 뒤 PENDING telemetry 버퍼에 저장한다.
     * 애플리케이션 선조회와 DB unique key를 함께 사용해 동시 중복도 무시한다.
     */
    @Transactional
    public LocalIngestResponse ingestTelemetry(LocalTelemetryRequest request) {
        validateTelemetryPayload(request);
        upsertManagedSensor(request.getSensorId(), request.getPlaceId(), request.getMacAddress());

        if (isDuplicateTelemetry(request)) {
            return LocalIngestResponse.builder()
                    .telemetryAccepted(0)
                    .heartbeatAccepted(0)
                    .duplicateIgnored(1)
                    .build();
        }

        GatewayTelemetryBuffer buffer = GatewayTelemetryBuffer.builder()
                .sensorId(request.getSensorId())
                .sequenceNo(request.getSequenceNo())
                .placeId(request.getPlaceId())
                .gatewayId(gatewayNodeProperties.getGatewayId())
                .measuredAt(request.getMeasuredAt())
                .distanceCm(request.getDistanceCm())
                .occupied(request.getOccupied())
                .padLeftValue(request.getPadLeftValue())
                .padRightValue(request.getPadRightValue())
                .confidence(request.getConfidence())
                .temperatureC(request.getTemperatureC())
                .humidityPercent(request.getHumidityPercent())
                .batteryPercent(request.getBatteryPercent())
                .rssi(request.getRssi())
                .rawPayload(request.getRawPayload())
                .syncStatus(BufferSyncStatus.PENDING)
                .retryCount(0)
                .build();

        try {
            gatewayBufferPersistenceService.saveTelemetry(buffer);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Duplicate telemetry dropped by unique key. sensorId={}, sequenceNo={}, measuredAt={}",
                    request.getSensorId(), request.getSequenceNo(), request.getMeasuredAt());
            return LocalIngestResponse.builder()
                    .telemetryAccepted(0)
                    .heartbeatAccepted(0)
                    .duplicateIgnored(1)
                    .build();
        }

        return LocalIngestResponse.builder()
                .telemetryAccepted(1)
                .heartbeatAccepted(0)
                .duplicateIgnored(0)
                .build();
    }

    /**
     * heartbeat를 sensorId·heartbeatAt 기준으로 멱등 버퍼링한다.
     */
    @Transactional
    public LocalIngestResponse ingestHeartbeat(LocalHeartbeatRequest request) {
        upsertManagedSensor(request.getSensorId(), request.getPlaceId(), request.getMacAddress());

        if (gatewayHeartbeatBufferRepository.existsBySensorIdAndHeartbeatAt(
                request.getSensorId(),
                request.getHeartbeatAt()
        )) {
            return LocalIngestResponse.builder()
                    .telemetryAccepted(0)
                    .heartbeatAccepted(0)
                    .duplicateIgnored(1)
                    .build();
        }

        GatewayHeartbeatBuffer buffer = GatewayHeartbeatBuffer.builder()
                .sensorId(request.getSensorId())
                .placeId(request.getPlaceId())
                .gatewayId(gatewayNodeProperties.getGatewayId())
                .heartbeatAt(request.getHeartbeatAt())
                .firmwareVersion(request.getFirmwareVersion())
                .batteryPercent(request.getBatteryPercent())
                .syncStatus(BufferSyncStatus.PENDING)
                .retryCount(0)
                .build();

        try {
            gatewayBufferPersistenceService.saveHeartbeat(buffer);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Duplicate heartbeat dropped by unique key. sensorId={}, heartbeatAt={}",
                    request.getSensorId(), request.getHeartbeatAt());
            return LocalIngestResponse.builder()
                    .telemetryAccepted(0)
                    .heartbeatAccepted(0)
                    .duplicateIgnored(1)
                    .build();
        }

        return LocalIngestResponse.builder()
                .telemetryAccepted(0)
                .heartbeatAccepted(1)
                .duplicateIgnored(0)
                .build();
    }

    /**
     * 단일 수집 메서드를 재사용해 배치의 수락·중복 건수를 합산한다.
     */
    @Transactional
    public LocalIngestResponse ingestBatch(LocalBatchIngestRequest request) {
        int telemetryAccepted = 0;
        int heartbeatAccepted = 0;
        int duplicateIgnored = 0;

        for (LocalTelemetryRequest telemetry : request.getTelemetries()) {
            LocalIngestResponse response = ingestTelemetry(telemetry);
            telemetryAccepted += response.getTelemetryAccepted();
            duplicateIgnored += response.getDuplicateIgnored();
        }

        for (LocalHeartbeatRequest heartbeat : request.getHeartbeats()) {
            LocalIngestResponse response = ingestHeartbeat(heartbeat);
            heartbeatAccepted += response.getHeartbeatAccepted();
            duplicateIgnored += response.getDuplicateIgnored();
        }

        return LocalIngestResponse.builder()
                .telemetryAccepted(telemetryAccepted)
                .heartbeatAccepted(heartbeatAccepted)
                .duplicateIgnored(duplicateIgnored)
                .build();
    }

    private boolean isDuplicateTelemetry(LocalTelemetryRequest request) {
        if (request.getSequenceNo() != null) {
            return gatewayTelemetryBufferRepository.existsBySensorIdAndSequenceNoAndMeasuredAt(
                    request.getSensorId(),
                    request.getSequenceNo(),
                    request.getMeasuredAt()
            );
        }

        return gatewayTelemetryBufferRepository.existsBySensorIdAndMeasuredAt(
                request.getSensorId(),
                request.getMeasuredAt()
        );
    }

    private void validateTelemetryPayload(LocalTelemetryRequest request) {
        if (request.getDistanceCm() == null && request.getOccupied() == null) {
            throw new GatewayException.ValidationException("Either distanceCm or occupied is required");
        }
        if (request.getDistanceCm() != null && request.getDistanceCm() <= 0) {
            throw new GatewayException.ValidationException("distanceCm must be positive");
        }
    }

    private void upsertManagedSensor(String sensorId, Long placeId, String macAddress) {
        if (placeId == null || placeId <= 0) {
            throw new GatewayException.ValidationException("placeId must be a positive value");
        }
        try {
            gatewayManagedSensorPersistenceService.upsert(sensorId, placeId, macAddress);
        } catch (DataIntegrityViolationException ex) {
            gatewayManagedSensorPersistenceService.upsert(sensorId, placeId, macAddress);
        }
    }
}
