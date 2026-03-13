package com.zeroq.gateway.service.monitoring.biz;

import com.zeroq.gateway.common.config.GatewayNodeProperties;
import com.zeroq.gateway.database.pub.repository.GatewayManagedSensorRepository;
import com.zeroq.gateway.infrastructure.cloud.CloudSensorApiClient;
import com.zeroq.gateway.service.monitoring.vo.GatewayQueueStatusResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayStatusSyncServiceTests {

    @Mock
    private GatewayMonitoringService gatewayMonitoringService;

    @Mock
    private GatewayManagedSensorRepository gatewayManagedSensorRepository;

    @Mock
    private CloudSensorApiClient cloudSensorApiClient;

    private final GatewayNodeProperties gatewayNodeProperties = new GatewayNodeProperties();
    private final GatewayCloudRuntimeMetricsService gatewayCloudRuntimeMetricsService = new GatewayCloudRuntimeMetricsService();

    @Test
    void pushGatewayStatusShouldSendDerivedGatewayMetrics() {
        GatewayStatusSyncService gatewayStatusSyncService = new GatewayStatusSyncService(
                gatewayMonitoringService,
                gatewayManagedSensorRepository,
                gatewayNodeProperties,
                gatewayCloudRuntimeMetricsService,
                cloudSensorApiClient
        );
        gatewayNodeProperties.setGatewayId("GW-RUNTIME-01");
        gatewayNodeProperties.setFirmwareVersion("v4.2.1");
        gatewayNodeProperties.setIpAddress("10.0.0.5");

        gatewayCloudRuntimeMetricsService.recordSuccess(18);
        gatewayCloudRuntimeMetricsService.recordSuccess(22);

        when(gatewayMonitoringService.getQueueStatus()).thenReturn(GatewayQueueStatusResponse.builder()
                .telemetryPending(2)
                .telemetryFailed(0)
                .telemetryExhausted(0)
                .heartbeatPending(1)
                .heartbeatFailed(0)
                .heartbeatExhausted(0)
                .commandDispatchPending(0)
                .commandAckPending(1)
                .commandAckFailed(0)
                .commandAckExhausted(0)
                .build());
        when(gatewayManagedSensorRepository.countByActiveTrue()).thenReturn(7L);

        gatewayStatusSyncService.pushGatewayStatus();

        ArgumentCaptor<CloudSensorApiClient.CloudGatewayStatus> captor =
                ArgumentCaptor.forClass(CloudSensorApiClient.CloudGatewayStatus.class);
        verify(cloudSensorApiClient).postGatewayStatus(captor.capture());

        CloudSensorApiClient.CloudGatewayStatus payload = captor.getValue();
        assertThat(payload.getGatewayId()).isEqualTo("GW-RUNTIME-01");
        assertThat(payload.getStatus()).isEqualTo("ONLINE");
        assertThat(payload.getCurrentSensorLoad()).isEqualTo(7);
        assertThat(payload.getLatencyMs()).isEqualTo(20);
        assertThat(payload.getPacketLossPercent()).isZero();
    }

    @Test
    void pushGatewayStatusShouldMarkGatewayWarningWhenQueueFailuresAccumulate() {
        GatewayStatusSyncService gatewayStatusSyncService = new GatewayStatusSyncService(
                gatewayMonitoringService,
                gatewayManagedSensorRepository,
                gatewayNodeProperties,
                gatewayCloudRuntimeMetricsService,
                cloudSensorApiClient
        );
        gatewayNodeProperties.setGatewayId("GW-RUNTIME-02");
        gatewayCloudRuntimeMetricsService.recordSuccess(25);
        gatewayCloudRuntimeMetricsService.recordFailure(40);

        when(gatewayMonitoringService.getQueueStatus()).thenReturn(GatewayQueueStatusResponse.builder()
                .telemetryPending(0)
                .telemetryFailed(2)
                .telemetryExhausted(0)
                .heartbeatPending(0)
                .heartbeatFailed(0)
                .heartbeatExhausted(0)
                .commandDispatchPending(0)
                .commandAckPending(0)
                .commandAckFailed(0)
                .commandAckExhausted(0)
                .build());
        when(gatewayManagedSensorRepository.countByActiveTrue()).thenReturn(1L);

        gatewayStatusSyncService.pushGatewayStatus();

        ArgumentCaptor<CloudSensorApiClient.CloudGatewayStatus> captor =
                ArgumentCaptor.forClass(CloudSensorApiClient.CloudGatewayStatus.class);
        verify(cloudSensorApiClient).postGatewayStatus(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("WARNING");
    }
}
