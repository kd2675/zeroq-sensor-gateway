package com.zeroq.gateway.service.seat.biz;

import com.zeroq.gateway.common.exception.GatewayException;
import com.zeroq.gateway.common.config.GatewayBleProperties;
import com.zeroq.gateway.common.security.SipHash24;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatSensorAdvertisementDecoderTests {

    private static final String SENSOR_KEY_HEX = "000102030405060708090A0B0C0D0E0F";
    private final GatewayBleProperties properties = legacyProperties();
    private final SeatSensorAdvertisementDecoder decoder = new SeatSensorAdvertisementDecoder(properties);

    @Test
    void shouldDecodeSeatSensorPayload() {
        String payloadHex = "0103534541542D3031349B5A50B004000084CCA96700";

        var decoded = decoder.decode(payloadHex);

        assertThat(decoded).satisfies(value -> {
            assertThat(value.getProtocolVersion()).isEqualTo(1);
            assertThat(value.getSensorId()).isEqualTo("SEAT-014");
            assertThat(value.isOccupied()).isTrue();
            assertThat(value.isHeartbeat()).isTrue();
            assertThat(value.getLeftValue()).isEqualTo(620);
            assertThat(value.getRightValue()).isEqualTo(360);
            assertThat(value.getBatteryPercent()).isEqualTo(80);
            assertThat(value.getSequenceNo()).isEqualTo(1200L);
            assertThat(value.getMeasuredAt()).isEqualTo(
                    LocalDateTime.ofEpochSecond(1739181188L, 0, ZoneOffset.UTC)
            );
            assertThat(value.getDeviceUptimeSeconds()).isNull();
        });
    }

    @Test
    void decode_protocolVersionOne_ignoresReservedV2Flags() {
        String payloadHex = "011B534541542D3031349B5A50B004000084CCA96700";

        var decoded = decoder.decode(payloadHex);

        assertThat(decoded).satisfies(value -> {
            assertThat(value.isDistanceMode()).isFalse();
            assertThat(value.isSensorFault()).isFalse();
            assertThat(value.getLeftValue()).isEqualTo(620);
            assertThat(value.getRightValue()).isEqualTo(360);
        });
    }

    @Test
    void decode_protocolVersionTwo_decodesUptimeAndChecksum() {
        String payloadHex = buildVersionTwoPayload(80, 3600L);

        var decoded = decoder.decode(payloadHex);

        assertThat(decoded).satisfies(value -> {
            assertThat(value.getProtocolVersion()).isEqualTo(2);
            assertThat(value.getSensorId()).isEqualTo("SEAT-014");
            assertThat(value.isOccupied()).isTrue();
            assertThat(value.isHeartbeat()).isTrue();
            assertThat(value.getLeftValue()).isEqualTo(620);
            assertThat(value.getRightValue()).isEqualTo(360);
            assertThat(value.getBatteryPercent()).isEqualTo(80);
            assertThat(value.getSequenceNo()).isEqualTo(1200L);
            assertThat(value.getMeasuredAt()).isNull();
            assertThat(value.getDeviceUptimeSeconds()).isEqualTo(3600L);
            assertThat(value.isDistanceMode()).isFalse();
            assertThat(value.getDistanceMm()).isNull();
        });
    }

    @Test
    void decode_distanceMode_decodesMillimetersAndOmitsPadValues() {
        String payloadHex = buildVersionTwoDistancePayload(742);

        var decoded = decoder.decode(payloadHex);

        assertThat(decoded).satisfies(value -> {
            assertThat(value.isDistanceMode()).isTrue();
            assertThat(value.getDistanceMm()).isEqualTo(742);
            assertThat(value.getLeftValue()).isNull();
            assertThat(value.getRightValue()).isNull();
            assertThat(value.isOccupied()).isTrue();
        });
    }

    @Test
    void decode_protocolVersionThree_validatesSensorAuthenticationTag() {
        properties.setSensorKeys(java.util.Map.of("SPOT-014", SENSOR_KEY_HEX));

        var decoded = decoder.decode(buildVersionThreeDistancePayload(742));

        assertThat(decoded).satisfies(value -> {
            assertThat(value.getProtocolVersion()).isEqualTo(3);
            assertThat(value.getSensorId()).isEqualTo("SPOT-014");
            assertThat(value.getDistanceMm()).isEqualTo(742);
            assertThat(value.getDeviceUptimeSeconds()).isEqualTo(3600L);
        });
    }

    @Test
    void decode_protocolVersionThree_withModifiedPayload_rejectsAuthenticationTag() {
        properties.setSensorKeys(java.util.Map.of("SPOT-014", SENSOR_KEY_HEX));
        byte[] payload = HexFormat.of().parseHex(buildVersionThreeDistancePayload(742));
        payload[10] ^= 0x01;

        assertThatThrownBy(() -> decoder.decode(HexFormat.of().formatHex(payload)))
                .isInstanceOf(GatewayException.ValidationException.class)
                .hasMessageContaining("authentication tag");
    }

    @Test
    void decode_protocolVersionThree_withReservedFlag_rejectsPayload() {
        properties.setSensorKeys(java.util.Map.of("SPOT-014", SENSOR_KEY_HEX));
        byte[] payload = HexFormat.of().parseHex(buildVersionThreeDistancePayload(742));
        payload[1] |= (byte) 0x80;
        writeUnsignedInt(payload, 20, SipHash24.hash(HexFormat.of().parseHex(SENSOR_KEY_HEX), payload, 20));

        assertThatThrownBy(() -> decoder.decode(HexFormat.of().formatHex(payload)))
                .isInstanceOf(GatewayException.ValidationException.class)
                .hasMessageContaining("reserved flag");
    }

    @Test
    void decode_protocolVersionThree_withoutDistanceMode_rejectsPayload() {
        properties.setSensorKeys(java.util.Map.of("SPOT-014", SENSOR_KEY_HEX));
        byte[] payload = HexFormat.of().parseHex(buildVersionThreeDistancePayload(742));
        payload[1] &= (byte) ~0x08;
        writeUnsignedInt(payload, 20, SipHash24.hash(HexFormat.of().parseHex(SENSOR_KEY_HEX), payload, 20));

        assertThatThrownBy(() -> decoder.decode(HexFormat.of().formatHex(payload)))
                .isInstanceOf(GatewayException.ValidationException.class)
                .hasMessageContaining("requires distance mode");
    }

    @Test
    void decode_distanceModeWithOutOfRangeValue_throwsValidationException() {
        String payloadHex = buildVersionTwoDistancePayload(4001);

        assertThatThrownBy(() -> decoder.decode(payloadHex))
                .isInstanceOf(GatewayException.ValidationException.class)
                .hasMessageContaining("distanceMm");
    }

    @Test
    void decode_protocolVersionTwoWithInvalidChecksum_throwsValidationException() {
        byte[] payload = HexFormat.of().parseHex(buildVersionTwoPayload(80, 3600L));
        payload[21] ^= 0x01;

        assertThatThrownBy(() -> decoder.decode(HexFormat.of().formatHex(payload)))
                .isInstanceOf(GatewayException.ValidationException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void decode_batteryPercentAboveOneHundred_throwsValidationException() {
        String payloadHex = buildVersionTwoPayload(101, 3600L);

        assertThatThrownBy(() -> decoder.decode(payloadHex))
                .isInstanceOf(GatewayException.ValidationException.class)
                .hasMessageContaining("batteryPercent");
    }

    @Test
    void decode_unsupportedProtocolVersion_throwsValidationException() {
        byte[] payload = HexFormat.of().parseHex(buildVersionTwoPayload(80, 3600L));
        payload[0] = 3;

        assertThatThrownBy(() -> decoder.decode(HexFormat.of().formatHex(payload)))
                .isInstanceOf(GatewayException.ValidationException.class)
                .hasMessageContaining("protocol version");
    }

    @Test
    void decode_nonHexPayload_throwsValidationException() {
        assertThatThrownBy(() -> decoder.decode("notghex0"))
                .isInstanceOf(GatewayException.ValidationException.class)
                .hasMessageContaining("hexadecimal");
    }

    @Test
    void decode_sensorIdWithNonAsciiByte_throwsValidationException() {
        byte[] payload = HexFormat.of().parseHex(buildVersionTwoPayload(80, 3600L));
        payload[2] = (byte) 0x80;
        payload[21] = crc8(payload, 21);

        assertThatThrownBy(() -> decoder.decode(HexFormat.of().formatHex(payload)))
                .isInstanceOf(GatewayException.ValidationException.class)
                .hasMessageContaining("sensorId");
    }

    @Test
    void decode_sensorIdWithDataAfterNullPadding_throwsValidationException() {
        byte[] payload = HexFormat.of().parseHex(buildVersionTwoPayload(80, 3600L));
        payload[4] = 0;
        payload[21] = crc8(payload, 21);

        assertThatThrownBy(() -> decoder.decode(HexFormat.of().formatHex(payload)))
                .isInstanceOf(GatewayException.ValidationException.class)
                .hasMessageContaining("sensorId");
    }

    private String buildVersionTwoPayload(int batteryPercent, long uptimeSeconds) {
        byte[] payload = new byte[22];
        payload[0] = 2;
        payload[1] = 3;
        byte[] sensorId = "SEAT-014".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(sensorId, 0, payload, 2, sensorId.length);
        payload[10] = (byte) 155;
        payload[11] = (byte) 90;
        payload[12] = (byte) batteryPercent;
        writeUnsignedInt(payload, 13, 1200L);
        writeUnsignedInt(payload, 17, uptimeSeconds);
        payload[21] = crc8(payload, 21);
        return HexFormat.of().formatHex(payload);
    }

    private String buildVersionTwoDistancePayload(int distanceMm) {
        byte[] payload = new byte[22];
        payload[0] = 2;
        payload[1] = 0x0B;
        byte[] sensorId = "SPOT-014".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(sensorId, 0, payload, 2, sensorId.length);
        payload[10] = (byte) distanceMm;
        payload[11] = (byte) (distanceMm >> 8);
        payload[12] = 80;
        writeUnsignedInt(payload, 13, 1200L);
        writeUnsignedInt(payload, 17, 3600L);
        payload[21] = crc8(payload, 21);
        return HexFormat.of().formatHex(payload);
    }

    private String buildVersionThreeDistancePayload(int distanceMm) {
        byte[] payload = new byte[24];
        payload[0] = 3;
        payload[1] = 0x0B;
        byte[] sensorId = "SPOT-014".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(sensorId, 0, payload, 2, sensorId.length);
        payload[10] = (byte) distanceMm;
        payload[11] = (byte) (distanceMm >> 8);
        payload[12] = 80;
        writeUnsignedInt(payload, 13, 1200L);
        payload[17] = 0x10;
        payload[18] = 0x0E;
        payload[19] = 0x00;
        byte[] key = HexFormat.of().parseHex(SENSOR_KEY_HEX);
        writeUnsignedInt(payload, 20, SipHash24.hash(key, payload, 20));
        return HexFormat.of().formatHex(payload);
    }

    private GatewayBleProperties legacyProperties() {
        GatewayBleProperties value = new GatewayBleProperties();
        value.setAllowLegacyUnsigned(true);
        return value;
    }

    private void writeUnsignedInt(byte[] target, int offset, long value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >> 8);
        target[offset + 2] = (byte) (value >> 16);
        target[offset + 3] = (byte) (value >> 24);
    }

    private byte crc8(byte[] bytes, int length) {
        int crc = 0;
        for (int i = 0; i < length; i++) {
            crc ^= bytes[i] & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
            }
        }
        return (byte) crc;
    }
}
