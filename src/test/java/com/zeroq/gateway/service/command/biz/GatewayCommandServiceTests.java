package com.zeroq.gateway.service.command.biz;

import com.zeroq.gateway.database.pub.entity.GatewayCommandBuffer;
import com.zeroq.gateway.database.pub.entity.GatewayCommandBufferStatus;
import com.zeroq.gateway.database.pub.entity.SensorCommandType;
import com.zeroq.gateway.database.pub.repository.GatewayCommandAckOutboxRepository;
import com.zeroq.gateway.database.pub.repository.GatewayCommandBufferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayCommandServiceTests {

    @Mock
    private GatewayCommandBufferRepository gatewayCommandBufferRepository;

    @Mock
    private GatewayCommandAckOutboxRepository gatewayCommandAckOutboxRepository;

    @InjectMocks
    private GatewayCommandService gatewayCommandService;

    @Test
    void getPendingCommands_ackLossIncludesPreviouslyDispatchedCommand() {
        GatewayCommandBuffer dispatched = GatewayCommandBuffer.builder()
                .cloudCommandId(42L)
                .sensorId("SPOT-014")
                .commandType(SensorCommandType.SET_THRESHOLD)
                .commandPayload("700,850")
                .requestedAt(LocalDateTime.now())
                .commandStatus(GatewayCommandBufferStatus.DISPATCHED)
                .build();
        when(gatewayCommandBufferRepository.findTop100ByCommandStatusInOrderByRequestedAtAsc(
                org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(List.of(dispatched));

        var response = gatewayCommandService.getPendingCommands(20);

        verify(gatewayCommandBufferRepository)
                .findTop100ByCommandStatusInOrderByRequestedAtAsc(List.of(
                        GatewayCommandBufferStatus.PENDING_DISPATCH,
                        GatewayCommandBufferStatus.DISPATCHED
                ));
        assertThat(response).singleElement().satisfies(command ->
                assertThat(command.getCloudCommandId()).isEqualTo(42L)
        );
    }
}
