package com.zeroq.gateway.service.seat.biz;

import com.zeroq.gateway.service.ingest.biz.LocalSensorIngestService;
import com.zeroq.gateway.service.ingest.vo.LocalHeartbeatRequest;
import com.zeroq.gateway.service.ingest.vo.LocalIngestResponse;
import com.zeroq.gateway.service.ingest.vo.LocalTelemetryRequest;
import com.zeroq.gateway.service.seat.vo.DecodedSeatSensorAdvertisement;
import com.zeroq.gateway.service.seat.vo.SeatSensorAdvertisementRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatSensorIngressService {
    private final SeatSensorAdvertisementDecoder seatSensorAdvertisementDecoder;
    private final LocalSensorIngestService localSensorIngestService;

    @Transactional
    public LocalIngestResponse ingestAdvertisement(SeatSensorAdvertisementRequest request) {
        DecodedSeatSensorAdvertisement decoded = seatSensorAdvertisementDecoder.decode(request.getPayloadHex());
        LocalDateTime measuredAt = resolveMeasuredAt(request, decoded);

        LocalTelemetryRequest telemetryRequest = new LocalTelemetryRequest();
        telemetryRequest.setSensorId(decoded.getSensorId());
        telemetryRequest.setSequenceNo(decoded.getSequenceNo());
        telemetryRequest.setPlaceId(request.getPlaceId());
        telemetryRequest.setMacAddress(request.getMacAddress());
        telemetryRequest.setMeasuredAt(measuredAt);
        telemetryRequest.setOccupied(decoded.isOccupied());
        telemetryRequest.setConfidence(decoded.isSensorFault() ? 0.0 : 1.0);
        if (decoded.isDistanceMode()) {
            telemetryRequest.setDistanceCm(decoded.getDistanceMm() / 10.0);
        } else {
            telemetryRequest.setPadLeftValue(decoded.getLeftValue());
            telemetryRequest.setPadRightValue(decoded.getRightValue());
        }
        telemetryRequest.setBatteryPercent((double) decoded.getBatteryPercent());
        telemetryRequest.setRssi(request.getRssi());
        telemetryRequest.setRawPayload(request.getPayloadHex());

        LocalIngestResponse telemetryResponse = localSensorIngestService.ingestTelemetry(telemetryRequest);

        if (!decoded.isHeartbeat()) {
            return telemetryResponse;
        }

        LocalHeartbeatRequest heartbeatRequest = new LocalHeartbeatRequest();
        heartbeatRequest.setSensorId(decoded.getSensorId());
        heartbeatRequest.setPlaceId(request.getPlaceId());
        heartbeatRequest.setMacAddress(request.getMacAddress());
        heartbeatRequest.setHeartbeatAt(measuredAt);
        heartbeatRequest.setBatteryPercent((double) decoded.getBatteryPercent());

        LocalIngestResponse heartbeatResponse = localSensorIngestService.ingestHeartbeat(heartbeatRequest);
        return LocalIngestResponse.builder()
                .telemetryAccepted(telemetryResponse.getTelemetryAccepted())
                .heartbeatAccepted(heartbeatResponse.getHeartbeatAccepted())
                .duplicateIgnored(telemetryResponse.getDuplicateIgnored() + heartbeatResponse.getDuplicateIgnored())
                .build();
    }

    private LocalDateTime resolveMeasuredAt(
            SeatSensorAdvertisementRequest request,
            DecodedSeatSensorAdvertisement decoded
    ) {
        if (decoded.getProtocolVersion() == 1 && decoded.getMeasuredAt() != null) {
            return decoded.getMeasuredAt();
        }
        return request.getObservedAt() == null
                ? LocalDateTime.now(ZoneOffset.UTC)
                : request.getObservedAt();
    }
}
