package com.zeroq.gateway.database.pub.repository;

import com.zeroq.gateway.database.pub.entity.GatewayCommandBuffer;
import com.zeroq.gateway.database.pub.entity.GatewayCommandBufferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GatewayCommandBufferRepository extends JpaRepository<GatewayCommandBuffer, Long> {
    Optional<GatewayCommandBuffer> findByCloudCommandId(Long cloudCommandId);

    List<GatewayCommandBuffer> findTop100ByCommandStatusOrderByRequestedAtAsc(GatewayCommandBufferStatus status);

    long countByCommandStatus(GatewayCommandBufferStatus status);
}
