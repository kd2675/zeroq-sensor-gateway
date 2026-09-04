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

선택:

- `ZEROQ_GATEWAY_LOCAL_URL`: 기본 `http://127.0.0.1:20191`
- `ZEROQ_BLE_MANUFACTURER_ID`: 기본 `0x5A51`, 파일럿 전용
- `ZEROQ_SPOT_PLACE_MAP_JSON`: 예: `{"SPOT-01":101,"SEAT-02":102}`
- `ZEROQ_BLE_SENSOR_ADDRESSES_JSON`: sensorId별 기대 BLE 주소, 예: `{"SPOT-014":"AA:BB:CC:DD:EE:FF"}`
- `ZEROQ_BLE_ALLOW_LEGACY_UNSIGNED`: 기본 `false`; v1/v2 장비의 한시적 마이그레이션에만 `true`
- `ZEROQ_BLE_ADAPTER`: BlueZ adapter 이름, 예: `hci0`
- `ZEROQ_GATEWAY_REQUEST_TIMEOUT_SECONDS`: 기본 5초
- `ZEROQ_BLE_LOG_LEVEL`: 기본 `INFO`

`placeId` 매핑을 생략하면 요청에도 `placeId`가 없다. 서버의 sensor mapping으로 공간을 해석하는 운영이면 생략할 수 있고, 로컬에서 고정할 필요가 있을 때만 설정한다. 이전 변수 `ZEROQ_SEAT_PLACE_MAP_JSON`도 호환용으로 읽지만 신규 배포는 `ZEROQ_SPOT_PLACE_MAP_JSON`을 사용한다. 예제 키는 공개값이므로 실제 장치에는 반드시 독립적인 무작위 키를 발급한다.

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
- command polling은 대기/전송됨 상태를 조회해 BLE write와 notification ACK를 수행한다. ACK가 유실되면 같은 commandId를 재전송한다.
- 종료 신호를 받으면 이미 시작한 HTTP 요청을 기다린 뒤 종료한다.

BLE 자체는 저장 큐가 아니다. 센서가 상태 변화 패킷을 충분히 오래 광고하고 heartbeat를 반복해야 하며, gateway가 꺼져 있던 시간의 모든 과거 상태를 복구할 수는 없다.

## 점검 순서

1. `bluetoothctl show`에서 adapter가 powered 상태인지 확인한다.
2. 스마트폰 또는 `bluetoothctl scan on`으로 ZeroQ 광고가 보이는지 확인한다.
3. scanner 로그에서 CRC/길이 오류가 없는지 확인한다.
4. Java gateway 로그와 H2 buffer에서 telemetry 적재를 확인한다.
5. gateway monitoring API에서 pending/failed/sent 상태를 확인한다.
6. admin에서 임계값 변경 명령을 만들고 scanner의 GATT write, 센서 ACK, cloud ACK까지 확인한다.
