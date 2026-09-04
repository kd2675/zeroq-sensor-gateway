# ZeroQ BLE Spot Scanner

XIAO 기반 ZeroQ 거리 sensor의 BLE manufacturer data를 받아 로컬 `zeroq-sensor-gateway` HTTP API로 전달하고, 대기 중인 명령을 GATT로 센서에 전달하는 Linux companion process다. Java gateway는 BLE 어댑터를 직접 제어하지 않으므로 실제 장비에서는 이 프로세스가 함께 실행되어야 한다.

디렉터리와 HTTP endpoint의 `seat` 명칭은 기존 API 호환을 위해 유지한다. 신규 장비는 센서별 키로 인증하는 protocol v3를 사용한다.

## 요구 환경

- Linux와 BlueZ 5.55 이상
- BLE를 사용할 수 있는 내장 어댑터 또는 USB 어댑터
- Python 3.10 이상
- 실행 중인 `zeroq-sensor-gateway`
- `GATEWAY_LOCAL_API_KEY`와 Java gateway 설정값의 일치

의존성은 재현성을 위해 `bleak==3.0.2`로 고정했다.

## 설치와 테스트

```bash
cd zeroq-sensor-gateway/tools/ble-seat-scanner
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r requirements.txt
python -m unittest discover -s tests -v
```

## 설정

`.env.example`을 참고해 서비스 매니저의 환경 파일을 만든다. `.env`와 실제 API key는 저장소에 커밋하지 않는다.

필수:

- `GATEWAY_LOCAL_API_KEY`: gateway의 `gateway.node.local-api-key`와 동일한 값
- `ZEROQ_BLE_SENSOR_KEYS_JSON`: sensorId별 32자리 hex 키, 예: `{"SPOT-014":"001122...EEFF"}`

필수:

- `ZEROQ_SPOT_PLACE_MAP_JSON`: 수집할 모든 sensorId의 양의 `placeId`, 예: `{"SPOT-01":101,"SEAT-02":102}`

선택:

- `ZEROQ_GATEWAY_LOCAL_URL`: 기본 `http://127.0.0.1:20191`
- `ZEROQ_BLE_MANUFACTURER_ID`: 기본 `0x5A51`, 파일럿 전용
- `ZEROQ_BLE_SENSOR_ADDRESSES_JSON`: sensorId별 기대 BLE 주소, 예: `{"SPOT-014":"AA:BB:CC:DD:EE:FF"}`
- `ZEROQ_BLE_ALLOW_LEGACY_UNSIGNED`: 기본 `false`; v1/v2 장비의 한시적 마이그레이션에만 `true`
- `ZEROQ_BLE_ADAPTER`: BlueZ adapter 이름, 예: `hci0`
- `ZEROQ_GATEWAY_REQUEST_TIMEOUT_SECONDS`: 기본 5초
- `ZEROQ_BLE_LOG_LEVEL`: 기본 `INFO`

`placeId`는 사용량 이력의 공간 귀속값이므로 생략할 수 없다. 인증 키나 기대 BLE 주소로 설정한 sensorId가 매핑에 없으면 scanner가 시작을 거부하고, legacy 광고에서 처음 발견한 미매핑 sensorId도 전송하지 않는다. 이전 변수 `ZEROQ_SEAT_PLACE_MAP_JSON`도 호환용으로 읽지만 신규 배포는 `ZEROQ_SPOT_PLACE_MAP_JSON`을 사용한다. 예제 키는 공개값이므로 실제 장치에는 반드시 독립적인 무작위 키를 발급한다.

매핑 값은 Admin에 등록된 해당 gateway의 공간 ID와 같아야 한다. 현재 cloud 배정은 이력 구간이 아니라 현재값만 보관하므로, 센서를 옮길 때는 기존 scanner를 먼저 중지하고 gateway의 telemetry/heartbeat 대기 큐가 0인지 확인한다. 그다음 Admin 설치 정보를 변경해 cloud 배정 projection을 갱신하고, 새 scanner 매핑으로 프로세스를 재시작한다. 이전 공간 데이터가 큐에 남은 상태에서 먼저 재배정하면 그 데이터는 cloud에서 거부된다.

protocol v3에서는 scanner가 Linux/BlueZ에서 관측한 `AA:BB:CC:DD:EE:FF` 형식의 BLE 주소를 local API에 반드시 전달한다. macOS에서 노출되는 장치 UUID는 이 계약의 주소로 사용할 수 없다. `ZEROQ_BLE_SENSOR_ADDRESSES_JSON`을 설정하면 전송 전에 기대 주소를 추가 검증하지만, BLE 주소 자체는 위조 가능하므로 키 기반 태그를 대체하지 않는다. 태그 역시 캡처 패킷 replay를 막지 못한다.

## 실행

먼저 Java gateway를 실행한다.

```bash
./gradlew :zeroq-sensor-gateway:bootRun --args='--spring.profiles.active=local'
```

그 다음 scanner 환경을 적용하고 실행한다.

```bash
cd zeroq-sensor-gateway/tools/ble-seat-scanner
. .venv/bin/activate
python seat_ble_scanner.py
```

정상 로그에는 `sensorId`와 `sequenceNo`만 남고 API key나 전체 payload는 남기지 않는다.

## systemd

`zeroq-ble-spot-scanner.service.example`의 사용자와 절대경로를 실제 설치 경로에 맞춘 후 `/etc/systemd/system/zeroq-ble-spot-scanner.service`로 배치한다. secret은 `/etc/zeroq/ble-spot-scanner.env`처럼 저장소 밖의 root 전용 파일에 둔다.

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now zeroq-ble-spot-scanner
journalctl -u zeroq-ble-spot-scanner -f
```

서비스 사용자는 BlueZ D-Bus와 해당 BLE adapter를 사용할 권한이 있어야 한다. 배포판별 정책이 다르므로 `bluetoothctl show`와 같은 사용자로 먼저 scan 가능 여부를 확인한다.

## 실패 처리

- 잘못된 길이, version, 인증 태그, BLE 주소, battery, distance는 HTTP 전송 전에 버린다.
- 같은 `(sensorId, sequenceNo, payload)`는 성공 후 10분간 중복 전송하지 않는다.
- HTTP 실패는 같은 관측 건 안에서 최대 5회, 각 5초 간격으로 재시도한다.
- command polling은 대기/전송됨 상태를 조회해 BLE write와 notification ACK를 수행한다. 스캔 callback에서 받은 `BLEDevice`를 연결 대상으로 재사용하고 같은 BlueZ adapter를 지정하므로, 프로세스 시작 뒤 해당 센서 광고를 한 번 이상 수신해야 명령을 전달한다. ACK가 유실되면 같은 commandId를 재전송하며, 해당 센서에서 오류가 난 polling 회차에는 뒤 명령을 보류한다. 센서 재부팅 뒤까지 exactly-once 실행을 보장하지는 않는다.
- firmware wire contract가 unsigned 32-bit command ID를 사용하므로 그 범위를 넘는 cloud command는 센서에 쓰지 않고 실패 ACK로 처리한다.
- 종료 신호를 받으면 이미 시작한 HTTP 요청을 기다린 뒤 종료한다.

BLE 자체는 저장 큐가 아니다. 센서가 상태 변화 패킷을 충분히 오래 광고하고 heartbeat를 반복해야 하며, gateway가 꺼져 있던 시간의 모든 과거 상태를 복구할 수는 없다. GATT command에는 현재 pairing 강제나 별도 command MAC이 없으므로 접근이 통제된 파일럿에서만 사용한다.

## 점검 순서

1. `bluetoothctl show`에서 adapter가 powered 상태인지 확인한다.
2. 스마트폰 또는 `bluetoothctl scan on`으로 ZeroQ 광고가 보이는지 확인한다.
3. scanner 로그에서 CRC/길이 오류가 없는지 확인한다.
4. Java gateway 로그와 H2 buffer에서 telemetry 적재를 확인한다.
5. gateway monitoring API에서 pending/failed/sent 상태를 확인한다.
6. admin에서 임계값 변경 명령을 만들고 scanner의 GATT write, 센서 ACK, cloud ACK까지 확인한다.
