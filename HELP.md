# zeroq-sensor-gateway Help

엣지 게이트웨이 개발 시 자주 쓰는 명령과 설정만 정리합니다.

## Build / Test

```bash
./gradlew :zeroq-sensor-gateway:compileJava
./gradlew :zeroq-sensor-gateway:test
```

## Run

```bash
./gradlew :zeroq-sensor-gateway:bootRun
./gradlew :zeroq-sensor-gateway:bootRun --args='--spring.profiles.active=local'
./gradlew :zeroq-sensor-gateway:bootRun --args='--spring.profiles.active=dev'
./gradlew :zeroq-sensor-gateway:bootRun --args='--spring.profiles.active=prod'
./gradlew :zeroq-sensor-gateway:bootRun --args='--spring.profiles.active=test'
```

## Ports

- `local/dev`: `20191`
- `prod`: `10191`
- `test`: `30191`

## BLE Spot Scanner

```bash
cd zeroq-sensor-gateway/tools/ble-seat-scanner
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r requirements.txt
python -m unittest discover -s tests -v
python seat_ble_scanner.py
```

- Python 3.10 이상, BlueZ 5.55 이상
- 필수 환경 변수: `GATEWAY_LOCAL_API_KEY`, `ZEROQ_BLE_SENSOR_KEYS_JSON`
- 권장 주소 바인딩: `ZEROQ_BLE_SENSOR_ADDRESSES_JSON`
- 상세 설정: `tools/ble-seat-scanner/README.md`

## Main Paths

- ingest: `/api/zeroq/gateway/v1/local/ingest`
- commands: `/api/zeroq/gateway/v1/local/commands`
- monitoring: `/api/zeroq/gateway/v1/monitoring`

## Key Config

- `GATEWAY_NODE_ID`
- `GATEWAY_LOCAL_API_KEY`
- `GATEWAY_CLOUD_BASE_URL`
- `GATEWAY_CLOUD_AUTH_TOKEN`
- `ZEROQ_GATEWAY_SHARED_SECRET`
- `ZEROQ_BLE_ALLOW_LEGACY_UNSIGNED`
- `GATEWAY_SYNC_BATCH_SIZE`
- `GATEWAY_SYNC_ENABLED`
- `GATEWAY_SYNC_MAX_RETRY`
- `GATEWAY_SYNC_INGEST_DELAY_MS`
- `GATEWAY_SYNC_COMMAND_POLL_DELAY_MS`
- `GATEWAY_SYNC_ACK_DELAY_MS`

## Storage

- local H2 file: `./data/zeroq_sensor_gateway`
- prod H2 file: `./data/zeroq_sensor_gateway_prod`

## Notes

- 로컬 API는 `GatewayApiKeyGuard`를 통해 보호됩니다.
- cloud sync 동작을 바꾸면 `gateway.sync.*` 설정과 문서를 함께 업데이트합니다.
- 현재 테스트 범위는 얕으므로 sync 정책을 건드릴 때는 서비스 테스트 추가를 우선 검토합니다.
