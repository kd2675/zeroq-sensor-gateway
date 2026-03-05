package com.zeroq.gateway.database.pub.repository;

import com.zeroq.gateway.database.pub.entity.BufferSyncStatus;
import com.zeroq.gateway.database.pub.entity.GatewayCommandAckOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface GatewayCommandAckOutboxRepository extends JpaRepository<GatewayCommandAckOutbox, Long> {
    List<GatewayCommandAckOutbox> findTop200BySyncStatusInAndRetryCountLessThanOrderByIdAsc(
            Collection<BufferSyncStatus> statuses,
            int retryCount
    );

    long countBySyncStatus(BufferSyncStatus status);

    long countBySyncStatusAndRetryCountGreaterThanEqual(BufferSyncStatus status, int retryCount);
}
