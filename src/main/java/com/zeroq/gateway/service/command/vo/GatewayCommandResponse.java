package com.zeroq.gateway.service.command.vo;

import com.zeroq.gateway.database.pub.entity.GatewayCommandBuffer;
import com.zeroq.gateway.database.pub.entity.GatewayCommandBufferStatus;
import com.zeroq.gateway.database.pub.entity.SensorCommandType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class GatewayCommandResponse {
    private Long cloudCommandId;
    private String sensorId;
    private SensorCommandType commandType;
    private String commandPayload;
    private LocalDateTime requestedAt;
    private GatewayCommandBufferStatus commandStatus;

    public static GatewayCommandResponse from(GatewayCommandBuffer command) {
        return GatewayCommandResponse.builder()
                .cloudCommandId(command.getCloudCommandId())
                .sensorId(command.getSensorId())
                .commandType(command.getCommandType())
                .commandPayload(command.getCommandPayload())
                .requestedAt(command.getRequestedAt())
                .commandStatus(command.getCommandStatus())
                .build();
    }
}
