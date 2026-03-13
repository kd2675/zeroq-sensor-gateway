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

    @PostMapping("/advertisement")
    public ResponseDataDTO<LocalIngestResponse> ingestAdvertisement(
            @Valid @RequestBody SeatSensorAdvertisementRequest request,
            HttpServletRequest httpServletRequest
    ) {
        gatewayApiKeyGuard.requireGatewayApiKey(httpServletRequest);
        return ResponseDataDTO.of(seatSensorIngressService.ingestAdvertisement(request), "좌석 센서 광고 패킷 저장 완료");
    }
}
