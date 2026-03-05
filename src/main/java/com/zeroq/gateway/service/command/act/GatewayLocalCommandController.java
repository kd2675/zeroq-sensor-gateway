package com.zeroq.gateway.service.command.act;

import com.zeroq.gateway.common.security.GatewayApiKeyGuard;
import com.zeroq.gateway.service.command.biz.GatewayCommandService;
import com.zeroq.gateway.service.command.vo.GatewayCommandResponse;
import com.zeroq.gateway.service.command.vo.LocalCommandAckRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import web.common.core.response.base.dto.ResponseDataDTO;

import java.util.List;

@RestController
@RequestMapping("/api/zeroq/gateway/v1/local/commands")
@RequiredArgsConstructor
public class GatewayLocalCommandController {
    private final GatewayApiKeyGuard gatewayApiKeyGuard;
    private final GatewayCommandService gatewayCommandService;

    @GetMapping("/pending")
    public ResponseDataDTO<List<GatewayCommandResponse>> getPendingCommands(
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest httpServletRequest
    ) {
        gatewayApiKeyGuard.requireGatewayApiKey(httpServletRequest);
        return ResponseDataDTO.of(gatewayCommandService.getPendingCommands(limit), "로컬 실행 대기 명령 조회 완료");
    }

    @PatchMapping("/{cloudCommandId}/dispatched")
    public ResponseDataDTO<GatewayCommandResponse> markDispatched(
            @PathVariable Long cloudCommandId,
            HttpServletRequest httpServletRequest
    ) {
        gatewayApiKeyGuard.requireGatewayApiKey(httpServletRequest);
        return ResponseDataDTO.of(gatewayCommandService.markDispatched(cloudCommandId), "명령 디스패치 처리 완료");
    }

    @PostMapping("/{cloudCommandId}/ack")
    public ResponseDataDTO<GatewayCommandResponse> ackCommand(
            @PathVariable Long cloudCommandId,
            @Valid @RequestBody LocalCommandAckRequest request,
            HttpServletRequest httpServletRequest
    ) {
        gatewayApiKeyGuard.requireGatewayApiKey(httpServletRequest);
        return ResponseDataDTO.of(gatewayCommandService.enqueueLocalAck(cloudCommandId, request), "클라우드 ACK 대기열 적재 완료");
    }
}
