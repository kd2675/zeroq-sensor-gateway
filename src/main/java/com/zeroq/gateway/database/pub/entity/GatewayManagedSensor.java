package com.zeroq.gateway.database.pub.entity;

import com.zeroq.gateway.common.jpa.CommonDateEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "gateway_managed_sensor", indexes = {
        @Index(name = "idx_gateway_managed_sensor_active", columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayManagedSensor extends CommonDateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sensor_id", nullable = false, unique = true, length = 50)
    private String sensorId;

    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_command_poll_at")
    private LocalDateTime lastCommandPollAt;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
}
