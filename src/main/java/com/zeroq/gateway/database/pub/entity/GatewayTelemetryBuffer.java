package com.zeroq.gateway.database.pub.entity;

import com.zeroq.gateway.common.jpa.CommonDateEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "gateway_telemetry_buffer", indexes = {
        @Index(name = "idx_gateway_telemetry_buffer_status", columnList = "sync_status,retry_count,id"),
        @Index(name = "idx_gateway_telemetry_buffer_sensor", columnList = "sensor_id,measured_at")
}, uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_gateway_telemetry_buffer_sensor_sequence_measured",
                columnNames = {"sensor_id", "sequence_no", "measured_at"}
        ),
        @UniqueConstraint(
                name = "uk_gateway_telemetry_buffer_sensor_measured",
                columnNames = {"sensor_id", "measured_at"}
        )
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayTelemetryBuffer extends CommonDateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sensor_id", nullable = false, length = 50)
    private String sensorId;

    @Column(name = "sequence_no")
    private Long sequenceNo;

    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "gateway_id", nullable = false, length = 50)
    private String gatewayId;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @Column(name = "distance_cm")
    private Double distanceCm;

    @Column(name = "occupied")
    private Boolean occupied;

    @Column(name = "pad_left_value")
    private Integer padLeftValue;

    @Column(name = "pad_right_value")
    private Integer padRightValue;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "temperature_c")
    private Double temperatureC;

    @Column(name = "humidity_percent")
    private Double humidityPercent;

    @Column(name = "battery_percent")
    private Double batteryPercent;

    @Column(name = "rssi")
    private Integer rssi;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

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
