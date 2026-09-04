from __future__ import annotations

import asyncio
import json
import logging
import os
import re
import signal
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any

from scanner_core import (
    DeliveryDeduplicator,
    DeliveryRequestCache,
    InvalidSeatAdvertisement,
    build_gateway_request,
    parse_advertisement,
)


LOGGER = logging.getLogger("zeroq.ble-seat-scanner")
COMMAND_WRITE_UUID = "5a510001-e8f2-537e-4f6c-d104768a1214"
COMMAND_ACK_UUID = "5a510002-e8f2-537e-4f6c-d104768a1214"


@dataclass(frozen=True)
class ScannerConfig:
    gateway_base_url: str
    gateway_api_key: str
    manufacturer_id: int
    place_ids: dict[str, int]
    sensor_keys: dict[str, bytes]
    sensor_addresses: dict[str, str]
    allow_legacy_unsigned: bool
    request_timeout_seconds: float
    adapter: str | None

    @classmethod
    def from_environment(cls) -> "ScannerConfig":
        api_key = os.environ.get("GATEWAY_LOCAL_API_KEY", "").strip()
        if not api_key:
            raise ValueError("GATEWAY_LOCAL_API_KEY is required")

        gateway_base_url = os.environ.get("ZEROQ_GATEWAY_LOCAL_URL", "http://127.0.0.1:20191").rstrip("/")
        manufacturer_id = int(os.environ.get("ZEROQ_BLE_MANUFACTURER_ID", "0x5A51"), 0)
        if manufacturer_id < 0 or manufacturer_id > 0xFFFF:
            raise ValueError("ZEROQ_BLE_MANUFACTURER_ID must fit in 16 bits")

        raw_place_ids = os.environ.get(
            "ZEROQ_SPOT_PLACE_MAP_JSON",
            os.environ.get("ZEROQ_SEAT_PLACE_MAP_JSON", "{}"),
        ).strip()
        decoded_place_ids = json.loads(raw_place_ids)
        if not isinstance(decoded_place_ids, dict):
            raise ValueError("ZEROQ_SPOT_PLACE_MAP_JSON must be a JSON object")
        place_ids = {str(sensor_id): int(place_id) for sensor_id, place_id in decoded_place_ids.items()}

        decoded_keys = json.loads(os.environ.get("ZEROQ_BLE_SENSOR_KEYS_JSON", "{}"))
        if not isinstance(decoded_keys, dict):
            raise ValueError("ZEROQ_BLE_SENSOR_KEYS_JSON must be a JSON object")
        sensor_keys: dict[str, bytes] = {}
        for sensor_id, key_hex in decoded_keys.items():
            try:
                key = bytes.fromhex(str(key_hex))
            except ValueError as error:
                raise ValueError(f"BLE key for {sensor_id} must be hexadecimal") from error
            if len(key) != 16:
                raise ValueError(f"BLE key for {sensor_id} must be exactly 16 bytes")
            sensor_keys[str(sensor_id)] = key

        decoded_addresses = json.loads(os.environ.get("ZEROQ_BLE_SENSOR_ADDRESSES_JSON", "{}"))
        if not isinstance(decoded_addresses, dict):
            raise ValueError("ZEROQ_BLE_SENSOR_ADDRESSES_JSON must be a JSON object")
        sensor_addresses: dict[str, str] = {}
        for sensor_id, address in decoded_addresses.items():
            normalized_address = normalize_ble_address(str(address))
            if not re.fullmatch(r"[0-9A-F]{2}(:[0-9A-F]{2}){5}", normalized_address):
                raise ValueError(
                    f"BLE address for {sensor_id} must use AA:BB:CC:DD:EE:FF format"
                )
            sensor_addresses[str(sensor_id)] = normalized_address
        allow_legacy_unsigned = os.environ.get(
            "ZEROQ_BLE_ALLOW_LEGACY_UNSIGNED", "false"
        ).strip().lower() in {"1", "true", "yes"}

        timeout_seconds = float(os.environ.get("ZEROQ_GATEWAY_REQUEST_TIMEOUT_SECONDS", "5"))
        if timeout_seconds <= 0:
            raise ValueError("ZEROQ_GATEWAY_REQUEST_TIMEOUT_SECONDS must be positive")

        adapter = os.environ.get("ZEROQ_BLE_ADAPTER", "").strip() or None
        return cls(
            gateway_base_url=gateway_base_url,
            gateway_api_key=api_key,
            manufacturer_id=manufacturer_id,
            place_ids=place_ids,
            sensor_keys=sensor_keys,
            sensor_addresses=sensor_addresses,
            allow_legacy_unsigned=allow_legacy_unsigned,
            request_timeout_seconds=timeout_seconds,
            adapter=adapter,
        )


class GatewayClient:
    def __init__(self, config: ScannerConfig) -> None:
        self._config = config

    async def post_advertisement(self, payload: dict[str, object]) -> None:
        await asyncio.to_thread(self._post_json, payload)

    async def get_pending_commands(self) -> list[dict[str, object]]:
        response = await asyncio.to_thread(
            self._request_json,
            "/api/zeroq/gateway/v1/local/commands/pending?limit=20",
            "GET",
            None,
        )
        data = response.get("data")
        return data if isinstance(data, list) else []

    async def mark_dispatched(self, command_id: int) -> None:
        await asyncio.to_thread(
            self._request_json,
            f"/api/zeroq/gateway/v1/local/commands/{command_id}/dispatched",
            "PATCH",
            None,
        )

    async def post_command_ack(
        self,
        command_id: int,
        status: str,
        detail: str,
    ) -> None:
        payload: dict[str, object] = {"status": status}
        if status == "FAILED":
            payload["failureReason"] = detail
        else:
            payload["ackPayload"] = detail
        await asyncio.to_thread(
            self._request_json,
            f"/api/zeroq/gateway/v1/local/commands/{command_id}/ack",
            "POST",
            payload,
        )

    def _post_json(self, payload: dict[str, object]) -> None:
        self._request_json(
            "/api/zeroq/gateway/v1/local/ingest/seat/advertisement",
            "POST",
            payload,
        )

    def _request_json(
        self,
        path: str,
        method: str,
        payload: dict[str, object] | None,
    ) -> dict[str, object]:
        body = None if payload is None else json.dumps(payload, separators=(",", ":")).encode("utf-8")
        request = urllib.request.Request(
            f"{self._config.gateway_base_url}{path}",
            data=body,
            headers={
                "Content-Type": "application/json",
                "X-Gateway-Key": self._config.gateway_api_key,
            },
            method=method,
        )
        try:
            with urllib.request.urlopen(request, timeout=self._config.request_timeout_seconds) as response:
                if response.status < 200 or response.status >= 300:
                    raise RuntimeError(f"gateway returned HTTP {response.status}")
                response_body = response.read()
                if not response_body:
                    return {}
                decoded = json.loads(response_body)
                return decoded if isinstance(decoded, dict) else {}
        except urllib.error.HTTPError as error:
            raise RuntimeError(f"gateway returned HTTP {error.code}") from error
        except urllib.error.URLError as error:
            raise RuntimeError(f"gateway request failed: {error.reason}") from error


def normalize_ble_address(address: str) -> str:
    return address.strip().upper().replace("-", ":")


class SeatAdvertisementForwarder:
    def __init__(self, config: ScannerConfig) -> None:
        self._config = config
        self._client = GatewayClient(config)
        self._deduplicator = DeliveryDeduplicator()
        self._request_cache = DeliveryRequestCache()
        self._tasks: set[asyncio.Task[None]] = set()
        self._sensor_addresses: dict[str, str] = {}

    def handle(self, device: Any, advertisement_data: Any) -> None:
        payload = advertisement_data.manufacturer_data.get(self._config.manufacturer_id)
        if payload is None:
            return

        try:
            advertisement = parse_advertisement(
                bytes(payload),
                self._config.sensor_keys,
                self._config.allow_legacy_unsigned,
            )
        except InvalidSeatAdvertisement as error:
            LOGGER.warning("Ignoring invalid ZeroQ seat advertisement: %s", error)
            return

        observed_address = normalize_ble_address(str(getattr(device, "address", "")))
        expected_address = self._config.sensor_addresses.get(advertisement.sensor_id)
        if expected_address is not None and observed_address != expected_address:
            LOGGER.warning(
                "Ignoring BLE address mismatch: sensorId=%s address=%s",
                advertisement.sensor_id,
                observed_address,
            )
            return
        if observed_address:
            self._sensor_addresses[advertisement.sensor_id] = observed_address

        now = time.monotonic()
        if not self._deduplicator.begin(advertisement.signature, now):
            return

        request = self._request_cache.get_or_create(
            advertisement.signature,
            now,
            lambda: build_gateway_request(
                advertisement,
                getattr(advertisement_data, "rssi", None),
                self._config.place_ids,
                observed_mac_address=observed_address or None,
            ),
        )
        task = asyncio.create_task(
            self._deliver(advertisement.signature, advertisement.sensor_id, request)
        )
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)

    async def drain(self) -> None:
        if self._tasks:
            await asyncio.gather(*self._tasks, return_exceptions=True)

    async def dispatch_pending_commands(self) -> None:
        try:
            commands = await self._client.get_pending_commands()
        except Exception as error:
            LOGGER.warning("Failed to load pending sensor commands: %s", error)
            return

        for command in commands:
            sensor_id = str(command.get("sensorId", ""))
            address = self._sensor_addresses.get(sensor_id)
            if not address:
                continue
            try:
                await self._dispatch_command(address, command)
            except Exception as error:
                LOGGER.warning(
                    "Failed to dispatch BLE command: sensorId=%s commandId=%s error=%s",
                    sensor_id,
                    command.get("cloudCommandId"),
                    error,
                )

    async def _dispatch_command(
        self,
        address: str,
        command: dict[str, object],
    ) -> None:
        from bleak import BleakClient

        command_id = int(command["cloudCommandId"])
        command_type = str(command["commandType"])
        command_payload = str(command.get("commandPayload") or "")
        if "|" in command_payload:
            await self._client.post_command_ack(command_id, "FAILED", "command payload must not contain pipe")
            return
        wire_command = f"{command_id}|{command_type}|{command_payload}".encode("utf-8")
        if len(wire_command) >= 160:
            await self._client.post_command_ack(command_id, "FAILED", "command payload exceeds 159 bytes")
            return

        ack_event = asyncio.Event()
        ack_result: list[tuple[str, str]] = []

        def handle_ack(_sender: Any, value: bytearray) -> None:
            try:
                raw_command_id, status, detail = bytes(value).decode("utf-8").split("|", 2)
                if int(raw_command_id) != command_id:
                    return
                ack_result[:] = [(status, detail)]
                ack_event.set()
            except (UnicodeDecodeError, ValueError):
                return

        async with BleakClient(address, timeout=self._config.request_timeout_seconds) as client:
            await client.start_notify(COMMAND_ACK_UUID, handle_ack)
            await client.write_gatt_char(COMMAND_WRITE_UUID, wire_command, response=True)
            await self._client.mark_dispatched(command_id)
            try:
                await asyncio.wait_for(ack_event.wait(), timeout=self._config.request_timeout_seconds)
            finally:
                await client.stop_notify(COMMAND_ACK_UUID)

        status, detail = ack_result[0]
        if status not in {"ACKNOWLEDGED", "FAILED", "CANCELED"}:
            invalid_status = status
            status = "FAILED"
            detail = f"invalid sensor ack status: {invalid_status}"
        await self._client.post_command_ack(command_id, status, detail)

    async def _deliver(
        self,
        signature: tuple[str, int, bytes],
        sensor_id: str,
        request: dict[str, object],
    ) -> None:
        delivered = False
        try:
            for attempt in range(1, 6):
                try:
                    await self._client.post_advertisement(request)
                    delivered = True
                    LOGGER.info("Forwarded seat advertisement: sensorId=%s sequenceNo=%s", sensor_id, signature[1])
                    break
                except Exception as error:
                    LOGGER.warning(
                        "Failed to forward seat advertisement: sensorId=%s attempt=%s error=%s",
                        sensor_id,
                        attempt,
                        error,
                    )
                    if attempt < 5:
                        await asyncio.sleep(5)
        finally:
            self._deduplicator.finish(signature, time.monotonic(), delivered)
            if delivered:
                self._request_cache.discard(signature)


async def run() -> None:
    from bleak import BleakScanner

    config = ScannerConfig.from_environment()
    forwarder = SeatAdvertisementForwarder(config)
    stop_event = asyncio.Event()
    loop = asyncio.get_running_loop()
    for signal_name in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(signal_name, stop_event.set)
        except NotImplementedError:
            pass

    bluez_options: dict[str, object] = {"filters": {"DuplicateData": True}}
    if config.adapter:
        bluez_options["adapter"] = config.adapter
    scanner = BleakScanner(forwarder.handle, scanning_mode="active", bluez=bluez_options)
    LOGGER.info(
        "Starting ZeroQ BLE scanner: manufacturerId=0x%04X adapter=%s",
        config.manufacturer_id,
        config.adapter or "default",
    )
    async def command_loop() -> None:
        while not stop_event.is_set():
            await forwarder.dispatch_pending_commands()
            try:
                await asyncio.wait_for(stop_event.wait(), timeout=2)
            except asyncio.TimeoutError:
                pass

    command_task = asyncio.create_task(command_loop())
    try:
        async with scanner:
            await stop_event.wait()
    finally:
        command_task.cancel()
        await asyncio.gather(command_task, return_exceptions=True)
    await forwarder.drain()


def main() -> None:
    logging.basicConfig(
        level=os.environ.get("ZEROQ_BLE_LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    try:
        asyncio.run(run())
    except (ValueError, json.JSONDecodeError) as error:
        raise SystemExit(f"Invalid scanner configuration: {error}") from error


if __name__ == "__main__":
    main()
