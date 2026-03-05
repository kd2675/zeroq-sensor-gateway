package com.zeroq.gateway.service.command.vo;

import com.zeroq.gateway.database.pub.entity.SensorCommandStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LocalCommandAckRequest {
    @NotNull
    private SensorCommandStatus status;

    private String ackPayload;
    private String failureReason;
    private LocalDateTime acknowledgedAt;
}
