from __future__ import annotations

import sys
import unittest
from datetime import datetime, timezone
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scanner_core import (  # noqa: E402
    DeliveryDeduplicator,
    DeliveryRequestCache,
    InvalidSeatAdvertisement,
    build_gateway_request,
    crc8,
    parse_advertisement,
    siphash24,
)


class ScannerCoreTests(unittest.TestCase):
    def test_parse_advertisement_protocol_version_two_decodes_identity(self) -> None:
        payload = version_two_payload()

        advertisement = parse_legacy(payload)

        self.assertEqual(
            (2, "SEAT-014", 1200, 3600, False, False, None),
            (
                advertisement.protocol_version,
                advertisement.sensor_id,
                advertisement.sequence_no,
                advertisement.device_uptime_seconds,
                advertisement.distance_mode,
                advertisement.sensor_fault,
                advertisement.distance_mm,
            ),
        )

    def test_parse_advertisement_distance_mode_decodes_millimeters(self) -> None:
        payload = version_two_payload(flags=0x19, distance_mm=742)

        advertisement = parse_legacy(payload)

        self.assertEqual((True, True, 742), (
            advertisement.distance_mode,
            advertisement.sensor_fault,
            advertisement.distance_mm,
        ))

    def test_parse_advertisement_distance_mode_rejects_out_of_range_value(self) -> None:
        payload = version_two_payload(flags=0x09, distance_mm=4001)

        with self.assertRaisesRegex(InvalidSeatAdvertisement, "distance"):
            parse_legacy(payload)

    def test_parse_advertisement_invalid_checksum_rejects_payload(self) -> None:
        payload = bytearray(version_two_payload())
        payload[-1] ^= 0x01

        with self.assertRaisesRegex(InvalidSeatAdvertisement, "checksum"):
            parse_legacy(bytes(payload))

    def test_parse_advertisement_rejects_data_after_sensor_id_padding(self) -> None:
        payload = bytearray(version_two_payload())
        payload[4] = 0
        payload[-1] = crc8(payload[:-1])

        with self.assertRaisesRegex(InvalidSeatAdvertisement, "padding"):
            parse_legacy(bytes(payload))

    def test_parse_advertisement_protocol_version_one_ignores_v2_reserved_flags(self) -> None:
        payload = bytearray(version_two_payload(flags=0x1B))
        payload[0] = 1

        advertisement = parse_legacy(bytes(payload))

        self.assertEqual((False, False, None), (
            advertisement.distance_mode,
            advertisement.sensor_fault,
            advertisement.distance_mm,
        ))

    def test_parse_advertisement_protocol_version_three_validates_authentication_tag(self) -> None:
        key = bytes(range(16))
        payload = version_three_payload(key)

        advertisement = parse_advertisement(payload, {"SPOT-014": key})

        self.assertEqual((3, "SPOT-014", 742), (
            advertisement.protocol_version,
            advertisement.sensor_id,
            advertisement.distance_mm,
        ))

    def test_parse_advertisement_protocol_version_three_rejects_reserved_flags(self) -> None:
        key = bytes(range(16))
        payload = bytearray(version_three_payload(key))
        payload[1] |= 0x80
        payload[20:24] = (siphash24(key, payload[:20]) & 0xFFFFFFFF).to_bytes(4, "little")

        with self.assertRaisesRegex(InvalidSeatAdvertisement, "reserved flag"):
            parse_advertisement(bytes(payload), {"SPOT-014": key})

    def test_parse_advertisement_protocol_version_three_requires_distance_mode(self) -> None:
        key = bytes(range(16))
        payload = bytearray(version_three_payload(key))
        payload[1] &= ~0x08
        payload[20:24] = (siphash24(key, payload[:20]) & 0xFFFFFFFF).to_bytes(4, "little")

        with self.assertRaisesRegex(InvalidSeatAdvertisement, "requires distance mode"):
            parse_advertisement(bytes(payload), {"SPOT-014": key})

    def test_siphash24_matches_reference_vector(self) -> None:
        key = bytes(range(16))
        message = bytes(range(15))

        self.assertEqual(0xA129CA6149BE45E5, siphash24(key, message))

    def test_build_gateway_request_formats_utc_without_offset(self) -> None:
        advertisement = parse_legacy(version_two_payload())
        observed_at = datetime(2026, 9, 3, 10, 20, 30, 456000, tzinfo=timezone.utc)

        request = build_gateway_request(advertisement, -58, {"SEAT-014": 101}, observed_at)

        self.assertEqual(
            {
                "payloadHex": version_two_payload().hex().upper(),
                "observedAt": "2026-09-03T10:20:30.456",
                "rssi": -58,
                "placeId": 101,
            },
            request,
        )

    def test_delivery_deduplicator_failed_delivery_observes_retry_delay(self) -> None:
        signature = parse_legacy(version_two_payload()).signature
        deduplicator = DeliveryDeduplicator(retention_seconds=10, failure_retry_seconds=5)

        first = deduplicator.begin(signature, 1.0)
        deduplicator.finish(signature, 2.0, delivered=False)
        immediate_retry = deduplicator.begin(signature, 3.0)
        delayed_retry = deduplicator.begin(signature, 7.0)

        self.assertEqual((True, False, True), (first, immediate_retry, delayed_retry))

    def test_delivery_deduplicator_delivered_packet_waits_for_expiration(self) -> None:
        signature = parse_legacy(version_two_payload()).signature
        deduplicator = DeliveryDeduplicator(retention_seconds=10)

        first = deduplicator.begin(signature, 1.0)
        deduplicator.finish(signature, 2.0, delivered=True)
        duplicate = deduplicator.begin(signature, 5.0)
        after_expiration = deduplicator.begin(signature, 12.0)

        self.assertEqual((True, False, True), (first, duplicate, after_expiration))

    def test_delivery_request_cache_reuses_original_request_for_retry(self) -> None:
        signature = parse_legacy(version_two_payload()).signature
        cache = DeliveryRequestCache(retention_seconds=10)
        created = 0

        def factory() -> dict[str, object]:
            nonlocal created
            created += 1
            return {"observedAt": f"request-{created}"}

        first = cache.get_or_create(signature, 1.0, factory)
        retry = cache.get_or_create(signature, 5.0, factory)
        expired = cache.get_or_create(signature, 11.0, factory)

        self.assertIs(first, retry)
        self.assertEqual("request-2", expired["observedAt"])


def version_two_payload(flags: int = 3, distance_mm: int | None = None) -> bytes:
    payload = bytearray(22)
    payload[0] = 2
    payload[1] = flags
    payload[2:10] = b"SEAT-014"
    if distance_mm is None:
        payload[10] = 155
        payload[11] = 90
    else:
        payload[10:12] = distance_mm.to_bytes(2, byteorder="little")
    payload[12] = 80
    payload[13:17] = (1200).to_bytes(4, byteorder="little")
    payload[17:21] = (3600).to_bytes(4, byteorder="little")
    payload[21] = crc8(payload[:-1])
    return bytes(payload)


def version_three_payload(key: bytes) -> bytes:
    payload = bytearray(24)
    payload[0] = 3
    payload[1] = 0x0B
    payload[2:10] = b"SPOT-014"
    payload[10:12] = (742).to_bytes(2, byteorder="little")
    payload[12] = 80
    payload[13:17] = (1200).to_bytes(4, byteorder="little")
    payload[17:20] = (3600).to_bytes(3, byteorder="little")
    payload[20:24] = (siphash24(key, payload[:20]) & 0xFFFFFFFF).to_bytes(4, "little")
    return bytes(payload)


def parse_legacy(payload: bytes):
    return parse_advertisement(payload, allow_legacy_unsigned=True)


if __name__ == "__main__":
    unittest.main()
