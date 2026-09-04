package com.zeroq.gateway.service.seat.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DecodedSeatSensorAdvertisement {
    private int protocolVersion;
    private String sensorId;
    private boolean occupied;
    private boolean heartbeat;
    private boolean lowBattery;
    private boolean distanceMode;
    private boolean sensorFault;
    private Integer distanceMm;
    private Integer leftValue;
    private Integer rightValue;
    private int batteryPercent;
    private long sequenceNo;
    private LocalDateTime measuredAt;
    private Long deviceUptimeSeconds;
}
