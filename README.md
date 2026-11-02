# OrderCommandService

STOCKDROP 주문 원본·배송·주문 문의·영속 Saga 서비스입니다. 순번 보장/표시, 도착 예정일 계산, 취소·반품 접수 및 실제 정산은 이번 범위에서 제외합니다.

Java 21 / Spring Boot 4.1.1 / Spring Cloud 2025.1.4. 기본 포트는 8083, 서비스 이름은 order-command-service입니다. 개발 ddl-auto=update, 운영 validate입니다.

1. 기존 Command PostgreSQL 컨테이너(stockdrop-command-postgres, 5434)를 실행합니다.
2. PowerShell에서 scripts/Initialize-LocalOrderDatabase.ps1, scripts/Test-LocalOrderDatabase.ps1을 실행합니다. 전용 order_command DB / orders 계정 / orders 스키마를 사용하며 Product DB를 변경하지 않습니다.
3. Java 21로 ./gradlew.bat bootRun을 실행합니다. Swagger는 http://localhost:8083/swagger-ui.html, health는 /actuator/health입니다.
4. PurchaseGateway(8001)를 재시작하면 명시된 Order 작업 경로가 Eureka·LoadBalancer로 연결됩니다. 브라우저의 일반 주문 목록/상세는 아직 없는 Order Query 범위입니다.

Product HTTP 어댑터는 기본 비활성입니다. 결제는 HTTP 대신 Outbox/Kafka 요청·결과 방식입니다. Account 내부 서비스 토큰 발급과 Order 자동 획득·캐시·만료 전 재발급이 구현되었습니다. ORDER_SERVICE_AUTH_ENABLED=true와 일치하는 서비스 자격증명/주체를 공급하고 Product 어댑터를 활성화합니다. **Payment/토스 앱은 아직 없습니다.** [Kafka 결제 계약](contracts/payment-kafka-v1.md)을 구현해야 합니다. 실제 구매 완료는 Payment 연결 후 검증해야 합니다. 인증을 끄거나 가짜 성공을 반환하지 않습니다. Product에는 주문용 확보 스냅샷 응답이 추가되었으므로 새 코드 적용/재시작도 필요합니다.

Kafka는 공용 ProductQueryService/compose.kafka.yaml을 사용합니다. 준비 후 scripts/Initialize-LocalOrderKafkaTopics.ps1을 실행하고 ORDER_OUTBOX_PUBLISHER_ENABLED=true로 활성화합니다. 주문·문의 이벤트는 비공개 토픽입니다. Order Query/소비자는 아직 구현하지 않았습니다. 사진은 ORDER_IMAGES_ENABLED/BUCKET/LOCAL/ENDPOINT 설정이 필요하며 AWS 자격증명 없이 기본 실행할 수 있습니다.

- [내부 HTTP·Saga·이벤트 계약과 현재 미구현 경계](contracts/README.md)
- [API와 프론트 연결](docs/api.md)
- [실행·운영·검증 범위](docs/operations.md)
- openapi/order-command.openapi.json: 실제 컨트롤러에서 생성한 계약 스냅샷

테스트는 Docker 기반 격리 PostgreSQL/Kafka/S3Mock와 실제 HTTP fixture를 사용합니다.

    ./gradlew.bat test bootJar

docs와 AGENTS.md는 기존 서비스 규칙에 따라 Git에서 제외합니다. README/contracts/OpenAPI/SQL/실행 스크립트는 추적 대상입니다. Git add/commit/push와 AWS 배포는 수행하지 않습니다.

결제 결과 소비는 ORDER_PAYMENT_EVENTS_ENABLED=true, 명령 발행은 ORDER_OUTBOX_PUBLISHER_ENABLED=true로 활성화합니다. 결과가 아직 없으면 주문은 VERIFYING_PAYMENT로 남으며 5분 경과만으로 재고를 반환하지 않습니다.
