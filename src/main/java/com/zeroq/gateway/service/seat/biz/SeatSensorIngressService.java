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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatSensorIngressService {
    private final SeatSensorAdvertisementDecoder seatSensorAdvertisementDecoder;
    private final LocalSensorIngestService localSensorIngestService;

    @Transactional
    public LocalIngestResponse ingestAdvertisement(SeatSensorAdvertisementRequest request) {
        DecodedSeatSensorAdvertisement decoded = seatSensorAdvertisementDecoder.decode(request.getPayloadHex());

        LocalTelemetryRequest telemetryRequest = new LocalTelemetryRequest();
        telemetryRequest.setSensorId(decoded.getSensorId());
        telemetryRequest.setSequenceNo(decoded.getSequenceNo());
        telemetryRequest.setPlaceId(request.getPlaceId());
        telemetryRequest.setMeasuredAt(decoded.getMeasuredAt());
        telemetryRequest.setOccupied(decoded.isOccupied());
        telemetryRequest.setPadLeftValue(decoded.getLeftValue());
        telemetryRequest.setPadRightValue(decoded.getRightValue());
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
        heartbeatRequest.setHeartbeatAt(decoded.getMeasuredAt());
        heartbeatRequest.setBatteryPercent((double) decoded.getBatteryPercent());

        LocalIngestResponse heartbeatResponse = localSensorIngestService.ingestHeartbeat(heartbeatRequest);
        return LocalIngestResponse.builder()
                .telemetryAccepted(telemetryResponse.getTelemetryAccepted())
                .heartbeatAccepted(heartbeatResponse.getHeartbeatAccepted())
                .duplicateIgnored(telemetryResponse.getDuplicateIgnored() + heartbeatResponse.getDuplicateIgnored())
                .build();
    }
}
