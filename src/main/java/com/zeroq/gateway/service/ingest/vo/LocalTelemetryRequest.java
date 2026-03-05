package com.zeroq.gateway.service.ingest.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    @NotNull
    @Positive
    private Double distanceCm;

    private Double confidence;
    private Double temperatureC;
    private Double humidityPercent;
    private Double batteryPercent;
    private Integer rssi;
    private String rawPayload;
}
