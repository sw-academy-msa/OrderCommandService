# OrderCommandService

STOCKDROP 주문 원본·배송·주문 문의·영속 Saga 서비스입니다. 순번 보장/표시, 도착 예정일 계산, 취소·반품 접수 및 실제 정산은 이번 범위에서 제외합니다.

Java 21 / Spring Boot 4.1.1 / Spring Cloud 2025.1.4. 기본 포트는 8083, 서비스 이름은 order-command-service입니다. 개발 ddl-auto=update, 운영 validate입니다.

1. 기존 Command PostgreSQL 컨테이너(stockdrop-command-postgres, 5434)를 실행합니다.
2. PowerShell에서 scripts/Initialize-LocalOrderDatabase.ps1, scripts/Test-LocalOrderDatabase.ps1을 실행합니다. 전용 order_command DB / orders 계정 / orders 스키마를 사용하며 Product DB를 변경하지 않습니다.
3. Java 21로 ./gradlew.bat bootRun을 실행합니다. Swagger는 http://localhost:8083/swagger-ui.html, health는 /actuator/health입니다.
4. PurchaseGateway(8001)를 재시작하면 명시된 Order 작업 경로가 Eureka·LoadBalancer로 연결됩니다. 브라우저의 일반 주문 목록/상세는 아직 없는 Order Query 범위입니다.

Product HTTP 어댑터는 기본 비활성입니다. 결제는 HTTP 대신 Outbox/Kafka 요청·결과 방식입니다. Account 내부 서비스 토큰 발급과 Order 자동 획득·캐시·만료 전 재발급이 구현되었습니다. ORDER_SERVICE_AUTH_ENABLED=true와 일치하는 서비스 자격증명/주체를 공급하고 Product 어댑터를 활성화합니다. PaymentService에 테스트 결제 승인·paymentKey 조회·전액 보상과 결과 Outbox가 구현되었습니다. [Kafka 결제 계약](contracts/payment-kafka-v1.md)을 따르며 실제 브라우저 인증을 포함한 구매 완료는 별도 검증이 필요합니다. 인증을 끄거나 가짜 성공을 반환하지 않습니다. Product에는 주문용 확보 스냅샷 응답이 추가되었으므로 새 코드 적용/재시작도 필요합니다.

Kafka는 공용 ProductQueryService/compose.kafka.yaml을 사용합니다. 준비 후 scripts/Initialize-LocalOrderKafkaTopics.ps1을 실행하고 ORDER_OUTBOX_PUBLISHER_ENABLED=true로 활성화합니다. 주문·문의 이벤트는 비공개 토픽입니다. Order Query/소비자는 아직 구현하지 않았습니다. 사진은 ORDER_IMAGES_ENABLED/BUCKET/LOCAL/ENDPOINT 설정이 필요하며 AWS 자격증명 없이 기본 실행할 수 있습니다.

- [내부 HTTP·Saga·이벤트 계약과 현재 미구현 경계](contracts/README.md)
- [API와 프론트 연결](docs/api.md)
- [실행·운영·검증 범위](docs/operations.md)
- openapi/order-command.openapi.json: 실제 컨트롤러에서 생성한 계약 스냅샷

테스트는 Docker 기반 격리 PostgreSQL/Kafka/S3Mock와 실제 HTTP fixture를 사용합니다.

    ./gradlew.bat test bootJar

docs와 AGENTS.md는 기존 서비스 규칙에 따라 Git에서 제외합니다. README/contracts/OpenAPI/SQL/실행 스크립트는 추적 대상입니다. Git add/commit/push와 AWS 배포는 수행하지 않습니다.

결제 결과 소비는 ORDER_PAYMENT_EVENTS_ENABLED=true, 명령 발행은 ORDER_OUTBOX_PUBLISHER_ENABLED=true로 활성화합니다. 결과가 아직 없으면 주문은 VERIFYING_PAYMENT로 남으며 5분 경과만으로 재고를 반환하지 않습니다.


## 객체 생성 규칙

응답 DTO·Saga 내부 DTO는 record를 유지하고 Lombok 빌더로 필드명을 지정하여 생성합니다. 엔티티와 JPA 값 객체는 기존 생성자에만 빌더를 적용하여 UUID·초기 상태·버전·시간·컬렉션 초기화를 유지합니다. 주문·결제 상태 전이와 잠금·재시도·Outbox 트랜잭션은 기존 서비스가 수행합니다. API 응답과 Kafka·내부 HTTP 계약은 유지합니다.
Lombok은 Spring Boot가 관리하는 버전을 compileOnly·annotationProcessor로 사용하며 실행 의존성에는 포함하지 않습니다. 빌더 문자열에 개인정보·결제키·토큰·이벤트 본문을 출력하지 않습니다.

## 도메인 예외

주문·문의·이미지의 exception 패키지에 도메인별 Exception과 ErrorCode를 둡니다. BusinessException을 상속하고 기존 GlobalExceptionHandler가 처리합니다. 기존 ORDER_* 코드·HTTP 상태·메시지는 유지합니다.
결제 결과 이벤트는 PaymentResultException·PaymentResultErrorCode로 구분하되 기존 InvalidPaymentEventException의 타입·패키지·메시지와 영구 오류/DLT 분류를 유지합니다. DB 장애는 기존 경로로 재시도합니다. Saga의 RemoteFailure는 오류 코드와 definitive 판정이 복구·보상 분기에 필요하므로 유지하며 일반 업무 예외로 감싸지 않습니다.

## 공통 성공 응답

공개 JSON 성공 응답은 Account와 같은 success/code/message/data 구조입니다. 업무 결과는 data에서 읽습니다. 주문 생성·결제 시도는 기존 HTTP 202를 유지하고 나머지는 200입니다. 이미지 삭제도 200과 data:null을 반환합니다. 오류 응답과 Kafka·내부 HTTP 호출 계약, 기존 DTO 변환은 유지합니다. Mapper는 추가하지 않습니다.

공통 success:true는 요청 처리 성공을 의미하며 주문·결제 확정을 뜻하지 않습니다. 최종 상태는 data.state와 Command 상태 조회로 확인합니다.

## 주문·결제 약관

약관 본문은 orders.checkout_terms에 버전별로 보존하며 Flyway가 초기 등록합니다. checkout 응답에 termsVersion을 전달하고 기존 결제 요청의 버전 검증과 동의 시각 저장을 재사용합니다. BUYER는 PurchaseGateway를 통해 현재·과거 본문을 조회합니다. [약관 API와 Flyway 적용 안내](contracts/checkout-terms.md)를 확인합니다. 초기 본문은 기획 기준 초안이며 운영 적용 전 검토가 필요합니다.

## 회원탈퇴 연동

[원본 확인, 신규 거래 차단과 장애 회수 계약](contracts/account-withdrawal-v1.md)을 확인합니다.
완료된 판매 이력은 탈퇴 차단 사유가 아닙니다. 진행 거래와 미확정 결제/보상만 확인하며 수수료는 후속 구현 범위입니다.

## 일반 업무 알림

[심사 결과, 보완 요청과 기한, 주문 확정과 배송 알림 계약](contracts/business-notifications-v1.md)을 확인합니다.
상태 변경과 Outbox는 함께 커밋하며 기존 구매 Saga와 결제 Kafka 계약은 유지합니다.
