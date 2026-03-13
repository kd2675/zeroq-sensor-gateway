package com.zeroq.gateway.service.seat.biz;

import com.zeroq.gateway.common.exception.GatewayException;
import com.zeroq.gateway.service.seat.vo.DecodedSeatSensorAdvertisement;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class SeatSensorAdvertisementDecoder {
    private static final int EXPECTED_BYTES = 22;
    private static final int FLAG_OCCUPIED = 0x01;
    private static final int FLAG_HEARTBEAT = 0x02;
    private static final int FLAG_LOW_BATTERY = 0x04;

    public DecodedSeatSensorAdvertisement decode(String payloadHex) {
        byte[] bytes = decodeHex(payloadHex);
        if (bytes.length != EXPECTED_BYTES) {
            throw new GatewayException.ValidationException(
                    "Seat sensor payload must be 22 bytes, but was %d bytes".formatted(bytes.length)
            );
        }

        int flags = unsigned(bytes[1]);
        String sensorId = decodeSensorId(bytes, 2, 8);
        int leftValue = unsigned(bytes[10]) << 2;
        int rightValue = unsigned(bytes[11]) << 2;
        int batteryPercent = unsigned(bytes[12]);
        long sequenceNo = unsignedInt(bytes, 13);
        long epochSeconds = unsignedInt(bytes, 17);

        return DecodedSeatSensorAdvertisement.builder()
                .sensorId(sensorId)
                .occupied((flags & FLAG_OCCUPIED) != 0)
                .heartbeat((flags & FLAG_HEARTBEAT) != 0)
                .lowBattery((flags & FLAG_LOW_BATTERY) != 0)
                .leftValue(leftValue)
                .rightValue(rightValue)
                .batteryPercent(batteryPercent)
                .sequenceNo(sequenceNo)
                .measuredAt(LocalDateTime.ofEpochSecond(epochSeconds, 0, ZoneOffset.UTC))
                .build();
    }

    private byte[] decodeHex(String hex) {
        String normalized = hex == null ? "" : hex.replace(" ", "").replace("0x", "");
        if ((normalized.length() & 1) != 0) {
            throw new GatewayException.ValidationException("Seat sensor payloadHex length must be even");
        }

        byte[] bytes = new byte[normalized.length() / 2];
        for (int i = 0; i < normalized.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(normalized.substring(i, i + 2), 16);
        }
        return bytes;
    }

    private String decodeSensorId(byte[] bytes, int offset, int length) {
        byte[] slice = new byte[length];
        System.arraycopy(bytes, offset, slice, 0, length);
        return new String(slice, StandardCharsets.US_ASCII).replace("\u0000", "").trim();
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
}
