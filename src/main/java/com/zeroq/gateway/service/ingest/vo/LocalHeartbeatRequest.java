package com.zeroq.gateway.service.ingest.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LocalHeartbeatRequest {
    @NotBlank
    private String sensorId;

    private Long placeId;

    @NotNull
    private LocalDateTime heartbeatAt;

    private String firmwareVersion;
    private Double batteryPercent;
}
