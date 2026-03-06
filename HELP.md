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

## Main Paths

- ingest: `/api/zeroq/gateway/v1/local/ingest`
- commands: `/api/zeroq/gateway/v1/local/commands`
- monitoring: `/api/zeroq/gateway/v1/monitoring`

## Key Config

- `GATEWAY_NODE_ID`
- `GATEWAY_LOCAL_API_KEY`
- `GATEWAY_CLOUD_BASE_URL`
- `GATEWAY_CLOUD_AUTH_TOKEN`
- `GATEWAY_SYNC_BATCH_SIZE`
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
