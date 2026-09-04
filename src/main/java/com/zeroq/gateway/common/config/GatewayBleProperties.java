package com.zeroq.gateway.common.config;

import com.zeroq.gateway.common.exception.GatewayException;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.ble")
public class GatewayBleProperties {
    private boolean allowLegacyUnsigned;
    private Map<String, String> sensorKeys = new HashMap<>();

    public byte[] requireSensorKey(String sensorId) {
        String keyHex = sensorKeys.get(sensorId);
        if (keyHex == null || keyHex.isBlank()) {
            throw new GatewayException.ValidationException("No BLE authentication key configured for sensorId: " + sensorId);
        }
        try {
            byte[] key = HexFormat.of().parseHex(keyHex.trim());
            if (key.length != 16) {
                throw new GatewayException.ValidationException("BLE sensor key must be exactly 16 bytes");
            }
            return key;
        } catch (IllegalArgumentException ex) {
            throw new GatewayException.ValidationException("BLE sensor key must be 32 hexadecimal characters");
        }
    }
}
