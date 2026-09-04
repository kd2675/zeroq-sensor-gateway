package com.zeroq.gateway.service.seat.act;

import com.zeroq.gateway.common.security.GatewayApiKeyGuard;
import com.zeroq.gateway.service.ingest.vo.LocalIngestResponse;
import com.zeroq.gateway.service.seat.biz.SeatSensorIngressService;
import com.zeroq.gateway.service.seat.vo.SeatSensorAdvertisementRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import web.common.core.response.base.dto.ResponseDataDTO;

@RestController
@RequestMapping("/api/zeroq/gateway/v1/local/ingest/seat")
@RequiredArgsConstructor
public class SeatSensorIngressController {
    private final GatewayApiKeyGuard gatewayApiKeyGuard;
    private final SeatSensorIngressService seatSensorIngressService;

    /**
     * BLE scanner가 전달한 광고 hex를 검증·해석해 로컬 telemetry/heartbeat 버퍼로 변환한다.
     * 로컬 API key는 프로세스 접근 제어이며 BLE v3 장치 인증 태그 검증을 대체하지 않는다.
     */
    @PostMapping("/advertisement")
    public ResponseDataDTO<LocalIngestResponse> ingestAdvertisement(
            @Valid @RequestBody SeatSensorAdvertisementRequest request,
            HttpServletRequest httpServletRequest
    ) {
        gatewayApiKeyGuard.requireGatewayApiKey(httpServletRequest);
        return ResponseDataDTO.of(seatSensorIngressService.ingestAdvertisement(request), "점유 센서 광고 패킷 저장 완료");
    }
}
