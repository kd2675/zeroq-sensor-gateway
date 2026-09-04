# ZeroQ 거리 센서 통신 규격

Last updated: 2026-09-04

## 1. 범위

이 규격은 VL53L1X 기반 BLE 거리 센서가 Linux gateway를 거쳐 cloud까지 telemetry를 전달하고, 반대 방향의 명령을 GATT로 받는 계약을 정의한다.

- uplink: 인증된 legacy advertising protocol v3
- downlink: BLE GATT write + notification ACK
- 최종 `occupied` 판정은 센서가 수행하고 gateway는 거리와 품질 정보를 함께 보관한다.
- HTTP 경로와 일부 클래스의 `seat` 명칭은 기존 API 호환을 위해 유지한다.

압력형 센서 예제와 신규 압력형 payload 계약은 현재 기준 범위에 포함하지 않는다.

## 2. 실제 데이터 경로

```text
XIAO + VL53L1X
  -> BLE manufacturer data protocol v3
  -> Python Bleak scanner: tag/address 검증, observedAt 부여
  -> POST /api/zeroq/gateway/v1/local/ingest/seat/advertisement
  -> gateway H2 buffer
  -> cloud-back-server internal API: gateway별 HMAC + body hash 검증
  -> zeroq-back-sensor telemetry/heartbeat
```

명령은 반대 방향으로 전달된다.

```text
zeroq-back-sensor command
  -> gateway command buffer
  -> Python scanner polling
  -> BLE GATT write
  -> sensor notification ACK
  -> gateway local ACK API
  -> cloud ACK sync
```

## 3. BLE envelope

- 파일럿 manufacturer company ID: `0x5A51`
- manufacturer payload: 24바이트
- scanner의 `manufacturer_data[0x5A51]`에는 company ID가 제외된 24바이트만 들어온다.
- flags AD structure 3바이트 + manufacturer AD structure 28바이트 = legacy advertising 31바이트
- local name이나 128-bit service UUID는 같은 advertising packet에 추가하지 않는다.

`0x5A51`은 파일럿용 임시값이다. 판매 제품에서는 Bluetooth SIG가 배정한 Company Identifier 또는 정식 service data 규격을 사용한다.

## 4. Protocol v3 payload

모든 다중 바이트 정수와 인증 태그는 little-endian이다.

| Byte | 길이 | 이름 | 설명 |
|---:|---:|---|---|
| 0 | 1 | version | `0x03` |
| 1 | 1 | flags | 아래 flag 표 |
| 2..9 | 8 | sensorId | ASCII letter/digit/`-`/`_`, 끝쪽 NUL padding, 1~8바이트 |
| 10..11 | 2 | distanceMm | 1~4000mm |
| 12 | 1 | batteryPercent | 0~100 |
| 13..16 | 4 | sequenceNo | unsigned 32-bit monotonic counter |
| 17..19 | 3 | deviceUptimeSeconds | 부팅 후 초의 하위 24비트, Unix 시간이 아님 |
| 20..23 | 4 | authTag | bytes 0..19의 SipHash-2-4 결과 하위 32비트 |

### Flags

| Bit | 값 | 이름 | 의미 |
|---:|---:|---|---|
| 0 | `0x01` | occupied | hold 적용 후 최종 점유 |
| 1 | `0x02` | heartbeat | 주기 상태 보고 |
| 2 | `0x04` | lowBattery | 배터리 20% 이하 |
| 3 | `0x08` | distanceMode | v3 거리 센서는 반드시 1 |
| 4 | `0x10` | sensorFault | 연속 측정 실패 또는 초기화 실패 |
| 5..7 | - | reserved | 반드시 0 |

gateway는 `sensorFault=1`인 패킷을 `confidence=0.0`으로 저장한다. 최신 수신값이 장애이면 이전 정상값을 현재 상태처럼 재사용하지 않는다.

## 5. 센서 인증과 주소 바인딩

- 각 센서에는 서로 다른 16바이트 키를 발급한다.
- 펌웨어 `ZEROQ_SENSOR_KEY_HEX`, scanner `ZEROQ_BLE_SENSOR_KEYS_JSON`, Java gateway `gateway.ble.sensor-keys`가 같은 키를 사용한다.
- scanner는 `ZEROQ_BLE_SENSOR_ADDRESSES_JSON`이 설정되면 sensorId별 기대 BLE 주소도 비교한다.
- Java gateway는 scanner가 보낸 `macAddress`와 기존 sensorId-MAC 바인딩이 바뀌면 수집을 거부한다.
- 예제의 공개 기본 키를 설치 장비에서 사용하지 않는다.

SipHash 태그는 24바이트 legacy advertising 제한에 맞춰 32비트로 잘랐다. 우발적 손상과 단순 ID 사칭을 줄이는 파일럿 장치이지만, 장기 키 노출·재전송 공격·강한 위조 방지·기밀성을 해결하지 않는다. 양산 설계는 연결형 암호화, 키 교체, boot/session nonce와 영속 sequence를 포함해야 한다.

Protocol v1/v2는 `allow-legacy-unsigned=true`로 명시한 마이그레이션 환경에서만 decode한다. 기본값은 `false`다. v1의 reserved flags는 v2 의미로 해석하지 않는다.

## 6. Timestamp와 중복 계약

- sensor uptime은 장치 진단용이며 실제 시각이 아니다.
- scanner가 BLE 수신 순간의 UTC를 offset 없는 `observedAt`에 기록한다.
- Java gateway와 cloud는 이를 UTC `measuredAt`으로 보존한다.
- 같은 `(sensorId, sequenceNo, raw payload)`는 성공 후 scanner에서 10분간 억제한다.
- HTTP 전송은 한 관측 건 안에서 최대 5회, 각 5초 backoff로 재시도한다.
- gateway/database 고유키도 같은 sensor/sequence/measuredAt 중복을 막는다.

advertising에는 과거 이벤트 큐가 없다. gateway가 꺼져 있던 시간의 모든 상태 변화는 복구할 수 없다. 사용량 계산은 heartbeat 3배인 180초를 넘는 공백을 occupied/vacant로 추정하지 않고 coverage에서 제외한다.

## 7. Local HTTP request

```http
POST /api/zeroq/gateway/v1/local/ingest/seat/advertisement
Content-Type: application/json
X-Gateway-Key: <local gateway API key>
```

```json
{
  "payloadHex": "<48 hex characters>",
  "observedAt": "2026-09-04T10:20:30.456",
  "placeId": 101,
  "rssi": -58,
  "macAddress": "AA:BB:CC:DD:EE:FF"
}
```

- `payloadHex`: 필수 protocol v3 payload
- `observedAt`: scanner가 생성한 UTC 관측 시각
- `placeId`: 선택. 고정 매핑이 있으면 scanner가 전달한다.
- `rssi`: 선택
- `macAddress`: 실제 BLE 주소. 주소 매핑과 Java sensorId 바인딩에 사용한다.

local API key는 scanner 환경 파일과 gateway `gateway.node.local-api-key`가 같아야 한다.

## 8. Decoder validation

Python scanner와 Java gateway는 다음을 거부한다.

- 지원하지 않는 길이/version과 v3의 unsigned legacy 길이
- 비어 있거나 허용 문자 밖의 sensorId, 중간 NUL padding
- reserved flag 또는 distanceMode 계약 위반
- battery 100 초과, distance 1~4000mm 범위 위반
- 등록되지 않은 센서 키 또는 authTag 불일치
- 설정된 기대 BLE 주소 또는 기존 Java sensorId-MAC 바인딩 불일치
- 잘못된 hex 문자나 홀수 길이

## 9. GATT command 계약

- service UUID: `5A510000-E8F2-537E-4F6C-D104768A1214`
- write characteristic: `5A510001-E8F2-537E-4F6C-D104768A1214`
- ACK characteristic: `5A510002-E8F2-537E-4F6C-D104768A1214`
- command wire format: `<commandId>|<commandType>|<payload>`
- ACK wire format: `<commandId>|ACKNOWLEDGED|<detail>` 또는 `<commandId>|FAILED|<detail>`
- 최대 characteristic 값: 160바이트

지원 명령:

| commandType | payload | 현재 동작 |
|---|---|---|
| `SET_THRESHOLD` | `enterMm,exitMm` | 40~4000mm, enter < exit 검사 후 RAM 반영 |
| `SET_SAMPLE_INTERVAL` | `200`~`60000` | 측정 간격(ms)을 RAM 반영 |
| `SYNC_TIME` | 빈 값 | gateway 관측 UTC가 기준임을 ACK |
| `REBOOT` | 빈 값 | ACK 후 재부팅 |

설정값은 현재 flash에 영속화하지 않는다. scanner는 `PENDING_DISPATCH`와 `DISPATCHED`를 다시 조회하고 센서가 같은 commandId를 받으면 마지막 ACK를 재전송하므로, ACK 유실 뒤에도 명령을 안전하게 재시도할 수 있다.

## 10. Cloud gateway 인증

gateway가 cloud internal API에 보내는 HMAC payload는 method, path/query, gateway ID, timestamp, nonce, content SHA-256을 포함한다. cloud는 request body를 직접 해시해 header와 대조한 다음 gateway별 secret으로 HMAC을 검증한다. body가 바뀌면 같은 서명을 재사용할 수 없다.

## 11. 구현 위치

- Java decoder/ingest: `src/main/java/com/zeroq/gateway/service/seat/`
- Java BLE 설정: `src/main/java/com/zeroq/gateway/common/config/GatewayBleProperties.java`
- Linux scanner: `tools/ble-seat-scanner/`
- 거리 센서 firmware: `../zeroq-back-sensor/examples/zeroq-spot-sensor-xiao-vl53l1x/`
- cloud HMAC 검증: `../cloud-back-server/src/main/java/cloud/back/server/security/`

## 12. 파일럿 검증 항목

1. 잘못된 키, 바뀐 payload, 다른 BLE 주소를 각각 거부하는지 확인한다.
2. sensor fault 최신값이 현재 점유값으로 보이지 않는지 확인한다.
3. gateway 재시작과 cloud 단절 뒤 H2 buffer가 유실 없이 재전송되는지 확인한다.
4. 명령 write, ACK 유실, 재시도, 동일 commandId 중복 처리를 실기기에서 확인한다.
5. sequence wrap, scanner 재시작, 센서 재부팅의 중복 정책을 장시간 시험한다.
