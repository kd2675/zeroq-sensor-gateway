package com.zeroq.gateway.service.seat.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SeatSensorAdvertisementRequest {
    @NotBlank
    private String payloadHex;

    private Long placeId;
    private Integer rssi;
}
