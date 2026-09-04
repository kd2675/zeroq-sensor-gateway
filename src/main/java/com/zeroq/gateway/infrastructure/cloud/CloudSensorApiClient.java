package com.zeroq.gateway.infrastructure.cloud;

import com.zeroq.gateway.common.config.GatewayNodeProperties;
import com.zeroq.gateway.common.exception.GatewayException;
import com.zeroq.gateway.database.pub.entity.GatewayHeartbeatBuffer;
import com.zeroq.gateway.database.pub.entity.GatewayTelemetryBuffer;
import com.zeroq.gateway.database.pub.entity.SensorCommandStatus;
import com.zeroq.gateway.database.pub.entity.SensorCommandType;
import com.zeroq.gateway.service.command.vo.LocalCommandAckRequest;
import com.zeroq.gateway.service.monitoring.biz.GatewayCloudRuntimeMetricsService;
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
    private final GatewayCloudRuntimeMetricsService gatewayCloudRuntimeMetricsService;

    /**
     * 서명된 내부 경로로 telemetry·heartbeat 배치를 전송하고 항목별 처리 건수를 반환한다.
     */
    public CloudBatchResponse postBatchIngest(List<GatewayTelemetryBuffer> telemetries, List<GatewayHeartbeatBuffer> heartbeats) {
        CloudBatchRequest request = new CloudBatchRequest();
        request.setGatewayId(gatewayNodeProperties.getGatewayId());
        request.setTelemetries(telemetries.stream().map(CloudTelemetry::from).toList());
        request.setHeartbeats(heartbeats.stream().map(CloudHeartbeat::from).toList());

        long startedAt = System.nanoTime();
        try {
            GatewayResponseData<CloudBatchResponse> response = cloudRestClient.post()
                    .uri("/internal/zeroq/gateway/sensor/ingest/batch")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null) {
                throw new GatewayException.RemoteCallException("Invalid cloud batch ingest response");
            }
            gatewayCloudRuntimeMetricsService.recordSuccess(elapsedMillis(startedAt));
            return response.getData();
        } catch (RestClientException ex) {
            gatewayCloudRuntimeMetricsService.recordFailure(elapsedMillis(startedAt));
            throw new GatewayException.RemoteCallException("Batch ingest API failed: " + ex.getMessage());
        }
    }

    /** 배치 부분 실패 복구에 사용하는 단일 telemetry 전송 경로다. */
    public void postTelemetry(GatewayTelemetryBuffer telemetry) {
        long startedAt = System.nanoTime();
        try {
            cloudRestClient.post()
                    .uri("/internal/zeroq/gateway/sensor/ingest/telemetry")
                    .body(CloudTelemetry.from(telemetry))
                    .retrieve()
                    .toBodilessEntity();
            gatewayCloudRuntimeMetricsService.recordSuccess(elapsedMillis(startedAt));
        } catch (RestClientException ex) {
            gatewayCloudRuntimeMetricsService.recordFailure(elapsedMillis(startedAt));
            throw new GatewayException.RemoteCallException("Telemetry ingest API failed: " + ex.getMessage());
        }
    }

    /** 배치 부분 실패 복구에 사용하는 단일 heartbeat 전송 경로다. */
    public void postHeartbeat(GatewayHeartbeatBuffer heartbeat) {
        long startedAt = System.nanoTime();
        try {
            cloudRestClient.post()
                    .uri("/internal/zeroq/gateway/sensor/ingest/heartbeat")
                    .body(CloudHeartbeat.from(heartbeat))
                    .retrieve()
                    .toBodilessEntity();
            gatewayCloudRuntimeMetricsService.recordSuccess(elapsedMillis(startedAt));
        } catch (RestClientException ex) {
            gatewayCloudRuntimeMetricsService.recordFailure(elapsedMillis(startedAt));
            throw new GatewayException.RemoteCallException("Heartbeat ingest API failed: " + ex.getMessage());
        }
    }

    /**
     * 관리 센서의 미완료 명령을 조회하면서 클라우드 PENDING 명령을 SENT로 표시한다.
     */
    public List<CloudPendingCommand> getPendingCommands(String sensorId) {
        long startedAt = System.nanoTime();
        try {
            GatewayResponseData<List<CloudPendingCommand>> response = cloudRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/zeroq/gateway/sensor/commands/sensor/{sensorId}/pending")
                            .queryParam("markAsSent", true)
                            .build(sensorId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null) {
                gatewayCloudRuntimeMetricsService.recordSuccess(elapsedMillis(startedAt));
                return List.of();
            }
            gatewayCloudRuntimeMetricsService.recordSuccess(elapsedMillis(startedAt));
            return response.getData();
        } catch (RestClientException ex) {
            gatewayCloudRuntimeMetricsService.recordFailure(elapsedMillis(startedAt));
            throw new GatewayException.RemoteCallException("Pending command API failed: " + ex.getMessage());
        }
    }

    /** 로컬 ACK outbox 항목을 클라우드 command 상태 전이 API에 전달한다. */
    public void ackCommand(Long commandId, LocalCommandAckRequest request) {
        CloudAckRequest ackRequest = CloudAckRequest.builder()
                .status(request.getStatus())
                .ackPayload(request.getAckPayload())
                .failureReason(request.getFailureReason())
                .acknowledgedAt(request.getAcknowledgedAt())
                .build();

        long startedAt = System.nanoTime();
        try {
            cloudRestClient.patch()
                    .uri("/internal/zeroq/gateway/sensor/commands/{commandId}/ack", commandId)
                    .body(ackRequest)
                    .retrieve()
                    .toBodilessEntity();
            gatewayCloudRuntimeMetricsService.recordSuccess(elapsedMillis(startedAt));
        } catch (RestClientException ex) {
            gatewayCloudRuntimeMetricsService.recordFailure(elapsedMillis(startedAt));
            throw new GatewayException.RemoteCallException("Command ack API failed: " + ex.getMessage());
        }
    }

    /** 게이트웨이 heartbeat와 큐·네트워크 지표를 클라우드 최신 상태 원장에 전달한다. */
    public void postGatewayStatus(CloudGatewayStatus request) {
        long startedAt = System.nanoTime();
        try {
            cloudRestClient.post()
                    .uri("/internal/zeroq/gateway/sensor/ingest/gateway-heartbeat")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            gatewayCloudRuntimeMetricsService.recordSuccess(elapsedMillis(startedAt));
        } catch (RestClientException ex) {
            gatewayCloudRuntimeMetricsService.recordFailure(elapsedMillis(startedAt));
            throw new GatewayException.RemoteCallException("Gateway status ingest API failed: " + ex.getMessage());
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
        private Boolean occupied;
        private Integer padLeftValue;
        private Integer padRightValue;
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
            dto.setOccupied(telemetry.getOccupied());
            dto.setPadLeftValue(telemetry.getPadLeftValue());
            dto.setPadRightValue(telemetry.getPadRightValue());
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
        private String gatewayId;
        private LocalDateTime heartbeatAt;
        private String firmwareVersion;
        private Double batteryPercent;

        public static CloudHeartbeat from(GatewayHeartbeatBuffer heartbeat) {
            CloudHeartbeat dto = new CloudHeartbeat();
            dto.setSensorId(heartbeat.getSensorId());
            dto.setGatewayId(heartbeat.getGatewayId());
            dto.setHeartbeatAt(heartbeat.getHeartbeatAt());
            dto.setFirmwareVersion(heartbeat.getFirmwareVersion());
            dto.setBatteryPercent(heartbeat.getBatteryPercent());
            return dto;
        }
    }

    @Getter
    @Setter
    public static class CloudGatewayStatus {
        private String gatewayId;
        private String status;
        private LocalDateTime heartbeatAt;
        private String firmwareVersion;
        private String ipAddress;
        private Integer currentSensorLoad;
        private Integer latencyMs;
        private Double packetLossPercent;
        private Long telemetryPending;
        private Long telemetryFailed;
        private Long heartbeatPending;
        private Long heartbeatFailed;
        private Long commandDispatchPending;
        private Long commandAckPending;
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

    private int elapsedMillis(long startedAt) {
        return (int) ((System.nanoTime() - startedAt) / 1_000_000L);
    }
}
