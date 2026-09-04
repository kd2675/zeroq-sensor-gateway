from __future__ import annotations

import os
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from seat_ble_scanner import ScannerConfig, SeatAdvertisementForwarder  # noqa: E402


class ScannerConfigTests(unittest.TestCase):
    def test_from_environment_sensorKeyAndAddress_areNormalized(self) -> None:
        environment = {
            "GATEWAY_LOCAL_API_KEY": "local-key",
            "ZEROQ_SPOT_PLACE_MAP_JSON": '{"SPOT-014":101}',
            "ZEROQ_BLE_SENSOR_KEYS_JSON": '{"SPOT-014":"000102030405060708090a0b0c0d0e0f"}',
            "ZEROQ_BLE_SENSOR_ADDRESSES_JSON": '{"SPOT-014":"aa-bb-cc-dd-ee-ff"}',
        }

        with patch.dict(os.environ, environment, clear=True):
            config = ScannerConfig.from_environment()

        self.assertEqual(bytes(range(16)), config.sensor_keys["SPOT-014"])
        self.assertEqual("AA:BB:CC:DD:EE:FF", config.sensor_addresses["SPOT-014"])

    def test_from_environment_invalidAddress_rejectsConfiguration(self) -> None:
        environment = {
            "GATEWAY_LOCAL_API_KEY": "local-key",
            "ZEROQ_SPOT_PLACE_MAP_JSON": '{"SPOT-014":101}',
            "ZEROQ_BLE_SENSOR_ADDRESSES_JSON": '{"SPOT-014":"not-an-address"}',
        }

        with patch.dict(os.environ, environment, clear=True):
            with self.assertRaisesRegex(ValueError, "AA:BB:CC"):
                ScannerConfig.from_environment()

    def test_from_environment_sensorKeyWithoutPlaceId_rejectsConfiguration(self) -> None:
        environment = {
            "GATEWAY_LOCAL_API_KEY": "local-key",
            "ZEROQ_BLE_SENSOR_KEYS_JSON": '{"SPOT-014":"000102030405060708090a0b0c0d0e0f"}',
        }

        with patch.dict(os.environ, environment, clear=True):
            with self.assertRaisesRegex(ValueError, "missing sensorIds: SPOT-014"):
                ScannerConfig.from_environment()


class AdvertisementHandlingTests(unittest.TestCase):
    def test_handle_withoutPlaceMapping_doesNotCallGateway(self) -> None:
        config = ScannerConfig(
            gateway_base_url="http://127.0.0.1:20191",
            gateway_api_key="local-key",
            manufacturer_id=0x5A51,
            place_ids={},
            sensor_keys={},
            sensor_addresses={},
            allow_legacy_unsigned=True,
            request_timeout_seconds=1,
            adapter=None,
        )
        forwarder = SeatAdvertisementForwarder(config)
        forwarder._client = types.SimpleNamespace(post_advertisement=AsyncMock())  # type: ignore[attr-defined]
        advertisement = types.SimpleNamespace(
            protocol_version=2,
            sensor_id="SPOT-014",
            signature=("SPOT-014", 1, b"payload"),
        )

        with patch("seat_ble_scanner.parse_advertisement", return_value=advertisement):
            forwarder.handle(
                types.SimpleNamespace(address="AA:BB:CC:DD:EE:FF"),
                types.SimpleNamespace(manufacturer_data={0x5A51: b"payload"}, rssi=-55),
            )

        forwarder._client.post_advertisement.assert_not_awaited()  # type: ignore[attr-defined]

    def test_handle_protocolVersionThreeWithoutMac_doesNotCallGateway(self) -> None:
        config = ScannerConfig(
            gateway_base_url="http://127.0.0.1:20191",
            gateway_api_key="local-key",
            manufacturer_id=0x5A51,
            place_ids={"SPOT-014": 101},
            sensor_keys={},
            sensor_addresses={},
            allow_legacy_unsigned=False,
            request_timeout_seconds=1,
            adapter=None,
        )
        forwarder = SeatAdvertisementForwarder(config)
        forwarder._client = types.SimpleNamespace(post_advertisement=AsyncMock())  # type: ignore[attr-defined]
        advertisement = types.SimpleNamespace(
            protocol_version=3,
            sensor_id="SPOT-014",
            signature=("SPOT-014", 1, b"payload"),
        )

        with patch("seat_ble_scanner.parse_advertisement", return_value=advertisement):
            forwarder.handle(
                types.SimpleNamespace(address="macOS-device-uuid"),
                types.SimpleNamespace(manufacturer_data={0x5A51: b"payload"}, rssi=-55),
            )

        forwarder._client.post_advertisement.assert_not_awaited()  # type: ignore[attr-defined]
        self.assertEqual(set(), forwarder._tasks)  # type: ignore[attr-defined]


class CommandDispatchTests(unittest.IsolatedAsyncioTestCase):
    def test_forwarder_expectedAddress_doesNotUseAddressStringAsConnectionTarget(self) -> None:
        config = ScannerConfig(
            gateway_base_url="http://127.0.0.1:20191",
            gateway_api_key="local-key",
            manufacturer_id=0x5A51,
            place_ids={"SPOT-014": 101},
            sensor_keys={},
            sensor_addresses={"SPOT-014": "AA:BB:CC:DD:EE:FF"},
            allow_legacy_unsigned=False,
            request_timeout_seconds=1,
            adapter="hci1",
        )

        forwarder = SeatAdvertisementForwarder(config)

        self.assertEqual({}, forwarder._sensor_targets)  # type: ignore[attr-defined]

    async def test_dispatchPendingCommands_firstSensorFailure_defersLaterCommandForSameSensor(self) -> None:
        config = ScannerConfig(
            gateway_base_url="http://127.0.0.1:20191",
            gateway_api_key="local-key",
            manufacturer_id=0x5A51,
            place_ids={},
            sensor_keys={},
            sensor_addresses={},
            allow_legacy_unsigned=False,
            request_timeout_seconds=1,
            adapter=None,
        )
        forwarder = SeatAdvertisementForwarder(config)
        forwarder._sensor_targets["SPOT-014"] = "AA:BB:CC:DD:EE:FF"  # type: ignore[attr-defined]
        forwarder._client = types.SimpleNamespace(  # type: ignore[attr-defined]
            get_pending_commands=AsyncMock(return_value=[
                {"sensorId": "SPOT-014", "cloudCommandId": 1},
                {"sensorId": "SPOT-014", "cloudCommandId": 2},
            ])
        )
        dispatch = AsyncMock(side_effect=RuntimeError("local ack store unavailable"))
        forwarder._dispatch_command = dispatch  # type: ignore[method-assign]

        await forwarder.dispatch_pending_commands()

        dispatch.assert_awaited_once()

    async def test_dispatchCommand_writesGattAndForwardsAcknowledgement(self) -> None:
        config = ScannerConfig(
            gateway_base_url="http://127.0.0.1:20191",
            gateway_api_key="local-key",
            manufacturer_id=0x5A51,
            place_ids={},
            sensor_keys={},
            sensor_addresses={},
            allow_legacy_unsigned=False,
            request_timeout_seconds=1,
            adapter="hci1",
        )
        forwarder = SeatAdvertisementForwarder(config)
        forwarder._client = types.SimpleNamespace(  # type: ignore[attr-defined]
            mark_dispatched=AsyncMock(),
            post_command_ack=AsyncMock(),
        )

        class FakeBleakClient:
            written: bytes | None = None
            received_target: object | None = None
            received_bluez: object | None = None

            def __init__(self, target: object, timeout: float, bluez: object) -> None:
                FakeBleakClient.received_target = target
                FakeBleakClient.received_bluez = bluez
                self.timeout = timeout
                self.callback = None

            async def __aenter__(self):
                return self

            async def __aexit__(self, _exc_type, _exc, _traceback):
                return None

            async def start_notify(self, _uuid: str, callback) -> None:
                self.callback = callback

            async def write_gatt_char(self, _uuid: str, value: bytes, response: bool) -> None:
                FakeBleakClient.written = value
                assert response is True
                self.callback(None, bytearray(b"42|ACKNOWLEDGED|threshold updated"))

            async def stop_notify(self, _uuid: str) -> None:
                return None

        ble_device = types.SimpleNamespace(address="AA:BB:CC:DD:EE:FF")
        with patch.dict(sys.modules, {"bleak": types.SimpleNamespace(BleakClient=FakeBleakClient)}):
            await forwarder._dispatch_command(  # type: ignore[attr-defined]
                ble_device,
                {
                    "cloudCommandId": 42,
                    "commandType": "SET_THRESHOLD",
                    "commandPayload": "700,850",
                },
            )

        self.assertEqual(b"42|SET_THRESHOLD|700,850", FakeBleakClient.written)
        self.assertIs(ble_device, FakeBleakClient.received_target)
        self.assertEqual({"adapter": "hci1"}, FakeBleakClient.received_bluez)
        forwarder._client.mark_dispatched.assert_awaited_once_with(42)  # type: ignore[attr-defined]
        forwarder._client.post_command_ack.assert_awaited_once_with(  # type: ignore[attr-defined]
            42,
            "ACKNOWLEDGED",
            "threshold updated",
        )

    async def test_dispatchCommand_commandIdOutsideUint32_rejectsBeforeGattWrite(self) -> None:
        config = ScannerConfig(
            gateway_base_url="http://127.0.0.1:20191",
            gateway_api_key="local-key",
            manufacturer_id=0x5A51,
            place_ids={},
            sensor_keys={},
            sensor_addresses={},
            allow_legacy_unsigned=False,
            request_timeout_seconds=1,
            adapter=None,
        )
        forwarder = SeatAdvertisementForwarder(config)
        forwarder._client = types.SimpleNamespace(  # type: ignore[attr-defined]
            mark_dispatched=AsyncMock(),
            post_command_ack=AsyncMock(),
        )

        await forwarder._dispatch_command(  # type: ignore[attr-defined]
            "AA:BB:CC:DD:EE:FF",
            {
                "cloudCommandId": 0x100000000,
                "commandType": "REBOOT",
                "commandPayload": "",
            },
        )

        forwarder._client.mark_dispatched.assert_not_awaited()  # type: ignore[attr-defined]
        forwarder._client.post_command_ack.assert_awaited_once_with(  # type: ignore[attr-defined]
            0x100000000,
            "FAILED",
            "command id exceeds sensor uint32 range",
        )


if __name__ == "__main__":
    unittest.main()
