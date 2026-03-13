package com.zeroq.gateway.service.monitoring.biz;

import com.zeroq.gateway.service.monitoring.vo.GatewayCloudRuntimeMetricsSnapshot;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;

@Service
public class GatewayCloudRuntimeMetricsService {
    private static final int WINDOW_SIZE = 24;

    private final Deque<AttemptSample> samples = new ArrayDeque<>();
    private int consecutiveFailureCount = 0;
    private LocalDateTime lastSuccessfulCloudSyncAt;

    public synchronized void recordSuccess(long latencyMs) {
        record(true, latencyMs);
        consecutiveFailureCount = 0;
        lastSuccessfulCloudSyncAt = LocalDateTime.now();
    }

    public synchronized void recordFailure(long latencyMs) {
        record(false, latencyMs);
        consecutiveFailureCount++;
    }

    public synchronized GatewayCloudRuntimeMetricsSnapshot snapshot() {
        int successCount = 0;
        long successLatencySum = 0L;
        int failureCount = 0;

        for (AttemptSample sample : samples) {
            if (sample.success()) {
                successCount++;
                successLatencySum += sample.latencyMs();
            } else {
                failureCount++;
            }
        }

        int totalCount = successCount + failureCount;
        Integer averageLatencyMs = successCount == 0 ? null : Math.toIntExact(Math.round((double) successLatencySum / successCount));
        Double packetLossPercent = totalCount == 0 ? 0.0 : (failureCount * 100.0) / totalCount;

        return GatewayCloudRuntimeMetricsSnapshot.builder()
                .averageLatencyMs(averageLatencyMs)
                .packetLossPercent(packetLossPercent)
                .lastSuccessfulCloudSyncAt(lastSuccessfulCloudSyncAt)
                .consecutiveFailureCount(consecutiveFailureCount)
                .build();
    }

    private void record(boolean success, long latencyMs) {
        samples.addLast(new AttemptSample(success, Math.max(latencyMs, 0L)));
        while (samples.size() > WINDOW_SIZE) {
            samples.removeFirst();
        }
    }

    private record AttemptSample(boolean success, long latencyMs) {
    }
}
