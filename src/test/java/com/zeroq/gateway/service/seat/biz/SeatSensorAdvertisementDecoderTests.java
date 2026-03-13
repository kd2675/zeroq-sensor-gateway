package com.zeroq.gateway.service.seat.biz;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeatSensorAdvertisementDecoderTests {

    private final SeatSensorAdvertisementDecoder decoder = new SeatSensorAdvertisementDecoder();

    @Test
    void shouldDecodeSeatSensorPayload() {
        String payloadHex = "0103534541542D3031349B5A50B004000084CCA96700";

        var decoded = decoder.decode(payloadHex);

        assertThat(decoded.getSensorId()).isEqualTo("SEAT-014");
        assertThat(decoded.isOccupied()).isTrue();
        assertThat(decoded.isHeartbeat()).isTrue();
        assertThat(decoded.getLeftValue()).isEqualTo(620);
        assertThat(decoded.getRightValue()).isEqualTo(360);
        assertThat(decoded.getBatteryPercent()).isEqualTo(80);
        assertThat(decoded.getSequenceNo()).isEqualTo(1200L);
    }
}
