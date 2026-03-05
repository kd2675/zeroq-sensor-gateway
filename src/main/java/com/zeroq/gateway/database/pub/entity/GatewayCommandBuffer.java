package com.zeroq.gateway.database.pub.entity;

import com.zeroq.gateway.common.jpa.CommonDateEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "gateway_command_buffer", indexes = {
        @Index(name = "idx_gateway_command_buffer_status", columnList = "command_status,requested_at"),
        @Index(name = "idx_gateway_command_buffer_sensor", columnList = "sensor_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayCommandBuffer extends CommonDateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cloud_command_id", nullable = false, unique = true)
    private Long cloudCommandId;

    @Column(name = "sensor_id", nullable = false, length = 50)
    private String sensorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 30)
    private SensorCommandType commandType;

    @Column(name = "command_payload", columnDefinition = "TEXT")
    private String commandPayload;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_status", nullable = false, length = 30)
    @Builder.Default
    private GatewayCommandBufferStatus commandStatus = GatewayCommandBufferStatus.PENDING_DISPATCH;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "last_dispatch_at")
    private LocalDateTime lastDispatchAt;

    @Column(name = "last_cloud_ack_at")
    private LocalDateTime lastCloudAckAt;
}
