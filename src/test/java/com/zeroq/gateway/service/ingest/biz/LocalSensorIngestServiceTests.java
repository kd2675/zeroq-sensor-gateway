package com.zeroq.gateway.service.ingest.biz;

import com.zeroq.gateway.common.exception.GatewayException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void shouldStoreSeatOccupancyTelemetryWithoutDistance() {
        LocalTelemetryRequest request = new LocalTelemetryRequest();
        request.setSensorId("SEAT-" + System.nanoTime());
        request.setPlaceId(201L);
        request.setMeasuredAt(LocalDateTime.now());
        request.setOccupied(true);
        request.setPadLeftValue(640);
        request.setPadRightValue(612);

        LocalIngestResponse response = localSensorIngestService.ingestTelemetry(request);

        assertThat(response.getTelemetryAccepted()).isEqualTo(1);
        assertThat(gatewayTelemetryBufferRepository.findAll())
                .anySatisfy(buffer -> {
                    assertThat(buffer.getSensorId()).isEqualTo(request.getSensorId());
                    assertThat(buffer.getOccupied()).isTrue();
                    assertThat(buffer.getPadLeftValue()).isEqualTo(640);
                    assertThat(buffer.getPadRightValue()).isEqualTo(612);
                });
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

    @Test
    void ingestTelemetry_sameSensorWithDifferentBleAddress_rejectsIdentityChange() {
        String sensorId = "SPOT-MAC-" + System.nanoTime();
        LocalTelemetryRequest first = telemetryRequest(sensorId, "AA:BB:CC:00:00:01");
        LocalTelemetryRequest changed = telemetryRequest(sensorId, "AA:BB:CC:00:00:02");
        localSensorIngestService.ingestTelemetry(first);

        assertThatThrownBy(() -> localSensorIngestService.ingestTelemetry(changed))
                .isInstanceOf(GatewayException.ValidationException.class)
                .hasMessageContaining("BLE address changed");
    }

    @Test
    void ingestTelemetry_sameBleAddressForDifferentSensor_rejectsDuplicateIdentity() {
        String suffix = String.valueOf(System.nanoTime());
        String macAddress = "AA:BB:CC:00:00:03";
        localSensorIngestService.ingestTelemetry(telemetryRequest("SPOT-A-" + suffix, macAddress));

        assertThatThrownBy(() -> localSensorIngestService.ingestTelemetry(
                telemetryRequest("SPOT-B-" + suffix, macAddress)
        ))
                .isInstanceOf(GatewayException.ValidationException.class)
                .hasMessageContaining("already assigned");
    }

    private LocalTelemetryRequest telemetryRequest(String sensorId, String macAddress) {
        LocalTelemetryRequest request = new LocalTelemetryRequest();
        request.setSensorId(sensorId);
        request.setMacAddress(macAddress);
        request.setMeasuredAt(LocalDateTime.now());
        request.setDistanceCm(74.2);
        return request;
    }
}
