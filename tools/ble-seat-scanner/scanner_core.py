from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Callable, Mapping


LEGACY_PAYLOAD_BYTES = 22
AUTHENTICATED_PAYLOAD_BYTES = 24
SUPPORTED_PROTOCOL_VERSIONS = {1, 2, 3}
FLAG_DISTANCE_MODE = 0x08
FLAG_SENSOR_FAULT = 0x10
ALLOWED_FLAGS = 0x1F
MAX_DISTANCE_MM = 4000


class InvalidSeatAdvertisement(ValueError):
    pass


@dataclass(frozen=True)
class SeatAdvertisement:
    payload: bytes
    protocol_version: int
    sensor_id: str
    sequence_no: int
    device_uptime_seconds: int | None
    distance_mode: bool
    sensor_fault: bool
    distance_mm: int | None

    @property
    def signature(self) -> tuple[str, int, bytes]:
        return self.sensor_id, self.sequence_no, self.payload


def crc8(payload: bytes) -> int:
    value = 0
    for byte in payload:
        value ^= byte
        for _ in range(8):
            value = ((value << 1) ^ 0x07) & 0xFF if value & 0x80 else (value << 1) & 0xFF
    return value


def siphash24(key: bytes, message: bytes) -> int:
    if len(key) != 16:
        raise ValueError("SipHash key must be 16 bytes")

    mask = (1 << 64) - 1
    k0 = int.from_bytes(key[:8], "little")
    k1 = int.from_bytes(key[8:], "little")
    state = [
        0x736F6D6570736575 ^ k0,
        0x646F72616E646F6D ^ k1,
        0x6C7967656E657261 ^ k0,
        0x7465646279746573 ^ k1,
    ]

    def rotate_left(value: int, bits: int) -> int:
        return ((value << bits) | (value >> (64 - bits))) & mask

    def rounds(count: int) -> None:
        for _ in range(count):
            state[0] = (state[0] + state[1]) & mask
            state[1] = rotate_left(state[1], 13) ^ state[0]
            state[0] = rotate_left(state[0], 32)
            state[2] = (state[2] + state[3]) & mask
            state[3] = rotate_left(state[3], 16) ^ state[2]
            state[0] = (state[0] + state[3]) & mask
            state[3] = rotate_left(state[3], 21) ^ state[0]
            state[2] = (state[2] + state[1]) & mask
            state[1] = rotate_left(state[1], 17) ^ state[2]
            state[2] = rotate_left(state[2], 32)

    full_length = len(message) - len(message) % 8
    for offset in range(0, full_length, 8):
        block = int.from_bytes(message[offset : offset + 8], "little")
        state[3] ^= block
        rounds(2)
        state[0] ^= block

    tail = len(message) << 56
    for index, value in enumerate(message[full_length:]):
        tail |= value << (index * 8)
    state[3] ^= tail
    rounds(2)
    state[0] ^= tail
    state[2] ^= 0xFF
    rounds(4)
    return state[0] ^ state[1] ^ state[2] ^ state[3]


def parse_advertisement(
    payload: bytes,
    sensor_keys: Mapping[str, bytes] | None = None,
    allow_legacy_unsigned: bool = False,
) -> SeatAdvertisement:
    if len(payload) not in {LEGACY_PAYLOAD_BYTES, AUTHENTICATED_PAYLOAD_BYTES}:
        raise InvalidSeatAdvertisement(
            f"seat payload must be 22 or 24 bytes, got {len(payload)}"
        )

    protocol_version = payload[0]
    if protocol_version not in SUPPORTED_PROTOCOL_VERSIONS:
        raise InvalidSeatAdvertisement(f"unsupported protocol version: {protocol_version}")
    if protocol_version == 3 and len(payload) != AUTHENTICATED_PAYLOAD_BYTES:
        raise InvalidSeatAdvertisement("protocol v3 payload must be 24 bytes")
    if protocol_version != 3 and len(payload) != LEGACY_PAYLOAD_BYTES:
        raise InvalidSeatAdvertisement("legacy payload must be 22 bytes")
    if protocol_version != 3 and not allow_legacy_unsigned:
        raise InvalidSeatAdvertisement("unsigned BLE protocol is disabled")
    if protocol_version == 2 and crc8(payload[:-1]) != payload[-1]:
        raise InvalidSeatAdvertisement("protocol v2 checksum mismatch")

    raw_sensor_id = payload[2:10]
    sensor_id_bytes = raw_sensor_id.rstrip(b"\x00")
    if b"\x00" in sensor_id_bytes:
        raise InvalidSeatAdvertisement("sensor id has data after null padding")
    try:
        sensor_id = sensor_id_bytes.decode("ascii")
    except UnicodeDecodeError as error:
        raise InvalidSeatAdvertisement("sensor id must be ASCII") from error
    if not sensor_id:
        raise InvalidSeatAdvertisement("sensor id must not be blank")
    if not all(character.isalnum() or character in "-_" for character in sensor_id):
        raise InvalidSeatAdvertisement(
            "sensor id must use ASCII letters, digits, '-' or '_'"
        )
    if protocol_version == 3:
        key = (sensor_keys or {}).get(sensor_id)
        if key is None:
            raise InvalidSeatAdvertisement(
                f"no BLE authentication key configured for sensor: {sensor_id}"
            )
        expected_tag = (siphash24(key, payload[:20]) & 0xFFFFFFFF).to_bytes(4, "little")
        if expected_tag != payload[20:24]:
            raise InvalidSeatAdvertisement("protocol v3 authentication tag mismatch")
    if payload[12] > 100:
        raise InvalidSeatAdvertisement("battery percent must be between 0 and 100")

    flags = payload[1]
    if protocol_version >= 2 and flags & ~ALLOWED_FLAGS:
        raise InvalidSeatAdvertisement("reserved flag bits must be zero")
    distance_mode = protocol_version >= 2 and bool(flags & FLAG_DISTANCE_MODE)
    if protocol_version == 3 and not distance_mode:
        raise InvalidSeatAdvertisement("protocol v3 requires distance mode")
    sensor_fault = protocol_version >= 2 and bool(flags & FLAG_SENSOR_FAULT)
    distance_mm = int.from_bytes(payload[10:12], byteorder="little") if distance_mode else None
    if distance_mm is not None and not 1 <= distance_mm <= MAX_DISTANCE_MM:
        raise InvalidSeatAdvertisement(
            f"distance must be between 1 and {MAX_DISTANCE_MM} mm"
        )

    sequence_no = int.from_bytes(payload[13:17], byteorder="little", signed=False)
    time_value = int.from_bytes(
        payload[17:20] if protocol_version == 3 else payload[17:21],
        byteorder="little",
        signed=False,
    )
    return SeatAdvertisement(
        payload=payload,
        protocol_version=protocol_version,
        sensor_id=sensor_id,
        sequence_no=sequence_no,
        device_uptime_seconds=time_value if protocol_version >= 2 else None,
        distance_mode=distance_mode,
        sensor_fault=sensor_fault,
        distance_mm=distance_mm,
    )


def build_gateway_request(
    advertisement: SeatAdvertisement,
    rssi: int | None,
    place_ids: Mapping[str, int],
    observed_at: datetime | None = None,
    observed_mac_address: str | None = None,
) -> dict[str, object]:
    captured_at = observed_at or datetime.now(timezone.utc)
    captured_at_utc = captured_at.astimezone(timezone.utc).replace(tzinfo=None)
    request: dict[str, object] = {
        "payloadHex": advertisement.payload.hex().upper(),
        "observedAt": captured_at_utc.isoformat(timespec="milliseconds"),
    }
    if rssi is not None:
        request["rssi"] = rssi
    if advertisement.sensor_id in place_ids:
        request["placeId"] = place_ids[advertisement.sensor_id]
    if observed_mac_address:
        request["macAddress"] = observed_mac_address
    return request


class DeliveryDeduplicator:
    def __init__(self, retention_seconds: float = 600.0, failure_retry_seconds: float = 5.0) -> None:
        if retention_seconds <= 0:
            raise ValueError("retention_seconds must be positive")
        if failure_retry_seconds <= 0:
            raise ValueError("failure_retry_seconds must be positive")
        self._retention_seconds = retention_seconds
        self._failure_retry_seconds = failure_retry_seconds
        self._in_flight: set[tuple[str, int, bytes]] = set()
        self._delivered_at: dict[tuple[str, int, bytes], float] = {}
        self._retry_after: dict[tuple[str, int, bytes], float] = {}

    def begin(self, signature: tuple[str, int, bytes], now: float) -> bool:
        self._expire(now)
        if signature in self._in_flight or signature in self._delivered_at:
            return False
        retry_after = self._retry_after.get(signature)
        if retry_after is not None and now < retry_after:
            return False
        self._retry_after.pop(signature, None)
        self._in_flight.add(signature)
        return True

    def finish(self, signature: tuple[str, int, bytes], now: float, delivered: bool) -> None:
        self._in_flight.discard(signature)
        if delivered:
            self._delivered_at[signature] = now
            self._retry_after.pop(signature, None)
        else:
            self._retry_after[signature] = now + self._failure_retry_seconds

    def _expire(self, now: float) -> None:
        expired = [
            signature
            for signature, delivered_at in self._delivered_at.items()
            if now - delivered_at >= self._retention_seconds
        ]
        for signature in expired:
            del self._delivered_at[signature]

        expired_retries = [
            signature
            for signature, retry_after in self._retry_after.items()
            if now - retry_after >= self._retention_seconds
        ]
        for signature in expired_retries:
            del self._retry_after[signature]


class DeliveryRequestCache:
    def __init__(self, retention_seconds: float = 600.0) -> None:
        if retention_seconds <= 0:
            raise ValueError("retention_seconds must be positive")
        self._retention_seconds = retention_seconds
        self._entries: dict[
            tuple[str, int, bytes], tuple[dict[str, object], float]
        ] = {}

    def get_or_create(
        self,
        signature: tuple[str, int, bytes],
        now: float,
        factory: Callable[[], dict[str, object]],
    ) -> dict[str, object]:
        self._expire(now)
        existing = self._entries.get(signature)
        if existing is not None:
            return existing[0]

        request = factory()
        self._entries[signature] = (request, now)
        return request

    def discard(self, signature: tuple[str, int, bytes]) -> None:
        self._entries.pop(signature, None)

    def _expire(self, now: float) -> None:
        expired = [
            signature
            for signature, (_, created_at) in self._entries.items()
            if now - created_at >= self._retention_seconds
        ]
        for signature in expired:
            del self._entries[signature]
