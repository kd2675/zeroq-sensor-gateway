package com.zeroq.gateway.infrastructure.cloud;

import com.zeroq.gateway.common.config.GatewayNodeProperties;
import com.zeroq.gateway.common.exception.GatewayException;
import com.zeroq.gateway.database.pub.entity.GatewayHeartbeatBuffer;
import com.zeroq.gateway.database.pub.entity.GatewayTelemetryBuffer;
import com.zeroq.gateway.database.pub.entity.SensorCommandStatus;
import com.zeroq.gateway.database.pub.entity.SensorCommandType;
import com.zeroq.gateway.service.command.vo.LocalCommandAckRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CloudSensorApiClient {
    private final RestClient cloudRestClient;
    private final GatewayNodeProperties gatewayNodeProperties;

    public CloudBatchResponse postBatchIngest(List<GatewayTelemetryBuffer> telemetries, List<GatewayHeartbeatBuffer> heartbeats) {
        CloudBatchRequest request = new CloudBatchRequest();
        request.setGatewayId(gatewayNodeProperties.getGatewayId());
        request.setTelemetries(telemetries.stream().map(CloudTelemetry::from).toList());
        request.setHeartbeats(heartbeats.stream().map(CloudHeartbeat::from).toList());

        try {
            GatewayResponseData<CloudBatchResponse> response = cloudRestClient.post()
                    .uri("/api/zeroq/v1/sensor/ingest/batch")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null) {
                throw new GatewayException.RemoteCallException("Invalid cloud batch ingest response");
            }
            return response.getData();
        } catch (RestClientException ex) {
            throw new GatewayException.RemoteCallException("Batch ingest API failed: " + ex.getMessage());
        }
    }

    public void postTelemetry(GatewayTelemetryBuffer telemetry) {
        try {
            cloudRestClient.post()
                    .uri("/api/zeroq/v1/sensor/ingest/telemetry")
                    .body(CloudTelemetry.from(telemetry))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new GatewayException.RemoteCallException("Telemetry ingest API failed: " + ex.getMessage());
        }
    }

    public void postHeartbeat(GatewayHeartbeatBuffer heartbeat) {
        try {
            cloudRestClient.post()
                    .uri("/api/zeroq/v1/sensor/ingest/heartbeat")
                    .body(CloudHeartbeat.from(heartbeat))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new GatewayException.RemoteCallException("Heartbeat ingest API failed: " + ex.getMessage());
        }
    }

    public List<CloudPendingCommand> getPendingCommands(String sensorId) {
        try {
            GatewayResponseData<List<CloudPendingCommand>> response = cloudRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/zeroq/v1/sensor/commands/sensor/{sensorId}/pending")
                            .queryParam("markAsSent", true)
                            .build(sensorId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null) {
                return List.of();
            }
            return response.getData();
        } catch (RestClientException ex) {
            throw new GatewayException.RemoteCallException("Pending command API failed: " + ex.getMessage());
        }
    }

    public void ackCommand(Long commandId, LocalCommandAckRequest request) {
        CloudAckRequest ackRequest = CloudAckRequest.builder()
                .status(request.getStatus())
                .ackPayload(request.getAckPayload())
                .failureReason(request.getFailureReason())
                .acknowledgedAt(request.getAcknowledgedAt())
                .build();

        try {
            cloudRestClient.patch()
                    .uri("/api/zeroq/v1/sensor/commands/{commandId}/ack", commandId)
                    .body(ackRequest)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new GatewayException.RemoteCallException("Command ack API failed: " + ex.getMessage());
        }
    }

    @Getter
    @Setter
    public static class CloudBatchRequest {
        private String gatewayId;
        private List<CloudTelemetry> telemetries;
        private List<CloudHeartbeat> heartbeats;
    }

    @Getter
    @Setter
    public static class CloudBatchResponse {
        private int telemetrySuccessCount;
        private int telemetryFailureCount;
        private int heartbeatSuccessCount;
        private int heartbeatFailureCount;
    }

    @Getter
    @Setter
    public static class CloudTelemetry {
        private String sensorId;
        private Long sequenceNo;
        private Long placeId;
        private String gatewayId;
        private LocalDateTime measuredAt;
        private Double distanceCm;
        private Double confidence;
        private Double temperatureC;
        private Double humidityPercent;
        private Double batteryPercent;
        private Integer rssi;
        private String rawPayload;

        public static CloudTelemetry from(GatewayTelemetryBuffer telemetry) {
            CloudTelemetry dto = new CloudTelemetry();
            dto.setSensorId(telemetry.getSensorId());
            dto.setSequenceNo(telemetry.getSequenceNo());
            dto.setPlaceId(telemetry.getPlaceId());
            dto.setGatewayId(telemetry.getGatewayId());
            dto.setMeasuredAt(telemetry.getMeasuredAt());
            dto.setDistanceCm(telemetry.getDistanceCm());
            dto.setConfidence(telemetry.getConfidence());
            dto.setTemperatureC(telemetry.getTemperatureC());
            dto.setHumidityPercent(telemetry.getHumidityPercent());
            dto.setBatteryPercent(telemetry.getBatteryPercent());
            dto.setRssi(telemetry.getRssi());
            dto.setRawPayload(telemetry.getRawPayload());
            return dto;
        }
    }

    @Getter
    @Setter
    public static class CloudHeartbeat {
        private String sensorId;
        private Long placeId;
        private String gatewayId;
        private LocalDateTime heartbeatAt;
        private String firmwareVersion;
        private Double batteryPercent;

        public static CloudHeartbeat from(GatewayHeartbeatBuffer heartbeat) {
            CloudHeartbeat dto = new CloudHeartbeat();
            dto.setSensorId(heartbeat.getSensorId());
            dto.setPlaceId(heartbeat.getPlaceId());
            dto.setGatewayId(heartbeat.getGatewayId());
            dto.setHeartbeatAt(heartbeat.getHeartbeatAt());
            dto.setFirmwareVersion(heartbeat.getFirmwareVersion());
            dto.setBatteryPercent(heartbeat.getBatteryPercent());
            return dto;
        }
    }

    @Getter
    @Setter
    public static class CloudPendingCommand {
        private Long id;
        private String sensorId;
        private SensorCommandType commandType;
        private SensorCommandStatus status;
        private String commandPayload;
        private String requestedBy;
        private LocalDateTime requestedAt;
    }

    @Getter
    @Builder
    public static class CloudAckRequest {
        private SensorCommandStatus status;
        private String ackPayload;
        private String failureReason;
        private LocalDateTime acknowledgedAt;
    }
}
