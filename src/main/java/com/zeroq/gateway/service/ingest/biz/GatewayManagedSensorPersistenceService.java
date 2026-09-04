package com.zeroq.gateway.service.ingest.biz;

import com.zeroq.gateway.common.exception.GatewayException;
import com.zeroq.gateway.database.pub.entity.GatewayManagedSensor;
import com.zeroq.gateway.database.pub.repository.GatewayManagedSensorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
class GatewayManagedSensorPersistenceService {
    private final GatewayManagedSensorRepository gatewayManagedSensorRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsert(String sensorId, Long placeId, String macAddress) {
        GatewayManagedSensor managedSensor = gatewayManagedSensorRepository.findBySensorId(sensorId)
                .orElseGet(() -> GatewayManagedSensor.builder()
                        .sensorId(sensorId)
                        .active(true)
                        .build());

        String normalizedMacAddress = normalizeMacAddress(macAddress);
        if (normalizedMacAddress != null) {
            if (managedSensor.getMacAddress() != null
                    && !managedSensor.getMacAddress().equals(normalizedMacAddress)) {
                throw new GatewayException.ValidationException(
                        "Sensor BLE address changed for sensorId: " + sensorId
                );
            }
            gatewayManagedSensorRepository.findByMacAddress(normalizedMacAddress).ifPresent(owner -> {
                if (!owner.getSensorId().equals(sensorId)) {
                    throw new GatewayException.ValidationException(
                            "BLE address is already assigned to another sensor"
                    );
                }
            });
            managedSensor.setMacAddress(normalizedMacAddress);
        }

        managedSensor.setPlaceId(placeId);
        managedSensor.setActive(true);
        gatewayManagedSensorRepository.saveAndFlush(managedSensor);
    }

    private String normalizeMacAddress(String macAddress) {
        if (macAddress == null || macAddress.isBlank()) {
            return null;
        }
        String normalized = macAddress.trim().toUpperCase(Locale.ROOT).replace('-', ':');
        if (!normalized.matches("[0-9A-F]{2}(:[0-9A-F]{2}){5}")) {
            throw new GatewayException.ValidationException(
                    "macAddress must use AA:BB:CC:DD:EE:FF format"
            );
        }
        return normalized;
    }
}
