package com.zeroq.gateway.service.seat.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SeatSensorAdvertisementRequest {
    @NotBlank
    private String payloadHex;

    private LocalDateTime observedAt;
    @NotNull
    @Positive
    private Long placeId;
    private Integer rssi;
    private String macAddress;
}
