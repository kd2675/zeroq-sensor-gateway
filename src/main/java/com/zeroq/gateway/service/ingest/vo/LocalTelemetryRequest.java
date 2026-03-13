package com.zeroq.gateway.service.ingest.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LocalTelemetryRequest {
    @NotBlank
    private String sensorId;

    private Long sequenceNo;
    private Long placeId;

    @NotNull
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
}
