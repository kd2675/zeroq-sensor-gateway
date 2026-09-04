package com.zeroq.gateway.service.ingest.biz;

import com.zeroq.gateway.database.pub.entity.GatewayHeartbeatBuffer;
import com.zeroq.gateway.database.pub.entity.GatewayTelemetryBuffer;
import com.zeroq.gateway.database.pub.repository.GatewayHeartbeatBufferRepository;
import com.zeroq.gateway.database.pub.repository.GatewayTelemetryBufferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class GatewayBufferPersistenceService {
    private final GatewayTelemetryBufferRepository gatewayTelemetryBufferRepository;
    private final GatewayHeartbeatBufferRepository gatewayHeartbeatBufferRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveTelemetry(GatewayTelemetryBuffer buffer) {
        gatewayTelemetryBufferRepository.saveAndFlush(buffer);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveHeartbeat(GatewayHeartbeatBuffer buffer) {
        gatewayHeartbeatBufferRepository.saveAndFlush(buffer);
    }
}
