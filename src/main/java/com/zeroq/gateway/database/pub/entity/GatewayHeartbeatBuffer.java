package com.zeroq.gateway.database.pub.entity;

import com.zeroq.gateway.common.jpa.CommonDateEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "gateway_heartbeat_buffer", indexes = {
        @Index(name = "idx_gateway_heartbeat_buffer_status", columnList = "sync_status,retry_count,id"),
        @Index(name = "idx_gateway_heartbeat_buffer_sensor", columnList = "sensor_id,heartbeat_at")
}, uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_gateway_heartbeat_buffer_sensor_heartbeat",
                columnNames = {"sensor_id", "heartbeat_at"}
        )
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayHeartbeatBuffer extends CommonDateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sensor_id", nullable = false, length = 50)
    private String sensorId;

    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "gateway_id", nullable = false, length = 50)
    private String gatewayId;

    @Column(name = "heartbeat_at", nullable = false)
    private LocalDateTime heartbeatAt;

    @Column(name = "firmware_version", length = 20)
    private String firmwareVersion;

    @Column(name = "battery_percent")
    private Double batteryPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    @Builder.Default
    private BufferSyncStatus syncStatus = BufferSyncStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
