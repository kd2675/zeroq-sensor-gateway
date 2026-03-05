package com.zeroq.gateway.service.ingest.biz;

import com.zeroq.gateway.common.config.GatewayNodeProperties;
import com.zeroq.gateway.database.pub.entity.BufferSyncStatus;
import com.zeroq.gateway.database.pub.entity.GatewayHeartbeatBuffer;
import com.zeroq.gateway.database.pub.entity.GatewayManagedSensor;
import com.zeroq.gateway.database.pub.entity.GatewayTelemetryBuffer;
import com.zeroq.gateway.database.pub.repository.GatewayHeartbeatBufferRepository;
import com.zeroq.gateway.database.pub.repository.GatewayManagedSensorRepository;
import com.zeroq.gateway.database.pub.repository.GatewayTelemetryBufferRepository;
import com.zeroq.gateway.service.ingest.vo.LocalBatchIngestRequest;
import com.zeroq.gateway.service.ingest.vo.LocalHeartbeatRequest;
import com.zeroq.gateway.service.ingest.vo.LocalIngestResponse;
import com.zeroq.gateway.service.ingest.vo.LocalTelemetryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocalSensorIngestService {
    private final GatewayNodeProperties gatewayNodeProperties;
    private final GatewayManagedSensorRepository gatewayManagedSensorRepository;
    private final GatewayTelemetryBufferRepository gatewayTelemetryBufferRepository;
    private final GatewayHeartbeatBufferRepository gatewayHeartbeatBufferRepository;

    @Transactional
    public LocalIngestResponse ingestTelemetry(LocalTelemetryRequest request) {
        upsertManagedSensor(request.getSensorId(), request.getPlaceId());

        GatewayTelemetryBuffer buffer = GatewayTelemetryBuffer.builder()
                .sensorId(request.getSensorId())
                .sequenceNo(request.getSequenceNo())
                .placeId(request.getPlaceId())
                .gatewayId(gatewayNodeProperties.getGatewayId())
                .measuredAt(request.getMeasuredAt())
                .distanceCm(request.getDistanceCm())
                .confidence(request.getConfidence())
                .temperatureC(request.getTemperatureC())
                .humidityPercent(request.getHumidityPercent())
                .batteryPercent(request.getBatteryPercent())
                .rssi(request.getRssi())
                .rawPayload(request.getRawPayload())
                .syncStatus(BufferSyncStatus.PENDING)
                .retryCount(0)
                .build();

        gatewayTelemetryBufferRepository.save(buffer);
        return LocalIngestResponse.builder()
                .telemetryAccepted(1)
                .heartbeatAccepted(0)
                .build();
    }

    @Transactional
    public LocalIngestResponse ingestHeartbeat(LocalHeartbeatRequest request) {
        upsertManagedSensor(request.getSensorId(), request.getPlaceId());

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

        gatewayHeartbeatBufferRepository.save(buffer);
        return LocalIngestResponse.builder()
                .telemetryAccepted(0)
                .heartbeatAccepted(1)
                .build();
    }

    @Transactional
    public LocalIngestResponse ingestBatch(LocalBatchIngestRequest request) {
        int telemetryAccepted = 0;
        int heartbeatAccepted = 0;

        for (LocalTelemetryRequest telemetry : request.getTelemetries()) {
            ingestTelemetry(telemetry);
            telemetryAccepted++;
        }

        for (LocalHeartbeatRequest heartbeat : request.getHeartbeats()) {
            ingestHeartbeat(heartbeat);
            heartbeatAccepted++;
        }

        return LocalIngestResponse.builder()
                .telemetryAccepted(telemetryAccepted)
                .heartbeatAccepted(heartbeatAccepted)
                .build();
    }

    private void upsertManagedSensor(String sensorId, Long placeId) {
        GatewayManagedSensor managedSensor = gatewayManagedSensorRepository.findBySensorId(sensorId)
                .orElseGet(() -> GatewayManagedSensor.builder()
                        .sensorId(sensorId)
                        .active(true)
                        .build());

        if (placeId != null) {
            managedSensor.setPlaceId(placeId);
        }
        managedSensor.setActive(true);

        gatewayManagedSensorRepository.save(managedSensor);
    }
}
