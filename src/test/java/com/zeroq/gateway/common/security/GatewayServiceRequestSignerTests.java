package com.zeroq.gateway.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayServiceRequestSignerTests {

    @Test
    void signShouldBeDeterministicForSameInput() {
        GatewayServiceRequestSigner signer = new GatewayServiceRequestSigner();

        String first = signer.sign(
                "GW-STORE-001",
                "POST",
                "/internal/zeroq/gateway/sensor/ingest/gateway-heartbeat",
                "1773134000000",
                "nonce-1",
                "shared-secret"
        );
        String second = signer.sign(
                "GW-STORE-001",
                "POST",
                "/internal/zeroq/gateway/sensor/ingest/gateway-heartbeat",
                "1773134000000",
                "nonce-1",
                "shared-secret"
        );

        assertThat(first).isEqualTo(second);
    }
}
