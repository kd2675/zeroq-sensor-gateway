# zeroq-sensor-gateway

매장 로컬 센서망과 ZeroQ 클라우드 센서 서버 사이를 연결하는 엣지 게이트웨이입니다. 로컬 ingest 버퍼, command pull, ACK outbox, gateway runtime status sync를 담당합니다.

## 역할

- 로컬 telemetry, heartbeat, batch ingest 수신
- 좌석 센서 advertising payload decode 후 local ingest 적재
- H2 기반 로컬 버퍼 저장
- `cloud-back-server` 경유 클라우드 재전송
- gateway 자체 runtime 상태를 `zeroq-back-sensor`로 주기 전송
- pending command pull 후 로컬 실행 큐 제공
- ACK outbox를 클라우드로 동기화
- queue status와 강제 sync 모니터링 제공

## API 베이스 경로

- `/api/zeroq/gateway/v1/local/ingest`
- `/api/zeroq/gateway/v1/local/ingest/seat`
- `/api/zeroq/gateway/v1/local/commands`
- `/api/zeroq/gateway/v1/monitoring`

## 보안

- 로컬 API는 `X-Gateway-Key` 헤더를 요구합니다.
- 키는 `gateway.node.local-api-key` 또는 `GATEWAY_LOCAL_API_KEY`에서 관리합니다.

## 실행 프로필과 포트

| Profile | Port |
|---|---:|
| `local` | `20191` |
| `dev` | `20191` |
| `prod` | `10191` |
| `test` | `30191` |

## 저장소와 동기화

- local DB: `jdbc:h2:file:./data/zeroq_sensor_gateway`
- prod DB: `jdbc:h2:file:./data/zeroq_sensor_gateway_prod`
- cloud base URL 기본값: `http://localhost:8080`
- cloud gateway shared secret: `ZEROQ_GATEWAY_SHARED_SECRET`
- 로컬 기본값은 `zeroq-gateway-local-shared-secret`입니다. 운영에서는 반드시 override 해야 합니다.
- 기본 sync delay:
  - ingest: `5000ms`
  - command poll: `10000ms`
  - ack: `5000ms`
  - gateway status: `15000ms`

## 실행과 검증

```bash
./gradlew :zeroq-sensor-gateway:bootRun
./gradlew :zeroq-sensor-gateway:bootRun --args='--spring.profiles.active=local'
./gradlew :zeroq-sensor-gateway:compileJava
./gradlew :zeroq-sensor-gateway:test
```

## 데이터와 로그

- DDL: `src/main/resources/db/ddl/zeroq_sensor_gateway_all.sql`
- 시드: `src/main/resources/db/seed/zeroq_sensor_gateway_seed.sql`
- 로그 설정: `src/main/resources/logback-spring.xml`
- 로그 경로: local/test `./logs`, dev `/data/logs/dev/zeroq_sensor_gateway`, prod `/data/logs/prod/zeroq_sensor_gateway`

## Related Docs

- `AGENTS.md`
- `AGENTS_ZEROQ_SENSOR_PROTOCOL.md`

## 참고

- 클라우드 연동은 `infrastructure/cloud/CloudSensorApiClient`가 담당합니다.
- gateway node metadata는 `gateway.node.gateway-id`, `gateway.node.firmware-version`, `gateway.node.ip-address`에서 읽습니다.
- queue 상태와 최근 cloud sync 성공/실패 기록을 합쳐 게이트웨이 heartbeat payload를 만듭니다.
- cloud 호출은 `/internal/zeroq/gateway/sensor/**` 내부 경로로 나가며, 요청마다 HMAC 서명을 붙입니다.
- 좌석 센서 manufacturer data는 `/api/zeroq/gateway/v1/local/ingest/seat/advertisement`로 적재할 수 있습니다.
- 테스트는 존재하지만 범위가 제한적이므로 sync 규칙과 command 처리 변경 시 회귀 테스트 보강이 필요합니다.
