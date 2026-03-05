package com.zeroq.gateway.database.pub.repository;

import com.zeroq.gateway.database.pub.entity.BufferSyncStatus;
import com.zeroq.gateway.database.pub.entity.GatewayTelemetryBuffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface GatewayTelemetryBufferRepository extends JpaRepository<GatewayTelemetryBuffer, Long> {
    List<GatewayTelemetryBuffer> findTop200BySyncStatusInAndRetryCountLessThanOrderByIdAsc(
            Collection<BufferSyncStatus> statuses,
            int retryCount
    );

    long countBySyncStatus(BufferSyncStatus status);
}
