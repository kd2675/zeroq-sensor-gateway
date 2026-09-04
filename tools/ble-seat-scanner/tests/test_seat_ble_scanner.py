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
            "ZEROQ_BLE_SENSOR_ADDRESSES_JSON": '{"SPOT-014":"not-an-address"}',
        }

        with patch.dict(os.environ, environment, clear=True):
            with self.assertRaisesRegex(ValueError, "AA:BB:CC"):
                ScannerConfig.from_environment()


class CommandDispatchTests(unittest.IsolatedAsyncioTestCase):
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
            adapter=None,
        )
        forwarder = SeatAdvertisementForwarder(config)
        forwarder._client = types.SimpleNamespace(  # type: ignore[attr-defined]
            mark_dispatched=AsyncMock(),
            post_command_ack=AsyncMock(),
        )

        class FakeBleakClient:
            written: bytes | None = None

            def __init__(self, _address: str, timeout: float) -> None:
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

        with patch.dict(sys.modules, {"bleak": types.SimpleNamespace(BleakClient=FakeBleakClient)}):
            await forwarder._dispatch_command(  # type: ignore[attr-defined]
                "AA:BB:CC:DD:EE:FF",
                {
                    "cloudCommandId": 42,
                    "commandType": "SET_THRESHOLD",
                    "commandPayload": "700,850",
                },
            )

        self.assertEqual(b"42|SET_THRESHOLD|700,850", FakeBleakClient.written)
        forwarder._client.mark_dispatched.assert_awaited_once_with(42)  # type: ignore[attr-defined]
        forwarder._client.post_command_ack.assert_awaited_once_with(  # type: ignore[attr-defined]
            42,
            "ACKNOWLEDGED",
            "threshold updated",
        )


if __name__ == "__main__":
    unittest.main()
