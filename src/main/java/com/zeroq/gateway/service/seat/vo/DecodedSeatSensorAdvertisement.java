package com.zeroq.gateway.service.seat.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DecodedSeatSensorAdvertisement {
    private String sensorId;
    private boolean occupied;
    private boolean heartbeat;
    private boolean lowBattery;
    private int leftValue;
    private int rightValue;
    private int batteryPercent;
    private long sequenceNo;
    private LocalDateTime measuredAt;
}
