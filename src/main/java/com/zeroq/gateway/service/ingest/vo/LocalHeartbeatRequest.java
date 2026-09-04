package com.zeroq.gateway.service.ingest.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LocalHeartbeatRequest {
    @NotBlank
    private String sensorId;

    @NotNull
    @Positive
    private Long placeId;
    private String macAddress;

    @NotNull
    private LocalDateTime heartbeatAt;

    private String firmwareVersion;
    private Double batteryPercent;
}
