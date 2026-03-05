DROP TABLE IF EXISTS gateway_command_ack_outbox;
DROP TABLE IF EXISTS gateway_command_buffer;
DROP TABLE IF EXISTS gateway_heartbeat_buffer;
DROP TABLE IF EXISTS gateway_telemetry_buffer;
DROP TABLE IF EXISTS gateway_managed_sensor;

CREATE TABLE IF NOT EXISTS gateway_managed_sensor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sensor_id VARCHAR(50) NOT NULL,
    place_id BIGINT NULL,
    active BOOLEAN NOT NULL,
    last_command_poll_at DATETIME NULL,
    metadata_json TEXT NULL,
    create_date DATETIME NOT NULL,
    update_date DATETIME NOT NULL,
    CONSTRAINT uk_gateway_managed_sensor_sensor_id UNIQUE (sensor_id)
);

CREATE INDEX idx_gateway_managed_sensor_active
    ON gateway_managed_sensor (active);

CREATE TABLE IF NOT EXISTS gateway_telemetry_buffer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sensor_id VARCHAR(50) NOT NULL,
    sequence_no BIGINT NULL,
    place_id BIGINT NULL,
    gateway_id VARCHAR(50) NOT NULL,
    measured_at DATETIME NOT NULL,
    distance_cm DOUBLE NOT NULL,
    confidence DOUBLE NULL,
    temperature_c DOUBLE NULL,
    humidity_percent DOUBLE NULL,
    battery_percent DOUBLE NULL,
    rssi INT NULL,
    raw_payload TEXT NULL,
    sync_status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL,
    last_attempt_at DATETIME NULL,
    error_message VARCHAR(500) NULL,
    create_date DATETIME NOT NULL,
    update_date DATETIME NOT NULL,
    CONSTRAINT uk_gateway_telemetry_buffer_sensor_sequence_measured
        UNIQUE (sensor_id, sequence_no, measured_at),
    CONSTRAINT uk_gateway_telemetry_buffer_sensor_measured
        UNIQUE (sensor_id, measured_at)
);

CREATE INDEX idx_gateway_telemetry_buffer_status
    ON gateway_telemetry_buffer (sync_status, retry_count, id);

CREATE INDEX idx_gateway_telemetry_buffer_sensor
    ON gateway_telemetry_buffer (sensor_id, measured_at);

CREATE TABLE IF NOT EXISTS gateway_heartbeat_buffer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sensor_id VARCHAR(50) NOT NULL,
    place_id BIGINT NULL,
    gateway_id VARCHAR(50) NOT NULL,
    heartbeat_at DATETIME NOT NULL,
    firmware_version VARCHAR(20) NULL,
    battery_percent DOUBLE NULL,
    sync_status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL,
    last_attempt_at DATETIME NULL,
    error_message VARCHAR(500) NULL,
    create_date DATETIME NOT NULL,
    update_date DATETIME NOT NULL,
    CONSTRAINT uk_gateway_heartbeat_buffer_sensor_heartbeat
        UNIQUE (sensor_id, heartbeat_at)
);

CREATE INDEX idx_gateway_heartbeat_buffer_status
    ON gateway_heartbeat_buffer (sync_status, retry_count, id);

CREATE INDEX idx_gateway_heartbeat_buffer_sensor
    ON gateway_heartbeat_buffer (sensor_id, heartbeat_at);

CREATE TABLE IF NOT EXISTS gateway_command_buffer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cloud_command_id BIGINT NOT NULL,
    sensor_id VARCHAR(50) NOT NULL,
    command_type VARCHAR(30) NOT NULL,
    command_payload TEXT NULL,
    requested_at DATETIME NOT NULL,
    command_status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(500) NULL,
    last_dispatch_at DATETIME NULL,
    last_cloud_ack_at DATETIME NULL,
    create_date DATETIME NOT NULL,
    update_date DATETIME NOT NULL,
    CONSTRAINT uk_gateway_command_buffer_cloud_command_id UNIQUE (cloud_command_id)
);

CREATE INDEX idx_gateway_command_buffer_status
    ON gateway_command_buffer (command_status, requested_at);

CREATE INDEX idx_gateway_command_buffer_sensor
    ON gateway_command_buffer (sensor_id);

CREATE TABLE IF NOT EXISTS gateway_command_ack_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cloud_command_id BIGINT NOT NULL,
    sensor_id VARCHAR(50) NOT NULL,
    ack_status VARCHAR(20) NOT NULL,
    ack_payload TEXT NULL,
    failure_reason VARCHAR(500) NULL,
    acknowledged_at DATETIME NOT NULL,
    sync_status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL,
    last_attempt_at DATETIME NULL,
    error_message VARCHAR(500) NULL,
    create_date DATETIME NOT NULL,
    update_date DATETIME NOT NULL
);

CREATE INDEX idx_gateway_command_ack_outbox_status
    ON gateway_command_ack_outbox (sync_status, retry_count, id);
