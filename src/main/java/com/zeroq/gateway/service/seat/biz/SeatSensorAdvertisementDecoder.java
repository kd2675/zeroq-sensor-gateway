package com.zeroq.gateway.service.seat.biz;

import com.zeroq.gateway.common.exception.GatewayException;
import com.zeroq.gateway.common.config.GatewayBleProperties;
import com.zeroq.gateway.common.security.SipHash24;
import com.zeroq.gateway.service.seat.vo.DecodedSeatSensorAdvertisement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class SeatSensorAdvertisementDecoder {
    private static final int LEGACY_BYTES = 22;
    private static final int AUTHENTICATED_BYTES = 24;
    private static final int VERSION_ONE = 1;
    private static final int VERSION_TWO = 2;
    private static final int VERSION_THREE = 3;
    private static final int FLAG_OCCUPIED = 0x01;
    private static final int FLAG_HEARTBEAT = 0x02;
    private static final int FLAG_LOW_BATTERY = 0x04;
    private static final int FLAG_DISTANCE_MODE = 0x08;
    private static final int FLAG_SENSOR_FAULT = 0x10;
    private static final int ALLOWED_FLAGS = 0x1F;
    private static final int MAX_DISTANCE_MM = 4000;
    private final GatewayBleProperties gatewayBleProperties;

    public DecodedSeatSensorAdvertisement decode(String payloadHex) {
        byte[] bytes = decodeHex(payloadHex);
        if (bytes.length != LEGACY_BYTES && bytes.length != AUTHENTICATED_BYTES) {
            throw new GatewayException.ValidationException(
                    "Seat sensor payload must be 22 or 24 bytes, but was %d bytes".formatted(bytes.length)
            );
        }

        int protocolVersion = unsigned(bytes[0]);
        if (protocolVersion != VERSION_ONE && protocolVersion != VERSION_TWO && protocolVersion != VERSION_THREE) {
            throw new GatewayException.ValidationException(
                    "Unsupported seat sensor protocol version: %d".formatted(protocolVersion)
            );
        }
        if (protocolVersion == VERSION_THREE && bytes.length != AUTHENTICATED_BYTES) {
            throw new GatewayException.ValidationException("protocol version 3 payload must be 24 bytes");
        }
        if (protocolVersion != VERSION_THREE && bytes.length != LEGACY_BYTES) {
            throw new GatewayException.ValidationException("Legacy sensor payload must be 22 bytes");
        }
        if (protocolVersion != VERSION_THREE && !gatewayBleProperties.isAllowLegacyUnsigned()) {
            throw new GatewayException.ValidationException("Unsigned BLE protocol is disabled");
        }
        if (protocolVersion == VERSION_TWO && crc8(bytes, LEGACY_BYTES - 1) != unsigned(bytes[21])) {
            throw new GatewayException.ValidationException("Seat sensor payload checksum mismatch");
        }

        int flags = unsigned(bytes[1]);
        if (protocolVersion >= VERSION_TWO && (flags & ~ALLOWED_FLAGS) != 0) {
            throw new GatewayException.ValidationException("Seat sensor reserved flag bits must be zero");
        }
        String sensorId = decodeSensorId(bytes, 2, 8);
        if (protocolVersion == VERSION_THREE) {
            validateAuthenticationTag(sensorId, bytes);
        }
        boolean distanceMode = protocolVersion >= VERSION_TWO && (flags & FLAG_DISTANCE_MODE) != 0;
        if (protocolVersion == VERSION_THREE && !distanceMode) {
            throw new GatewayException.ValidationException("Protocol v3 requires distance mode");
        }
        Integer distanceMm = distanceMode ? unsignedShort(bytes, 10) : null;
        if (distanceMm != null && (distanceMm == 0 || distanceMm > MAX_DISTANCE_MM)) {
            throw new GatewayException.ValidationException(
                    "Seat sensor distanceMm must be between 1 and %d".formatted(MAX_DISTANCE_MM)
            );
        }
        Integer leftValue = distanceMode ? null : unsigned(bytes[10]) << 2;
        Integer rightValue = distanceMode ? null : unsigned(bytes[11]) << 2;
        int batteryPercent = unsigned(bytes[12]);
        if (batteryPercent > 100) {
            throw new GatewayException.ValidationException("Seat sensor batteryPercent must be between 0 and 100");
        }
        long sequenceNo = unsignedInt(bytes, 13);
        long timeValue = protocolVersion == VERSION_THREE
                ? unsigned24(bytes, 17)
                : unsignedInt(bytes, 17);

        return DecodedSeatSensorAdvertisement.builder()
                .protocolVersion(protocolVersion)
                .sensorId(sensorId)
                .occupied((flags & FLAG_OCCUPIED) != 0)
                .heartbeat((flags & FLAG_HEARTBEAT) != 0)
                .lowBattery((flags & FLAG_LOW_BATTERY) != 0)
                .distanceMode(distanceMode)
                .sensorFault(protocolVersion >= VERSION_TWO && (flags & FLAG_SENSOR_FAULT) != 0)
                .distanceMm(distanceMm)
                .leftValue(leftValue)
                .rightValue(rightValue)
                .batteryPercent(batteryPercent)
                .sequenceNo(sequenceNo)
                .measuredAt(protocolVersion == VERSION_ONE
                        ? LocalDateTime.ofEpochSecond(timeValue, 0, ZoneOffset.UTC)
                        : null)
                .deviceUptimeSeconds(protocolVersion >= VERSION_TWO ? timeValue : null)
                .build();
    }

    private void validateAuthenticationTag(String sensorId, byte[] bytes) {
        byte[] key = gatewayBleProperties.requireSensorKey(sensorId);
        long expectedTag = SipHash24.hash(key, bytes, 20) & 0xffffffffL;
        long actualTag = unsignedInt(bytes, 20);
        if (expectedTag != actualTag) {
            throw new GatewayException.ValidationException("Seat sensor authentication tag mismatch");
        }
    }

    private byte[] decodeHex(String hex) {
        String normalized = hex == null ? "" : hex.replaceAll("\\s+", "");
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.isEmpty() || (normalized.length() & 1) != 0) {
            throw new GatewayException.ValidationException("Seat sensor payloadHex length must be even");
        }

        byte[] bytes = new byte[normalized.length() / 2];
        for (int i = 0; i < normalized.length(); i += 2) {
            int high = Character.digit(normalized.charAt(i), 16);
            int low = Character.digit(normalized.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                throw new GatewayException.ValidationException("Seat sensor payloadHex must be hexadecimal");
            }
            bytes[i / 2] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    private String decodeSensorId(byte[] bytes, int offset, int length) {
        StringBuilder sensorId = new StringBuilder(length);
        boolean paddingStarted = false;
        for (int i = offset; i < offset + length; i++) {
            int value = unsigned(bytes[i]);
            if (value == 0) {
                paddingStarted = true;
                continue;
            }
            if (paddingStarted || !isSensorIdCharacter(value)) {
                throw new GatewayException.ValidationException(
                        "Seat sensor sensorId must use 1-8 ASCII letters, digits, '-' or '_'"
                );
            }
            sensorId.append((char) value);
        }
        if (sensorId.isEmpty()) {
            throw new GatewayException.ValidationException("Seat sensor sensorId must not be blank");
        }
        return sensorId.toString();
    }

    private boolean isSensorIdCharacter(int value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '-'
                || value == '_';
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private long unsignedInt(byte[] bytes, int offset) {
        return ((long) unsigned(bytes[offset])) |
                (((long) unsigned(bytes[offset + 1])) << 8) |
                (((long) unsigned(bytes[offset + 2])) << 16) |
                (((long) unsigned(bytes[offset + 3])) << 24);
    }

    private int unsignedShort(byte[] bytes, int offset) {
        return unsigned(bytes[offset]) | (unsigned(bytes[offset + 1]) << 8);
    }

    private long unsigned24(byte[] bytes, int offset) {
        return ((long) unsigned(bytes[offset]))
                | (((long) unsigned(bytes[offset + 1])) << 8)
                | (((long) unsigned(bytes[offset + 2])) << 16);
    }

    private int crc8(byte[] bytes, int length) {
        int crc = 0;
        for (int i = 0; i < length; i++) {
            crc ^= unsigned(bytes[i]);
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
            }
        }
        return crc;
    }
}
