-- Existing gateway DB migration. Run once before enabling BLE address binding.
ALTER TABLE gateway_managed_sensor
    ADD COLUMN IF NOT EXISTS mac_address VARCHAR(20) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_gateway_managed_sensor_mac_address
    ON gateway_managed_sensor (mac_address);
