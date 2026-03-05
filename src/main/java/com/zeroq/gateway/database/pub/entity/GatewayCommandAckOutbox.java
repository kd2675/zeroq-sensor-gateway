package com.zeroq.gateway.database.pub.entity;

import com.zeroq.gateway.common.jpa.CommonDateEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "gateway_command_ack_outbox", indexes = {
        @Index(name = "idx_gateway_command_ack_outbox_status", columnList = "sync_status,retry_count,id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayCommandAckOutbox extends CommonDateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cloud_command_id", nullable = false)
    private Long cloudCommandId;

    @Column(name = "sensor_id", nullable = false, length = 50)
    private String sensorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ack_status", nullable = false, length = 20)
    private SensorCommandStatus ackStatus;

    @Column(name = "ack_payload", columnDefinition = "TEXT")
    private String ackPayload;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "acknowledged_at", nullable = false)
    private LocalDateTime acknowledgedAt;

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
