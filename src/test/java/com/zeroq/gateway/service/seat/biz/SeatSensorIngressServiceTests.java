package com.zeroq.gateway.service.seat.biz;

import com.zeroq.gateway.service.ingest.biz.LocalSensorIngestService;
import com.zeroq.gateway.service.ingest.vo.LocalHeartbeatRequest;
import com.zeroq.gateway.service.ingest.vo.LocalIngestResponse;
import com.zeroq.gateway.service.ingest.vo.LocalTelemetryRequest;
import com.zeroq.gateway.service.seat.vo.DecodedSeatSensorAdvertisement;
import com.zeroq.gateway.service.seat.vo.SeatSensorAdvertisementRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatSensorIngressServiceTests {

    @Mock
    private SeatSensorAdvertisementDecoder seatSensorAdvertisementDecoder;

    @Mock
    private LocalSensorIngestService localSensorIngestService;

    @InjectMocks
    private SeatSensorIngressService seatSensorIngressService;

    @Test
    void ingestAdvertisement_protocolVersionTwo_usesGatewayObservationTime() {
        LocalDateTime observedAt = LocalDateTime.of(2026, 9, 3, 1, 2, 3);
        SeatSensorAdvertisementRequest request = request(observedAt);
        when(seatSensorAdvertisementDecoder.decode(request.getPayloadHex())).thenReturn(decodedVersionTwo(false));
        when(localSensorIngestService.ingestTelemetry(org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(1, 0));

        seatSensorIngressService.ingestAdvertisement(request);

        ArgumentCaptor<LocalTelemetryRequest> captor = ArgumentCaptor.forClass(LocalTelemetryRequest.class);
        verify(localSensorIngestService).ingestTelemetry(captor.capture());
        assertThat(captor.getValue()).satisfies(value -> {
            assertThat(value.getMeasuredAt()).isEqualTo(observedAt);
            assertThat(value.getSensorId()).isEqualTo("SEAT-014");
            assertThat(value.getSequenceNo()).isEqualTo(1200L);
            assertThat(value.getOccupied()).isTrue();
            assertThat(value.getConfidence()).isEqualTo(1.0);
            assertThat(value.getMacAddress()).isEqualTo("AA:BB:CC:DD:EE:FF");
        });
    }

    @Test
    void ingestAdvertisement_protocolVersionOne_usesDeviceEpochTime() {
        LocalDateTime deviceTime = LocalDateTime.of(2026, 3, 11, 12, 10);
        SeatSensorAdvertisementRequest request = request(LocalDateTime.of(2026, 9, 3, 1, 2, 3));
        when(seatSensorAdvertisementDecoder.decode(request.getPayloadHex())).thenReturn(
                DecodedSeatSensorAdvertisement.builder()
                        .protocolVersion(1)
                        .sensorId("SEAT-014")
                        .occupied(true)
                        .batteryPercent(80)
                        .sequenceNo(1200L)
                        .measuredAt(deviceTime)
                        .build()
        );
        when(localSensorIngestService.ingestTelemetry(org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(1, 0));

        seatSensorIngressService.ingestAdvertisement(request);

        ArgumentCaptor<LocalTelemetryRequest> captor = ArgumentCaptor.forClass(LocalTelemetryRequest.class);
        verify(localSensorIngestService).ingestTelemetry(captor.capture());
        assertThat(captor.getValue().getMeasuredAt()).isEqualTo(deviceTime);
    }

    @Test
    void ingestAdvertisement_heartbeatFlag_enqueuesHeartbeatAtSameObservationTime() {
        LocalDateTime observedAt = LocalDateTime.of(2026, 9, 3, 1, 2, 3);
        SeatSensorAdvertisementRequest request = request(observedAt);
        when(seatSensorAdvertisementDecoder.decode(request.getPayloadHex())).thenReturn(decodedVersionTwo(true));
        when(localSensorIngestService.ingestTelemetry(org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(1, 0));
        when(localSensorIngestService.ingestHeartbeat(org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(0, 1));

        seatSensorIngressService.ingestAdvertisement(request);

        ArgumentCaptor<LocalHeartbeatRequest> captor = ArgumentCaptor.forClass(LocalHeartbeatRequest.class);
        verify(localSensorIngestService).ingestHeartbeat(captor.capture());
        assertThat(captor.getValue()).satisfies(value -> {
            assertThat(value.getHeartbeatAt()).isEqualTo(observedAt);
            assertThat(value.getSensorId()).isEqualTo("SEAT-014");
            assertThat(value.getBatteryPercent()).isEqualTo(80.0);
            assertThat(value.getMacAddress()).isEqualTo("AA:BB:CC:DD:EE:FF");
        });
    }

    @Test
    void ingestAdvertisement_distanceMode_mapsMillimetersToCentimetersWithoutPadValues() {
        LocalDateTime observedAt = LocalDateTime.of(2026, 9, 3, 1, 2, 3);
        SeatSensorAdvertisementRequest request = request(observedAt);
        when(seatSensorAdvertisementDecoder.decode(request.getPayloadHex())).thenReturn(
                DecodedSeatSensorAdvertisement.builder()
                        .protocolVersion(2)
                        .sensorId("SPOT-014")
                        .occupied(true)
                        .distanceMode(true)
                        .distanceMm(742)
                        .batteryPercent(80)
                        .sequenceNo(1200L)
                        .deviceUptimeSeconds(3600L)
                        .build()
        );
        when(localSensorIngestService.ingestTelemetry(org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(1, 0));

        seatSensorIngressService.ingestAdvertisement(request);

        ArgumentCaptor<LocalTelemetryRequest> captor = ArgumentCaptor.forClass(LocalTelemetryRequest.class);
        verify(localSensorIngestService).ingestTelemetry(captor.capture());
        assertThat(captor.getValue()).satisfies(value -> {
            assertThat(value.getDistanceCm()).isEqualTo(74.2);
            assertThat(value.getPadLeftValue()).isNull();
            assertThat(value.getPadRightValue()).isNull();
            assertThat(value.getOccupied()).isTrue();
        });
    }

    @Test
    void ingestAdvertisement_sensorFault_setsZeroConfidence() {
        LocalDateTime observedAt = LocalDateTime.of(2026, 9, 3, 1, 2, 3);
        SeatSensorAdvertisementRequest request = request(observedAt);
        when(seatSensorAdvertisementDecoder.decode(request.getPayloadHex())).thenReturn(
                DecodedSeatSensorAdvertisement.builder()
                        .protocolVersion(2)
                        .sensorId("SPOT-014")
                        .occupied(false)
                        .distanceMode(true)
                        .sensorFault(true)
                        .distanceMm(4000)
                        .batteryPercent(80)
                        .sequenceNo(1200L)
                        .deviceUptimeSeconds(3600L)
                        .build()
        );
        when(localSensorIngestService.ingestTelemetry(org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(1, 0));

        seatSensorIngressService.ingestAdvertisement(request);

        ArgumentCaptor<LocalTelemetryRequest> captor = ArgumentCaptor.forClass(LocalTelemetryRequest.class);
        verify(localSensorIngestService).ingestTelemetry(captor.capture());
        assertThat(captor.getValue().getConfidence()).isEqualTo(0.0);
    }

    private SeatSensorAdvertisementRequest request(LocalDateTime observedAt) {
        SeatSensorAdvertisementRequest request = new SeatSensorAdvertisementRequest();
        request.setPayloadHex("payload");
        request.setObservedAt(observedAt);
        request.setRssi(-58);
        request.setMacAddress("AA:BB:CC:DD:EE:FF");
        return request;
    }

    private DecodedSeatSensorAdvertisement decodedVersionTwo(boolean heartbeat) {
        return DecodedSeatSensorAdvertisement.builder()
                .protocolVersion(2)
                .sensorId("SEAT-014")
                .occupied(true)
                .heartbeat(heartbeat)
                .leftValue(620)
                .rightValue(360)
                .batteryPercent(80)
                .sequenceNo(1200L)
                .deviceUptimeSeconds(3600L)
                .build();
    }

    private LocalIngestResponse response(int telemetryAccepted, int heartbeatAccepted) {
        return LocalIngestResponse.builder()
                .telemetryAccepted(telemetryAccepted)
                .heartbeatAccepted(heartbeatAccepted)
                .duplicateIgnored(0)
                .build();
    }
}
