package com.zeroq.gateway.service.ingest.biz;

import com.zeroq.gateway.database.pub.entity.BufferSyncStatus;
import com.zeroq.gateway.database.pub.repository.GatewayHeartbeatBufferRepository;
import com.zeroq.gateway.database.pub.repository.GatewayManagedSensorRepository;
import com.zeroq.gateway.database.pub.repository.GatewayTelemetryBufferRepository;
import com.zeroq.gateway.service.ingest.vo.LocalHeartbeatRequest;
import com.zeroq.gateway.service.ingest.vo.LocalIngestResponse;
import com.zeroq.gateway.service.ingest.vo.LocalTelemetryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LocalSensorIngestServiceTests {

    @Autowired
    private LocalSensorIngestService localSensorIngestService;

    @Autowired
    private GatewayManagedSensorRepository gatewayManagedSensorRepository;

    @Autowired
    private GatewayTelemetryBufferRepository gatewayTelemetryBufferRepository;

    @Autowired
    private GatewayHeartbeatBufferRepository gatewayHeartbeatBufferRepository;

    @Test
    void shouldStoreTelemetryAndManagedSensor() {
        LocalTelemetryRequest request = new LocalTelemetryRequest();
        request.setSensorId("SN-T-" + System.nanoTime());
        request.setPlaceId(101L);
        request.setMeasuredAt(LocalDateTime.now());
        request.setDistanceCm(100.0);

        LocalIngestResponse response = localSensorIngestService.ingestTelemetry(request);

        assertThat(response.getTelemetryAccepted()).isEqualTo(1);
        assertThat(gatewayManagedSensorRepository.findBySensorId(request.getSensorId())).isPresent();
        assertThat(gatewayTelemetryBufferRepository.countBySyncStatus(BufferSyncStatus.PENDING)).isGreaterThan(0);
    }

    @Test
    void shouldStoreHeartbeatBuffer() {
        LocalHeartbeatRequest request = new LocalHeartbeatRequest();
        request.setSensorId("SN-H-" + System.nanoTime());
        request.setPlaceId(102L);
        request.setHeartbeatAt(LocalDateTime.now());
        request.setBatteryPercent(88.0);

        LocalIngestResponse response = localSensorIngestService.ingestHeartbeat(request);

        assertThat(response.getHeartbeatAccepted()).isEqualTo(1);
        assertThat(gatewayManagedSensorRepository.findBySensorId(request.getSensorId())).isPresent();
        assertThat(gatewayHeartbeatBufferRepository.countBySyncStatus(BufferSyncStatus.PENDING)).isGreaterThan(0);
    }
}
