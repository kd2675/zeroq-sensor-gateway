package com.zeroq.gateway.database.pub.repository;

import com.zeroq.gateway.database.pub.entity.GatewayManagedSensor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GatewayManagedSensorRepository extends JpaRepository<GatewayManagedSensor, Long> {
    Optional<GatewayManagedSensor> findBySensorId(String sensorId);

    List<GatewayManagedSensor> findAllByActiveTrueOrderBySensorIdAsc();

    long countByActiveTrue();
}
