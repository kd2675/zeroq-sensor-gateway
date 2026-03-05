# zeroq-sensor-gateway

매장 오프라인 센서망과 ZeroQ 클라우드 사이를 연결하는 엣지 게이트웨이 서버입니다.

## 역할
- 로컬 센서 이벤트(telemetry/heartbeat) 수신
- 로컬 DB 버퍼 저장(오프라인 지속)
- 클라우드(`zeroq-back-sensor`)로 배치/개별 재전송
- 클라우드 명령 pull 후 로컬 실행 큐 제공
- 로컬 ACK를 outbox에 저장 후 클라우드 ACK 동기화

## 아키텍처
- 센서 -> `zeroq-sensor-gateway`(로컬)
- `zeroq-sensor-gateway` -> `cloud-back-server` -> `zeroq-back-sensor` (HTTP)
- 클라우드 명령은 pull(`pending`) 방식으로 수집

## 보안
- 로컬 API는 `X-Gateway-Key` 헤더 필수
- 값: `gateway.node.local-api-key`

## 로그
- `logback-spring.xml` 적용
- 커스텀 클래스:
  - `com.zeroq.gateway.common.logback.PrintOnlyWarningLogbackStatusListener`
  - `com.zeroq.gateway.common.logback.filter.CustomLogbackFilter`
- 기본 로그 파일: `${log.config.path}/zeroq_sensor_gateway.log`
- 프로파일별 경로:
  - local/test: `./logs`
  - dev: `/data/logs/dev/zeroq_sensor_gateway`
  - prod: `/data/logs/prod/zeroq_sensor_gateway`

## 주요 엔드포인트
- `POST /api/zeroq/gateway/v1/local/ingest/telemetry`
- `POST /api/zeroq/gateway/v1/local/ingest/heartbeat`
- `POST /api/zeroq/gateway/v1/local/ingest/batch`
- `GET /api/zeroq/gateway/v1/local/commands/pending`
- `PATCH /api/zeroq/gateway/v1/local/commands/{cloudCommandId}/dispatched`
- `POST /api/zeroq/gateway/v1/local/commands/{cloudCommandId}/ack`
- `GET /api/zeroq/gateway/v1/monitoring/queue-status`
- `POST /api/zeroq/gateway/v1/monitoring/sync-now`

## DB 스크립트
- DDL: `src/main/resources/db/ddl/zeroq_sensor_gateway_all.sql`
- Seed: `src/main/resources/db/seed/zeroq_sensor_gateway_seed.sql`

## 실행
```bash
./gradlew :zeroq-sensor-gateway:bootRun
```

## 빌드/테스트
```bash
./gradlew :zeroq-sensor-gateway:compileJava
./gradlew :zeroq-sensor-gateway:test
```
